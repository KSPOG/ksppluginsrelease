package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.craftingscript;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.clevels.CraftingLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftInventory;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftInventory.Ingredient;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftInventory.RecipeType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CraftingScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(CraftingScript.class);
    private static final String BANK_WALK_KEY = "Crafting:edge-bank";
    private static final String FURNACE_WALK_KEY = "Crafting:edge-furnace";
    private static final int LOOP_DELAY_MS = 600;
    private static final int WALK_COOLDOWN_MS = 3_000;
    private static final int FURNACE_SEARCH_RADIUS = 12;
    private static final int ACTION_COOLDOWN_MS = 2_000;
    private static final int PRODUCTION_START_TIMEOUT_MS = 2_500;

    private volatile CraftingState state = CraftingState.WAITING;
    private volatile CraftingLevels targetLevel = CraftingLevels.LEATHER_GLOVES;
    private volatile CraftInventory targetRecipe = CraftInventory.LEATHER_GLOVES;
    private boolean progressiveCrafting = true;
    private boolean debugLogging;
    private long lastActionAtMs;
    private boolean expectingXpDrop;

    public boolean run()
    {
        return run(CraftingLevels.LEATHER_GLOVES, true);
    }

    public boolean run(CraftingLevels fallbackLevel)
    {
        return run(fallbackLevel, true);
    }

    public boolean run(CraftingLevels selectedLevel, boolean progressiveCrafting)
    {
        shutdown();
        this.progressiveCrafting = progressiveCrafting;
        targetLevel = selectedLevel == null ? CraftingLevels.LEATHER_GLOVES : selectedLevel;
        targetRecipe = CraftInventory.valueOf(targetLevel.name());
        state = CraftingState.CHECKING_RESOURCES;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (this.progressiveCrafting)
            {
                selectTargetRecipe();
            }
            KspTaskDebug.throttled(log, debugLogging, "Crafting", "loop", 5_000L,
                    "loop | state={} recipe={} player={} moving={} animating={} interacting={} bankOpen={}",
                    state,
                    targetLevel.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen());

            if (!hasRequiredInventory(targetRecipe))
            {
                state = CraftingState.BANKING;
                prepareInventory(targetRecipe);
                return;
            }

            if (targetRecipe.requiresFurnace())
            {
                if (!ensureInArea(Areas.EDGE_FURNACE, FURNACE_WALK_KEY))
                {
                    state = CraftingState.WALKING_TO_FURNACE;
                    return;
                }

                state = CraftingState.CRAFTING_AT_FURNACE;
                craftAtFurnace(targetRecipe);
                return;
            }

            if (!ensureInArea(Areas.EDGE_BANK, BANK_WALK_KEY))
            {
                state = CraftingState.WALKING_TO_BANK;
                return;
            }

            state = targetRecipe.getRecipeType() == RecipeType.LEATHER
                    ? CraftingState.CRAFTING_LEATHER
                    : CraftingState.CUTTING_GEMS;
            craftFromInventory(targetRecipe);
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    private void selectTargetRecipe()
    {
        int craftingLevel = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        CraftingLevels[] levels = CraftingLevels.values();
        for (int index = levels.length - 1; index >= 0; index--)
        {
            CraftingLevels candidateLevel = levels[index];
            if (craftingLevel < candidateLevel.getRequiredLevel())
            {
                continue;
            }

            CraftInventory candidateRecipe = CraftInventory.valueOf(candidateLevel.name());
            if (!hasRecipeResources(candidateRecipe))
            {
                continue;
            }

            if (targetLevel != candidateLevel)
            {
                targetLevel = candidateLevel;
                targetRecipe = candidateRecipe;
                expectingXpDrop = false;
                debug("Selected crafting recipe {} at level {}",
                        targetLevel.getDisplayName(), craftingLevel);
            }
            return;
        }

        CraftInventory fallbackRecipe = CraftInventory.valueOf(targetLevel.name());
        targetRecipe = fallbackRecipe;
    }

    private boolean hasRecipeResources(CraftInventory recipe)
    {
        for (Ingredient ingredient : recipe.getIngredients())
        {
            int available = Rs2Inventory.count(ingredient.getItemName())
                    + Math.max(0, Rs2Bank.count(ingredient.getItemName()));
            if (available < ingredient.getAmount())
            {
                return false;
            }
        }
        return true;
    }

    private boolean hasRequiredInventory(CraftInventory recipe)
    {
        for (Ingredient ingredient : recipe.getIngredients())
        {
            if (Rs2Inventory.count(ingredient.getItemName()) < ingredient.getAmount())
            {
                return false;
            }
        }
        return true;
    }

    private void prepareInventory(CraftInventory recipe)
    {
        expectingXpDrop = false;

        if (!ensureInArea(Areas.EDGE_BANK, BANK_WALK_KEY))
        {
            state = CraftingState.WALKING_TO_BANK;
            return;
        }

        if (!Rs2Bank.isOpen())
        {
            if (!Rs2Bank.openBank())
            {
                return;
            }
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()
                || !KspBankMode.ensureWithdrawAsItem())
        {
            return;
        }

        Rs2Bank.depositAll();
        sleep(200);

        for (Ingredient ingredient : recipe.getIngredients())
        {
            int amount = resolveWithdrawAmount(recipe, ingredient);
            if (amount <= 0 || !withdraw(ingredient.getItemName(), amount))
            {
                Microbot.status = "Missing crafting item: " + ingredient.getItemName();
                debug("Unable to withdraw {} x{} for {}",
                        ingredient.getItemName(), amount, targetLevel.getDisplayName());
                return;
            }
        }

        if (!hasRequiredInventory(recipe))
        {
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);
    }

    private int resolveWithdrawAmount(CraftInventory recipe, Ingredient ingredient)
    {
        if (ingredient.isReusable())
        {
            return ingredient.getAmount();
        }

        Ingredient[] ingredients = recipe.getIngredients();
        int reusableSlots = 0;
        int consumableTypes = 0;
        for (Ingredient candidate : ingredients)
        {
            if (candidate.isReusable())
            {
                reusableSlots += candidate.getAmount();
            }
            else
            {
                consumableTypes++;
            }
        }

        int bankCount = Math.max(0, Rs2Bank.count(ingredient.getItemName()));
        if (consumableTypes == 1)
        {
            return Math.min(28 - reusableSlots, bankCount);
        }
        return Math.min(ingredient.getAmount(), bankCount);
    }

    private boolean withdraw(String itemName, int amount)
    {
        boolean withdrew = amount == 1
                ? Rs2Bank.withdrawOne(itemName)
                : Rs2Bank.withdrawX(itemName, amount);
        if (!withdrew)
        {
            return false;
        }

        sleepUntil(() -> Rs2Inventory.count(itemName) >= amount, 2_000);
        return Rs2Inventory.count(itemName) >= amount;
    }

    private boolean ensureInArea(Areas area, String walkKey)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (area.getArea().contains(playerLocation))
        {
            KspWalkerGuard.clear(walkKey);
            return true;
        }

        if (Rs2Player.isMoving())
        {
            return false;
        }

        Microbot.status = "Walking to " + area.getDisplayName();
        KspWalkerGuard.walkToDestination(
                walkKey,
                area.getArea()::getRandomPoint,
                area.getArea()::contains,
                2,
                WALK_COOLDOWN_MS);
        return false;
    }

    private void craftFromInventory(CraftInventory recipe)
    {
        if (!canStartAction())
        {
            return;
        }

        Ingredient tool = recipe.getTool();
        Ingredient material = getFirstConsumable(recipe);
        if (tool == null || material == null)
        {
            return;
        }

        if (expectingXpDrop && Rs2Player.waitForXpDrop(Skill.CRAFTING, 4_500))
        {
            return;
        }

        Microbot.status = "Crafting " + targetLevel.getDisplayName();
        Rs2Inventory.use(tool.getItemName());
        sleep(150);
        Rs2Inventory.use(material.getItemName());

        boolean interfaceOpened = sleepUntil(
                () -> Rs2Widget.isProductionWidgetOpen()
                        || Rs2Widget.findWidget(targetLevel.getDisplayName(), null, false) != null
                        || Rs2Player.isAnimating(),
                PRODUCTION_START_TIMEOUT_MS);
        if (!interfaceOpened)
        {
            return;
        }

        selectProductAndMakeAll(recipe);
    }

    private void craftAtFurnace(CraftInventory recipe)
    {
        if (!canStartAction())
        {
            return;
        }

        if (expectingXpDrop && Rs2Player.waitForXpDrop(Skill.CRAFTING, 4_500))
        {
            return;
        }

        if (Rs2Widget.isGoldCraftingWidgetOpen() || Rs2Widget.isSilverCraftingWidgetOpen())
        {
            selectProductAndMakeAll(recipe);
            return;
        }

        Rs2TileObjectModel furnace = findFurnace();
        if (furnace == null)
        {
            Microbot.status = "Waiting for Edgeville furnace";
            return;
        }

        Microbot.status = "Crafting " + targetLevel.getDisplayName();
        lastActionAtMs = System.currentTimeMillis();
        if (!furnace.click("Smelt"))
        {
            return;
        }

        sleepUntil(() -> Rs2Widget.isGoldCraftingWidgetOpen()
                || Rs2Widget.isSilverCraftingWidgetOpen(), 3_000);
        selectProductAndMakeAll(recipe);
    }

    private void selectProductAndMakeAll(CraftInventory recipe)
    {
        boolean selected = Rs2Widget.clickWidget(recipe.getProductName(), true)
                || Rs2Widget.clickWidget(recipe.getProductName(), false);
        if (!selected && recipe.getRecipeType() == RecipeType.GEM_CUTTING)
        {
            selected = true;
        }
        if (!selected)
        {
            debug("Could not select crafting product {}", recipe.getProductName());
            return;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        expectingXpDrop = true;
        lastActionAtMs = System.currentTimeMillis();
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 2_500);
    }

    private boolean canStartAction()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return false;
        }
        return System.currentTimeMillis() - lastActionAtMs >= ACTION_COOLDOWN_MS;
    }

    private Ingredient getFirstConsumable(CraftInventory recipe)
    {
        for (Ingredient ingredient : recipe.getIngredients())
        {
            if (!ingredient.isReusable())
            {
                return ingredient;
            }
        }
        return null;
    }

    private Rs2TileObjectModel findFurnace()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return null;
        }

        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName("Furnace")
                .within(playerLocation, FURNACE_SEARCH_RADIUS)
                .where(object -> object.getWorldLocation() != null
                        && Areas.EDGE_FURNACE.getArea().contains(object.getWorldLocation()))
                .nearestReachable(FURNACE_SEARCH_RADIUS));
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            KspTaskDebug.info(log, true, "Crafting", message, args);
        }
    }

    public CraftingState getState()
    {
        return state;
    }

    public CraftingLevels getTargetLevel()
    {
        return targetLevel;
    }

    public CraftInventory getTargetRecipe()
    {
        return targetRecipe;
    }

    @Override
    public void shutdown()
    {
        state = CraftingState.WAITING;
        lastActionAtMs = 0L;
        expectingXpDrop = false;
        KspWalkerGuard.clear(BANK_WALK_KEY);
        KspWalkerGuard.clear(FURNACE_WALK_KEY);
        super.shutdown();
    }

    public enum CraftingState
    {
        CHECKING_RESOURCES,
        BANKING,
        WALKING_TO_BANK,
        WALKING_TO_FURNACE,
        CRAFTING_LEATHER,
        CUTTING_GEMS,
        CRAFTING_AT_FURNACE,
        WAITING
    }
}
