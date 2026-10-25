package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.craftingscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPauseGate;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

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
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.clevels.CraftingLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftInventory;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftingBankPlan;
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
public class CraftingScript extends KspManagedScript
{
    private static final Logger log = LoggerFactory.getLogger(CraftingScript.class);
    private static final String BANK_WALK_KEY = "Crafting:bank";
    private static final String FURNACE_WALK_KEY = "Crafting:furnace";
    private static final int LOOP_DELAY_MS = 100;
    private static final int WALK_COOLDOWN_MS = 1_000;
    private static final int FURNACE_SEARCH_RADIUS = 12;
    private static final int ACTION_COOLDOWN_MS = 100;
    private static final long PRODUCTION_ACTION_TIMEOUT_MS = 1_500L;
    private static final long LEATHER_PROGRESS_TIMEOUT_MS = 4_800L;
    private static final long GEM_START_TIMEOUT_MS = 3_000L;
    private static final long GEM_PROGRESS_TIMEOUT_MS = 4_800L;
    private static final long FURNACE_PROGRESS_TIMEOUT_MS = 5_500L;
    private static final long ACTION_DISPATCH_TIMEOUT_MS = 1_500L;

    private volatile CraftingState state = CraftingState.WAITING;
    private volatile CraftingLevels targetLevel = CraftingLevels.LEATHER_GLOVES;
    private volatile CraftInventory targetRecipe = CraftInventory.LEATHER_GLOVES;
    private volatile Areas selectedLeatherArea = Areas.EDGE_BANK;
    private volatile Areas selectedFurnaceArea = Areas.EDGE_FURNACE;
    private boolean progressiveCrafting = true;
    private boolean debugLogging;
    private long lastWorkerErrorLogAtMs;
    private long lastActionAtMs;
    private boolean expectingXpDrop;
    private boolean leatherBatchActive;
    private boolean gemBatchActive;
    private boolean furnaceBatchActive;
    private int lastFurnaceMetalCount = -1;
    private long lastFurnaceProgressAtMs;
    private int lastUncutGemCount = -1;
    private long lastGemProgressAtMs;
    private boolean gemCombinationPending;
    private long gemCombinationAtMs;
    private int lastLeatherCount = -1;
    private long lastLeatherProgressAtMs;
    private boolean bankInventoryReset;
    private long pendingProductionActionAtMs;
    private String pendingProductionRecipe;
    private long pendingWorldActionAtMs;
    private String pendingWorldAction;
    private long pendingInventoryActionAtMs;
    private String pendingInventoryItem;
    private int pendingInventoryItemId = -1;
    private int pendingInventoryBeforeCount = -1;

    public boolean run() { return run(CraftingLevels.LEATHER_GLOVES, true); }

    public boolean run(CraftingLevels fallbackLevel) { return run(fallbackLevel, true); }

