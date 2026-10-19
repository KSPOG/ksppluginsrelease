/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SmeltScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(SmeltScript.class);
    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
    private static final int PRODUCTION_ACTION_START_TIMEOUT_MS = 1_500;
    private static final long SMELTING_BATCH_STALL_TIMEOUT_MS = 5_000L;

    private long lastWebWalkAtMs;
    private long bankInteractionSentAt;
    private long furnaceInteractionSentAt;
    private long lastSmeltAnimationAtMs;
    private long pendingProductionActionAtMs;
    private String pendingProductionActionKey;
    private SmeltArea targetArea = SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE;
    private BarLevels targetBar = BarLevels.BRONZE;
    private boolean debugLogging;
    private boolean walkingToTargetArea;
    private boolean progressiveSmelting = true;
    private boolean smeltingBatchActive;
    private int smeltingBatchRemainingCycles;
    private long smeltingBatchLastProgressAtMs;

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    public boolean run(SmeltArea area, BarLevels fallbackBarLevel)
    {
        return run(area, fallbackBarLevel, true);
    }

    public boolean run(SmeltArea area, BarLevels fallbackBarLevel, boolean progressiveSmelting)
    {
        shutdown();
        targetArea = SmeltArea.randomArea();
        targetBar = fallbackBarLevel;
        this.progressiveSmelting = progressiveSmelting;
        debug("Selected random Smelting area | area={}", targetArea.getDisplayName());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn()) return;
            if (isSmeltingBatchActive(targetBar))
            {
                Microbot.status = "Smelting " + targetBar.getDisplayName();
                return;
            }

            selectTargetBar(fallbackBarLevel);
            KspTaskDebug.throttled(log, debugLogging, "Smelting", "loop", 5_000L,
                    "loop | targetBar={} area={} player={} moving={} animating={} interacting={} bankOpen={} productionOpen={} awaitingStart={}",
                    targetBar != null ? targetBar.getDisplayName() : "none",
                    targetArea.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    Rs2Widget.isProductionWidgetOpen(),
                    furnaceInteractionSentAt != 0L);

            if (!ensureOreInventoryForTargetBar(targetBar)) return;
            if (!ensureInTargetArea()) return;
            smeltAtFurnace(targetBar);
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void selectTargetBar(BarLevels fallbackBarLevel)
    {
        if (!progressiveSmelting)
        {
            targetBar = fallbackBarLevel;
            return;
        }

        BarLevels inventoryBar = resolveInventorySmeltableBar();
        if (inventoryBar != null)
        {
            if (targetBar != inventoryBar)
            {
                targetBar = inventoryBar;
                debug("Keeping inventory-selected bar {}", targetBar.getDisplayName());
            }
            return;
        }

        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        BarLevels bestBar = resolveBestSmeltableBar(smithingLevel);
        if (bestBar != null)
        {
            if (targetBar != bestBar)
            {
                targetBar = bestBar;
                debug("Selected best smeltable bar {} at smithing level {}", targetBar.getDisplayName(), smithingLevel);
            }
            return;
        }

        targetBar = fallbackBarLevel;
    }

    private BarLevels resolveInventorySmeltableBar()
    {
        BarLevels[] bars = BarLevels.values();
        for (int i = bars.length - 1; i >= 0; --i)
        {
            BarLevels bar = bars[i];
            if (hasBalancedOreInventory(ReqOres.valueOf(bar.name()))) return bar;
        }
        return null;
    }

    private BarLevels resolveBestSmeltableBar(int smithingLevel)
    {
        BarLevels[] bars = BarLevels.values();
        for (int i = bars.length - 1; i >= 0; --i)
        {
            BarLevels bar = bars[i];
            if (smithingLevel >= bar.getRequiredSmithingLevel() && getCraftableBarsFromBank(bar) > 0) return bar;
        }
        return null;
    }

    private int getCraftableBarsFromBank(BarLevels bar)
    {
        ReqOres req = ReqOres.valueOf(bar.name());
        int primaryBars = Math.max(0, Rs2Bank.count(req.getPrimaryOreName())) / req.getPrimaryOreAmount();
        if (!req.hasSecondaryOre()) return primaryBars;
        int secondaryBars = Math.max(0, Rs2Bank.count(req.getSecondaryOreName())) / req.getSecondaryOreAmount();
        return Math.min(primaryBars, secondaryBars);
    }

    private boolean ensureOreInventoryForTargetBar(BarLevels bar)
    {
        ReqOres req = ReqOres.valueOf(bar.name());
        if (hasBalancedOreInventory(req))
        {
            bankInteractionSentAt = 0L;
            if (Rs2Bank.isOpen())
            {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
                return false;
            }
            return true;
        }

        if (!openWorkBank()) return false;
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;

        Microbot.status = "Preparing " + bar.getDisplayName();
        if (!Rs2Bank.setWithdrawAsItem()) return false;

        if (!Rs2Inventory.isEmpty())
        {
            if (!Rs2Bank.depositAll()) return false;
            if (!sleepUntil(Rs2Inventory::isEmpty, 3_000)) return false;
        }

        int cycles = getBarsToWithdrawForInventory(req);
        if (cycles <= 0)
        {
            Microbot.status = "Out of ores for " + bar.getDisplayName();
            return false;
        }

        int primaryTarget = cycles * req.getPrimaryOreAmount();
        int secondaryTarget = req.hasSecondaryOre() ? cycles * req.getSecondaryOreAmount() : 0;
        if (!withdrawAndWait(req.getPrimaryOreName(), primaryTarget)) return false;
        if (req.hasSecondaryOre() && !withdrawAndWait(req.getSecondaryOreName(), secondaryTarget)) return false;

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
        if (!hasExactOreInventory(req, primaryTarget, secondaryTarget))
        {
            Microbot.status = "Smelting inventory setup failed";
            return false;
        }

        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;
        return true;
    }

    private boolean openWorkBank()
    {
        if (Rs2Bank.isOpen())
        {
            bankInteractionSentAt = 0L;
            return true;
        }

        Microbot.status = "Walking to nearest bank";
        if (!Rs2Player.isMoving())
        {
            if (Rs2Bank.openBank() && sleepUntil(Rs2Bank::isOpen, 3_000))
            {
                bankInteractionSentAt = 0L;
                return true;
            }
            Rs2Bank.walkToBankAndUseBank();
        }
        return false;
    }

    private boolean withdrawAndWait(String oreName, int wanted)
    {
        if (oreName == null || wanted <= 0) return true;
        int current = Rs2Inventory.count(oreName);
        if (current == wanted) return true;
        if (current > wanted) return false;
        if (!Rs2Bank.withdrawX(oreName, wanted - current)) return false;
        if (!sleepUntil(() -> Rs2Inventory.count(oreName) >= wanted, 3_000)) return false;
        return Rs2Inventory.count(oreName) == wanted;
    }

    private boolean hasBalancedOreInventory(ReqOres req)
    {
        if (!hasRequiredOresInInventory(req)) return false;
        if (!req.hasSecondaryOre()) return true;
        int primaryCount = Rs2Inventory.count(req.getPrimaryOreName());
        int secondaryCount = Rs2Inventory.count(req.getSecondaryOreName());
        return primaryCount * req.getSecondaryOreAmount() == secondaryCount * req.getPrimaryOreAmount();
    }

    private boolean hasExactOreInventory(ReqOres req, int primaryTargetAmount, int secondaryTargetAmount)
    {
        if (Rs2Inventory.count(req.getPrimaryOreName()) != primaryTargetAmount) return false;
        return !req.hasSecondaryOre() || Rs2Inventory.count(req.getSecondaryOreName()) == secondaryTargetAmount;
    }

    private int getBarsToWithdrawForInventory(ReqOres req)
    {
        int maxBarsFromPrimary = Math.max(0, Rs2Bank.count(req.getPrimaryOreName())) / req.getPrimaryOreAmount();
        if (maxBarsFromPrimary <= 0) return 0;
        int maxBarsFromBank = maxBarsFromPrimary;
        if (req.hasSecondaryOre())
        {
            int maxBarsFromSecondary = Math.max(0, Rs2Bank.count(req.getSecondaryOreName())) / req.getSecondaryOreAmount();
            maxBarsFromBank = Math.min(maxBarsFromPrimary, maxBarsFromSecondary);
        }
        int oresPerBar = req.getPrimaryOreAmount() + (req.hasSecondaryOre() ? req.getSecondaryOreAmount() : 0);
        return Math.min(maxBarsFromBank, 28 / oresPerBar);
    }

    private boolean hasRequiredOresInInventory(ReqOres req)
    {
        if (Rs2Inventory.count(req.getPrimaryOreName()) < req.getPrimaryOreAmount()) return false;
        return !req.hasSecondaryOre() || Rs2Inventory.count(req.getSecondaryOreName()) >= req.getSecondaryOreAmount();
    }

    private boolean ensureInTargetArea()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && targetArea.toWorldArea().contains(player))
        {
            clearTargetAreaWalkIfNeeded();
            return true;
        }

        if (KspWalkerGuard.walkToDestination(
                "Smelting:target-area",
                this::getAreaCenter,
                targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS))
        {
            lastWebWalkAtMs = System.currentTimeMillis();
            walkingToTargetArea = true;
        }
        return false;
    }

    private void clearTargetAreaWalkIfNeeded()
    {
        if (!walkingToTargetArea) return;
        KspWalkerGuard.clearActiveWalker("ksp_account_builder_smelting_reached_area");
        KspWalkerGuard.clear("Smelting:target-area");
        walkingToTargetArea = false;
    }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        return new WorldPoint(centerX, centerY, targetArea.getSouthWest().getPlane());
    }

    private void smeltAtFurnace(BarLevels bar)
    {
        if (Rs2Player.isAnimating())
        {
            lastSmeltAnimationAtMs = System.currentTimeMillis();
            return;
        }
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            return;
        }
        if (handleSmeltSelection(bar) || handleProductionWidget(bar))
        {
            furnaceInteractionSentAt = 0L;
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !targetArea.toWorldArea().contains(player)) return;
        clearTargetAreaWalkIfNeeded();

        long now = System.currentTimeMillis();
        if (furnaceInteractionSentAt > 0L && now - furnaceInteractionSentAt < TARGET_INTERACTION_TIMEOUT_MS)
        {
            Microbot.status = Rs2Player.isMoving() ? "Approaching furnace" : "Waiting for smelting interface";
            return;
        }
        if (Rs2Player.isMoving() || Rs2Player.isInteracting()) return;

        furnaceInteractionSentAt = 0L;
        Rs2TileObjectModel furnace = findNearbyFurnaceInTargetArea();
        if (furnace == null)
        {
            Microbot.status = "Waiting for " + targetArea.getDisplayName();
            if (KspLiveScene.interact("Furnace", Rs2Player.getWorldLocation(), 12, "Smelt"))
                furnaceInteractionSentAt = now;
            return;
        }

        Microbot.status = "Opening furnace interface";
        if (furnace.click("Smelt")) furnaceInteractionSentAt = now;
    }

    private Rs2TileObjectModel findNearbyFurnaceInTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) return null;
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName("Furnace")
                .within(playerLocation, 12)
                .where(obj -> obj.getWorldLocation() != null && targetArea.toWorldArea().contains(obj.getWorldLocation()))
                .nearestReachable(12));
    }

    private boolean handleSmeltSelection(BarLevels bar)
    {
        if (Rs2Widget.findWidget("What would you like to smelt?", null, false) == null)
        {
            clearProductionActionIfWidgetClosed();
            return false;
        }
        if (isProductionActionPending("smelt-selection:" + bar.name())) return true;
        if (Rs2Widget.clickWidget(bar.getDisplayName())) markProductionAction("smelt-selection:" + bar.name());
        return true;
    }

    private boolean handleProductionWidget(BarLevels bar)
    {
        if (!Rs2Widget.isProductionWidgetOpen())
        {
            clearProductionActionIfWidgetClosed();
            return false;
        }
        String key = "production:" + bar.name();
        if (isProductionActionPending(key)) return true;
        if (!selectProductionBar(bar)) return true;
        markProductionAction(key);
        Rs2Keyboard.keyPress(32);
        furnaceInteractionSentAt = 0L;
        beginSmeltingBatch(bar);
        return true;
    }

    private void beginSmeltingBatch(BarLevels bar)
    {
        smeltingBatchActive = true;
        smeltingBatchRemainingCycles = getCraftableBarsFromInventory(bar);
        smeltingBatchLastProgressAtMs = System.currentTimeMillis();
        lastSmeltAnimationAtMs = 0L;
    }

    private boolean isSmeltingBatchActive(BarLevels bar)
    {
        if (!smeltingBatchActive || bar == null) return false;
        int remainingCycles = getCraftableBarsFromInventory(bar);
        long now = System.currentTimeMillis();
        if (Rs2Player.isAnimating())
        {
            lastSmeltAnimationAtMs = now;
            smeltingBatchLastProgressAtMs = now;
        }
        if (remainingCycles < smeltingBatchRemainingCycles)
        {
            smeltingBatchRemainingCycles = remainingCycles;
            smeltingBatchLastProgressAtMs = now;
        }
        if (remainingCycles <= 0)
        {
            clearSmeltingBatch();
            return false;
        }
        if (now - smeltingBatchLastProgressAtMs < SMELTING_BATCH_STALL_TIMEOUT_MS) return true;
        clearSmeltingBatch();
        return false;
    }

    private int getCraftableBarsFromInventory(BarLevels bar)
    {
        if (bar == null) return 0;
        ReqOres req = ReqOres.valueOf(bar.name());
        int primaryCycles = Rs2Inventory.count(req.getPrimaryOreName()) / req.getPrimaryOreAmount();
        if (!req.hasSecondaryOre()) return Math.max(0, primaryCycles);
        int secondaryCycles = Rs2Inventory.count(req.getSecondaryOreName()) / req.getSecondaryOreAmount();
        return Math.max(0, Math.min(primaryCycles, secondaryCycles));
    }

    private void clearSmeltingBatch()
    {
        smeltingBatchActive = false;
        smeltingBatchRemainingCycles = 0;
        smeltingBatchLastProgressAtMs = 0L;
        lastSmeltAnimationAtMs = 0L;
    }

    private boolean isProductionActionPending(String key)
    {
        if (pendingProductionActionAtMs == 0L || pendingProductionActionKey == null) return false;
        long elapsed = System.currentTimeMillis() - pendingProductionActionAtMs;
        if (!pendingProductionActionKey.equals(key))
        {
            if (elapsed >= PRODUCTION_ACTION_START_TIMEOUT_MS) clearPendingProductionAction();
            return false;
        }
        if (Rs2Player.isAnimating())
        {
            clearPendingProductionAction();
            return true;
        }
        if (elapsed < PRODUCTION_ACTION_START_TIMEOUT_MS) return true;
        clearPendingProductionAction();
        return false;
    }

    private void markProductionAction(String key)
    {
        pendingProductionActionKey = key;
        pendingProductionActionAtMs = System.currentTimeMillis();
    }

    private void clearProductionActionIfWidgetClosed()
    {
        if (!Rs2Widget.isProductionWidgetOpen()
                && Rs2Widget.findWidget("What would you like to smelt?", null, false) == null)
        {
            clearPendingProductionAction();
        }
    }

    private void clearPendingProductionAction()
    {
        pendingProductionActionAtMs = 0L;
        pendingProductionActionKey = null;
    }

    private boolean selectProductionBar(BarLevels bar)
    {
        boolean selected = Rs2Widget.clickWidget(bar.getDisplayName(), Optional.of(270), 13, false);
        if (!selected)
        {
            selected = Rs2Widget.clickWidget(bar.getDisplayName(), true)
                    || Rs2Widget.clickWidget(bar.getDisplayName(), false);
        }
        return selected;
    }

    private boolean interactGameObjectWithoutCamera(TileObject tileObject, String action)
    {
        if (!(tileObject instanceof GameObject) || action == null || action.isBlank()) return false;
        GameObject object = (GameObject) tileObject;
        ObjectComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition resolved = Microbot.getClient().getObjectDefinition(object.getId());
            if (resolved != null && resolved.getImpostorIds() != null && resolved.getImpostor() != null) resolved = resolved.getImpostor();
            return resolved;
        }).orElse(null);
        if (composition == null || composition.getActions() == null) return false;
        String[] actions = composition.getActions();
        int actionIndex = -1;
        for (int i = 0; i < actions.length; i++)
        {
            if (actions[i] != null && action.equalsIgnoreCase(actions[i])) { actionIndex = i; break; }
        }
        if (actionIndex < 0) return false;
        MenuAction menuAction = gameObjectMenuAction(actionIndex);
        if (menuAction == null) return false;
        int sceneX = object.getLocalLocation().getSceneX();
        int sceneY = object.getLocalLocation().getSceneY();
        if (object.sizeX() > 1) sceneX -= object.sizeX() / 2;
        if (object.sizeY() > 1) sceneY -= object.sizeY() / 2;
        NewMenuEntry entry = new NewMenuEntry()
                .param0(sceneX).param1(sceneY).opcode(menuAction.getId()).identifier(object.getId())
                .itemId(-1).option(actions[actionIndex]).target(composition.getName()).gameObject(object);
        Microbot.doInvoke(entry, Rs2UiHelper.getObjectClickbox(object));
        return true;
    }

    private MenuAction gameObjectMenuAction(int actionIndex)
    {
        switch (actionIndex)
        {
            case 0: return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 1: return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 2: return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 3: return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 4: return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default: return null;
        }
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging) KspTaskDebug.info(log, true, "Smelting", message, args);
    }

    public void shutdown()
    {
        lastWebWalkAtMs = 0L;
        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;
        lastSmeltAnimationAtMs = 0L;
        clearSmeltingBatch();
        clearPendingProductionAction();
        walkingToTargetArea = false;
        KspWalkerGuard.clear("Smelting:target-area");
        super.shutdown();
    }

    public SmeltArea getTargetArea() { return targetArea; }
    public BarLevels getTargetBar() { return targetBar; }
}