    public boolean run(CraftingLevels selectedLevel, boolean progressiveCrafting)
    {
        shutdown();
        this.progressiveCrafting = progressiveCrafting;
        targetLevel = selectedLevel == null ? CraftingLevels.LEATHER_GLOVES : selectedLevel;
        targetRecipe = CraftInventory.valueOf(targetLevel.name());
        selectedLeatherArea = Areas.randomLeatherArea();
        selectedFurnaceArea = Areas.randomFurnaceArea();
        debug("Selected Crafting areas | leather={} furnace={}",
                selectedLeatherArea.getDisplayName(), selectedFurnaceArea.getDisplayName());
        state = CraftingState.CHECKING_RESOURCES;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
            // Track scheduled worker activity before any early-return guard.
            // This helps distinguish a stopped worker from repeated skipped ticks.
            KspTaskDebug.throttled(log, debugLogging, "Crafting", "worker-heartbeat", 15_000L,
                    "worker tick | state={} paused={}", state, KspAccountBuilderPauseGate.isPaused());

            if (KspAccountBuilderPauseGate.isPaused() || !super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            if (isLeatherBatchActive() || isGemBatchActive() || isFurnaceBatchActive()) return;

            // Finish any open production dialogue before selecting a new recipe,
            // walking to a bank, or dispatching another inventory Use click.
            if (isCraftingProductionWidgetOpen(targetRecipe))
            {
                selectProductAndMakeAll(targetRecipe);
                return;
            }

            // Freeze a viable recipe while its inventory batch is being crafted
            // or withdrawn. Selecting a different recipe after each partial bank
            // withdrawal used to abandon the chisel / silver-bar batch.
            if (this.progressiveCrafting
                    && !bankInventoryReset
                    && !isInventoryActionPending()
                    && !hasRequiredInventory(targetRecipe))
            {
                selectTargetRecipe();
            }
            KspTaskDebug.throttled(log, debugLogging, "Crafting", "loop", 5_000L,
                    "loop | state={} recipe={} player={} moving={} animating={} interacting={} bankOpen={} leatherArea={} furnaceArea={}",
                    state,
                    targetLevel.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    selectedLeatherArea.getDisplayName(),
                    selectedFurnaceArea.getDisplayName());

            if (!hasRequiredInventory(targetRecipe))
            {
                state = CraftingState.BANKING;
                prepareInventory(targetRecipe);
                return;
            }

            if (Rs2Bank.isOpen())
            {
                bankInventoryReset = false;
                Rs2Bank.closeBank();
                return;
            }

            if (targetRecipe.requiresFurnace())
            {
                if (!ensureInArea(selectedFurnaceArea, FURNACE_WALK_KEY))
                {
                    state = CraftingState.WALKING_TO_FURNACE;
                    return;
                }

                state = CraftingState.CRAFTING_AT_FURNACE;
                craftAtFurnace(targetRecipe);
                return;
            }

            Areas inventoryCraftArea = targetRecipe.getRecipeType() == RecipeType.LEATHER
                    ? selectedLeatherArea
                    : Areas.EDGE_BANK;
            if (!ensureInArea(inventoryCraftArea, BANK_WALK_KEY))
            {
                state = CraftingState.WALKING_TO_BANK;
                return;
            }

            state = targetRecipe.getRecipeType() == RecipeType.LEATHER
                    ? CraftingState.CRAFTING_LEATHER
                    : CraftingState.CUTTING_GEMS;
            craftFromInventory(targetRecipe);
            }
            catch (Exception exception)
            {
                // An uncaught scheduled exception would otherwise stop all future ticks.
                // Keep the worker alive and rate-limit stack traces.
                long now = System.currentTimeMillis();
                if (lastWorkerErrorLogAtMs == 0L || now - lastWorkerErrorLogAtMs >= 30_000L)
                {
                    lastWorkerErrorLogAtMs = now;
                    log.error("[KSP Crafting] Scheduled worker failed; retrying: state={} recipe={}", state, targetRecipe, exception);
                }
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

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
                bankInventoryReset = false;
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
            if (inventoryCount(ingredient) + Math.max(0, bankCount(ingredient)) < ingredient.getAmount())
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
            if (inventoryCount(ingredient) < ingredient.getAmount())
            {
                return false;
            }
        }
        return true;
    }

    private void prepareInventory(CraftInventory recipe)
    {
        expectingXpDrop = false;
        clearLeatherBatch();
        clearGemBatch();
        clearFurnaceBatch();
        gemCombinationPending = false;

        if (!Rs2Bank.isOpen())
        {
            state = CraftingState.WALKING_TO_BANK;
            Microbot.status = "Walking to nearest bank";
            if (!Rs2Player.isMoving())
            {
                // Both bank-opening strategies share one interaction cooldown.
                // Never bypass a pending bank click through the walking fallback.
                if (!isWorldActionPending("open-bank"))
                {
                    if (!Rs2Bank.openBank())
                    {
                        Rs2Bank.walkToBankAndUseBank();
                    }
                    markWorldAction("open-bank");
                }
            }
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()
                || !KspBankMode.ensureWithdrawAsItem())
        {
            return;
        }

        if (!bankInventoryReset)
        {
            // Do not withdraw supplies until the bank has actually deposited
            // the previous inventory. A dispatched click is not confirmation.
            if (Rs2Inventory.emptySlotCount() < 28)
            {
                if (!isWorldActionPending("deposit-all"))
                {
                    Rs2Bank.depositAll();
                    markWorldAction("deposit-all");
                }
                return;
            }
            bankInventoryReset = true;
            return;
        }

        // Make the smallest fixed tooling withdrawals first, then fill the
        // remaining slots with the correct raw material. Item IDs prevent
        // "Sapphire" from matching "Uncut sapphire" and vice versa.
        for (Ingredient ingredient : recipe.getIngredients())
        {
            int current = inventoryCount(ingredient);
            int bankAvailable = Math.max(0, bankCount(ingredient));
            int desired = CraftingBankPlan.targetAmount(recipe, ingredient, current, bankAvailable);
            if (current >= desired && current >= ingredient.getAmount()) continue;

            int deficit = desired - current;
            if (deficit <= 0 || bankAvailable <= 0 || current + bankAvailable < ingredient.getAmount())
            {
                state = CraftingState.WAITING;
                Microbot.status = "Missing crafting supply: " + ingredient.getItemName()
                        + " (inventory=" + current + ", bank=" + bankAvailable + ")";
                KspTaskDebug.throttled(log, debugLogging, "Crafting", "missing-supply",
                        10_000L, "Unable to prepare {}: {} need={} have={} bank={}",
                        recipe, ingredient.getItemName(), ingredient.getAmount(), current, bankAvailable);
                return;
            }

            if (isInventoryActionPending()) return;

            // Rs2Bank.withdrawX opens a quantity prompt and may block while
            // typing; acknowledge the resulting inventory change on the next
            // tick, instead of immediately dispatching another click.
            boolean dispatched = withdraw(ingredient, Math.min(deficit, bankAvailable));
            markInventoryAction(ingredient, current);
            Microbot.status = (dispatched ? "Withdrawing " : "Retrying withdrawal of ")
                    + ingredient.getItemName() + " ("
                    + current + "/" + desired + ")";
            return;
        }

        if (!hasRequiredInventory(recipe)) return;

        bankInventoryReset = false;
        Rs2Bank.closeBank();
    }

    private int inventoryCount(Ingredient ingredient)
    {
        return ingredient.getItemId() > 0
                ? Rs2Inventory.count(ingredient.getItemId())
                : Rs2Inventory.count(ingredient.getItemName(), true);
    }

    private int bankCount(Ingredient ingredient)
    {
        return ingredient.getItemId() > 0
                ? Rs2Bank.count(ingredient.getItemId())
                : Rs2Bank.count(ingredient.getItemName(), true);
    }

    private boolean withdraw(Ingredient ingredient, int amount)
    {
        if (amount < 1) return false;
        if (ingredient.getItemId() > 0)
        {
            return amount == 1
                    ? Rs2Bank.withdrawOne(ingredient.getItemId())
                    : Rs2Bank.withdrawX(ingredient.getItemId(), amount);
        }
        return amount == 1
                ? Rs2Bank.withdrawOne(ingredient.getItemName(), true)
                : Rs2Bank.withdrawX(ingredient.getItemName(), amount, true);
    }

    private boolean ensureInArea(Areas area, String walkKey)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (area.getArea().contains(playerLocation))
        {
            KspWalkerGuard.clear(walkKey);
            return true;
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
        if (isCraftingProductionWidgetOpen(recipe))
        {
            selectProductAndMakeAll(recipe);
            return;
        }

        if (isProductionActionPending(recipe)) return;
        if (isWorldActionPending("combine") || !canStartAction()) return;

        Ingredient tool = recipe.getTool();
        Ingredient material = getFirstConsumable(recipe);
        if (tool == null || material == null) return;

        Microbot.status = "Crafting " + targetLevel.getDisplayName();
        if (recipe.getRecipeType() == RecipeType.GEM_CUTTING)
        {
            // A successful combine dispatch can still leave the inventory
            // waiting for the second click. Verify the resulting interface
            // or consumed gems before attempting another combination.
            long now = System.currentTimeMillis();
            if (gemCombinationPending)
            {
                if (now - gemCombinationAtMs < GEM_START_TIMEOUT_MS)
                {
                    Microbot.status = "Waiting for chisel and uncut gem interaction";
                    return;
                }
                gemCombinationPending = false;
                debug("Gem combination timeout; retrying chisel-first | recipe={}", recipe);
            }
            // OSRS Chisel=1755: always select the chisel first and use it on
            // the exact UNCut gem ID. Never reverse the pair or select a cut gem.
            boolean dispatched = tool.getItemId() > 0 && material.getItemId() > 0
                    ? Rs2Inventory.combine(tool.getItemId(), material.getItemId())
                    : Rs2Inventory.combine(tool.getItemName(), material.getItemName());
            lastActionAtMs = now;
            markWorldAction("combine");
            if (dispatched)
            {
                gemCombinationAtMs = now;
                gemCombinationPending = true;
            }
            else
            {
                Microbot.status = "Gem use failed - retrying chisel on " + material.getItemName();
            }
            return;
        }

        if (Rs2Inventory.combine(tool.getItemName(), material.getItemName()))
        {
            lastActionAtMs = System.currentTimeMillis();
            markWorldAction("combine");
        }
    }

    private Ingredient getFurnaceMetal(CraftInventory recipe)
    {
        for (Ingredient ingredient : recipe.getIngredients())
        {
            if ("Silver bar".equals(ingredient.getItemName())
                    || "Gold bar".equals(ingredient.getItemName()))
            {
                return ingredient;
            }
        }
        return null;
    }

    private void craftAtFurnace(CraftInventory recipe)
    {
        if (isCraftingProductionWidgetOpen(recipe))
        {
            selectProductAndMakeAll(recipe);
            return;
        }

        if (isProductionActionPending(recipe)
                || isWorldActionPending("furnace") || !canStartAction()) return;

        Rs2TileObjectModel furnace = findFurnace();
        if (furnace == null)
        {
            Microbot.status = "Waiting for furnace near " + selectedFurnaceArea.getDisplayName();
            return;
        }

        Ingredient metal = getFurnaceMetal(recipe);
        if (metal == null || inventoryCount(metal) <= 0)
        {
            Microbot.status = "Missing metal bars for " + recipe.getProductName();
            return;
        }

        // Jewellery production requires USING a bar on a furnace, not the
        // furnace's Smelt action (which opens ore smelting instead).
        Microbot.status = "Using " + metal.getItemName()
                + " on furnace for " + recipe.getProductName();
        boolean dispatched = Rs2Inventory.useUnNotedItemOnObject(metal.getItemName(), furnace);
        lastActionAtMs = System.currentTimeMillis();
        markWorldAction("furnace");
        if (!dispatched)
        {
            debug("Failed bar-on-furnace interaction | recipe={} metal={} furnace={}",
                    recipe, metal.getItemName(), furnace.getId());
        }
    }

    private void selectProductAndMakeAll(CraftInventory recipe)
    {
        if (!isCraftingProductionWidgetOpen(recipe) || isProductionActionPending(recipe)) return;
        if (Rs2Player.isAnimating()) {
            clearProductionAction();
            return;
        }

        boolean selected = Rs2Widget.clickWidget(recipe.getProductName(), true)
                || Rs2Widget.clickWidget(recipe.getProductName(), false);

        if (!selected && recipe.getRecipeType() != RecipeType.GEM_CUTTING)
        {
            debug("Could not select crafting product {}", recipe.getProductName());
            return;
        }
        // For gem cutting the production interface may default to the
        // correct gem. Space confirms Make-All, but only with a live widget.
        // Never send Space after a failed inventory-only combine.
        if (recipe.getRecipeType() == RecipeType.GEM_CUTTING
                && !isCraftingProductionWidgetOpen(recipe)) return;

        markProductionAction(recipe);
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        expectingXpDrop = true;
        lastActionAtMs = System.currentTimeMillis();
        if (recipe.getRecipeType() == RecipeType.GEM_CUTTING)
        {
            gemCombinationPending = false;
            gemBatchActive = true;
            Ingredient gem = getFirstConsumable(recipe);
            lastUncutGemCount = gem == null ? -1 : inventoryCount(gem);
            lastGemProgressAtMs = lastActionAtMs;
        }
        if (recipe.getRecipeType() == RecipeType.LEATHER)
        {
            leatherBatchActive = true;
            lastLeatherCount = inventoryCount(getFirstConsumable(recipe));
            lastLeatherProgressAtMs = lastActionAtMs;
        }
        if (recipe.requiresFurnace())
        {
            Ingredient metal = getFurnaceMetal(recipe);
            furnaceBatchActive = metal != null;
            lastFurnaceMetalCount = metal == null ? -1 : inventoryCount(metal);
            lastFurnaceProgressAtMs = lastActionAtMs;
        }
    }

    private boolean isGemBatchActive()
    {
        if (!gemBatchActive) return false;
        Ingredient material = getFirstConsumable(targetRecipe);
        if (material == null || targetRecipe.getRecipeType() != RecipeType.GEM_CUTTING)
        {
            clearGemBatch();
            return false;
        }
        int count = inventoryCount(material);
        if (Rs2Bank.isOpen() || count <= 0 || !Rs2Inventory.hasItem(1755))
        {
            clearGemBatch();
            return false;
        }
        long now = System.currentTimeMillis();
        if (Rs2Player.isAnimating() || count < lastUncutGemCount)
        {
            lastUncutGemCount = count;
            lastGemProgressAtMs = now;
            return true;
        }
        if (now - lastGemProgressAtMs < GEM_PROGRESS_TIMEOUT_MS) return true;
        clearGemBatch();
        return false;
    }

    private void clearGemBatch()
    {
        gemBatchActive = false;
        lastUncutGemCount = -1;
        lastGemProgressAtMs = 0L;
    }

    private boolean isFurnaceBatchActive()
    {
        if (!furnaceBatchActive) return false;
        Ingredient metal = getFurnaceMetal(targetRecipe);
        if (metal == null || !targetRecipe.requiresFurnace() || Rs2Bank.isOpen())
        {
            clearFurnaceBatch();
            return false;
        }

        int count = inventoryCount(metal);
        if (count <= 0)
        {
            clearFurnaceBatch();
            return false;
        }

        long now = System.currentTimeMillis();
        if (count < lastFurnaceMetalCount)
        {
            lastFurnaceMetalCount = count;
            lastFurnaceProgressAtMs = now;
            return true;
        }

        if (Rs2Player.isAnimating())
        {
            lastFurnaceProgressAtMs = now;
            return true;
        }

        if (now - lastFurnaceProgressAtMs < FURNACE_PROGRESS_TIMEOUT_MS)
        {
            return true;
        }

        clearFurnaceBatch();
        return false;
    }

    private void clearFurnaceBatch()
    {
        furnaceBatchActive = false;
        lastFurnaceMetalCount = -1;
        lastFurnaceProgressAtMs = 0L;
    }

    private boolean isLeatherBatchActive()
    {
        if (!leatherBatchActive) return false;

        Ingredient leather = getFirstConsumable(targetRecipe);
        int count = leather == null ? 0 : inventoryCount(leather);
        if (Rs2Bank.isOpen() || Rs2Player.isMoving() || count <= 0
                || !Rs2Inventory.hasItem("Needle", true)
                || !Rs2Inventory.hasItem("Thread", true))
        {
            clearLeatherBatch();
            return false;
        }

        long now = System.currentTimeMillis();
        if (Rs2Player.isAnimating() || count < lastLeatherCount)
        {
            lastLeatherCount = count;
            lastLeatherProgressAtMs = now;
            return true;
        }

        if (Rs2Widget.isProductionWidgetOpen()
                || now - lastLeatherProgressAtMs >= LEATHER_PROGRESS_TIMEOUT_MS)
        {
            clearLeatherBatch();
            return false;
        }
        return true;
    }

    private void clearLeatherBatch()
    {
        leatherBatchActive = false;
        lastLeatherCount = -1;
        lastLeatherProgressAtMs = 0L;
    }

    private boolean isCraftingProductionWidgetOpen(CraftInventory recipe)
    {
        // Bank item widgets must not be mistaken for Crafting production dialogs.
        // Otherwise the script can repeatedly attempt Make-All while the bank
        // is open instead of withdrawing its chisel and uncut gems.
        if (recipe == null || Rs2Bank.isOpen()) return false;
        if (recipe.requiresFurnace())
        {
            return "Tiara".equals(recipe.getProductName())
                    ? Rs2Widget.isSilverCraftingWidgetOpen()
                    : Rs2Widget.isGoldCraftingWidgetOpen();
        }

        // Only act inside a real production interface, never a similarly
        // named bank/inventory widget (e.g. cut Sapphire or uncut Sapphire).
        return Rs2Widget.isProductionWidgetOpen();
    }

    private boolean isProductionActionPending(CraftInventory recipe)
    {
        if (pendingProductionActionAtMs == 0L || pendingProductionRecipe == null || recipe == null) return false;

        if (Rs2Player.isAnimating())
        {
            clearProductionAction();
            return false;
        }

        long elapsed = System.currentTimeMillis() - pendingProductionActionAtMs;
        if (!pendingProductionRecipe.equals(recipe.name()) || elapsed >= PRODUCTION_ACTION_TIMEOUT_MS)
        {
            clearProductionAction();
            return false;
        }

        return true;
    }

    private void markProductionAction(CraftInventory recipe)
    {
        pendingProductionRecipe = recipe.name();
        pendingProductionActionAtMs = System.currentTimeMillis();
    }

    private void clearProductionAction()
    {
        pendingProductionActionAtMs = 0L;
        pendingProductionRecipe = null;
    }

    private boolean isWorldActionPending(String action)
    {
        if (pendingWorldActionAtMs == 0L || pendingWorldAction == null) return false;
        if (!pendingWorldAction.equals(action)) return false;
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) return true;
        if (System.currentTimeMillis() - pendingWorldActionAtMs < ACTION_DISPATCH_TIMEOUT_MS) return true;
        pendingWorldActionAtMs = 0L;
        pendingWorldAction = null;
        return false;
    }

    private void markWorldAction(String action)
    {
        pendingWorldAction = action;
        pendingWorldActionAtMs = System.currentTimeMillis();
    }

    private boolean isInventoryActionPending()
    {
        if (pendingInventoryActionAtMs == 0L || pendingInventoryItem == null) return false;
        int currentCount = pendingInventoryItemId > 0
                ? Rs2Inventory.count(pendingInventoryItemId)
                : Rs2Inventory.count(pendingInventoryItem, true);
        if (currentCount != pendingInventoryBeforeCount) {
            clearInventoryAction();
            return false;
        }
        if (System.currentTimeMillis() - pendingInventoryActionAtMs < ACTION_DISPATCH_TIMEOUT_MS) return true;
        clearInventoryAction();
        return false;
    }

    private void markInventoryAction(Ingredient item, int beforeCount)
    {
        pendingInventoryItem = item.getItemName();
        pendingInventoryItemId = item.getItemId();
        pendingInventoryBeforeCount = beforeCount;
        pendingInventoryActionAtMs = System.currentTimeMillis();
    }

    private void clearInventoryAction()
    {
        pendingInventoryActionAtMs = 0L;
        pendingInventoryItem = null;
        pendingInventoryItemId = -1;
        pendingInventoryBeforeCount = -1;
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
                        && selectedFurnaceArea.getArea().contains(object.getWorldLocation()))
                .nearestReachable(FURNACE_SEARCH_RADIUS));
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            KspTaskDebug.info(log, true, "Crafting", message, args);
        }
    }

    public CraftingState getState() { return state; }

    public CraftingLevels getTargetLevel() { return targetLevel; }

    public CraftInventory getTargetRecipe() { return targetRecipe; }

    @Override
    public void shutdown()
    {
        state = CraftingState.WAITING;
        clearLeatherBatch();
        clearGemBatch();
        clearFurnaceBatch();
        gemCombinationPending = false;
        gemCombinationAtMs = 0L;
        clearProductionAction();
        pendingWorldActionAtMs = 0L;
        pendingWorldAction = null;
        clearInventoryAction();
        lastActionAtMs = 0L;
        expectingXpDrop = false;
        bankInventoryReset = false;
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
