package net.runelite.client.plugins.microbot.kspwillowchopper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class KspWillowChopperScript extends Script {
    public static final int WILLOW_LOG_ID = ItemID.WILLOW_LOGS;
    private static final int TINDERBOX_ID = ItemID.TINDERBOX;
    private static final int BURN_INTERFACE_WIDGET = 17694735;
    private static final int FIRE_ID = ObjectID.FIRE;
    private static final int FIRE_ID_ALT = 49927;

    private final KspWillowChopperPlugin plugin;

    private volatile String status = "Idle";
    private volatile boolean campfireNearby;
    private volatile boolean burningActive;
    private volatile boolean sessionStarted;

    private long startTimeMillis;
    private int startWoodcuttingXp;
    private int startFiremakingXp;
    private int logsBanked;
    private int logsBurned;
    private int campfiresLit;

    private int lastBurnLogCount = -1;
    private long lastBurnProgressMillis = 0L;
    private long lastWillowClickMillis = 0L;

    @Inject
    public KspWillowChopperScript(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(KspWillowChopperConfig config) {
        startTimeMillis = System.currentTimeMillis();
        startWoodcuttingXp = skillXp(Skill.WOODCUTTING);
        startFiremakingXp = skillXp(Skill.FIREMAKING);
        logsBanked = 0;
        logsBurned = 0;
        campfiresLit = 0;
        lastBurnLogCount = -1;
        burningActive = false;
        campfireNearby = false;
        sessionStarted = true;
        status = "Starting";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    return;
                }

                if (!super.run()) {
                    if (plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                        status = "Forestry: " + plugin.getCurrentForestryEvent();
                    }
                    return;
                }

                if (plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                    plugin.setCurrentForestryEvent(KspForestryEvent.NONE);
                }

                if (Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) < 30) {
                    status = "Need 30 Woodcutting";
                    return;
                }

                if (!hasAxe()) {
                    status = "No axe equipped/in inventory";
                    return;
                }

                if (config.bankLogs()) {
                    burningActive = false;
                    lastBurnLogCount = -1;
                    campfireNearby = false;

                    if (Rs2Inventory.isFull()) {
                        handleDirectBanking();
                    } else {
                        clickWillowDirect();
                    }
                } else {
                    handleBurnMode();
                }
            } catch (Exception ex) {
                status = "Error - check logs";
                Microbot.logStackTrace("KspWillowChopperScript", ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        sessionStarted = false;
        burningActive = false;
        campfireNearby = false;
        status = "Idle";
    }

    private boolean hasAxe() {
        return Rs2Inventory.hasItem("axe") || Rs2Equipment.isWearing("axe");
    }

    private void handleDirectBanking() {
        status = "Opening bank directly";
        int before = Rs2Inventory.count(WILLOW_LOG_ID);

        if (!Rs2Bank.openBank()) {
            status = "Nearby bank not clickable";
            return;
        }

        if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
            status = "Waiting for bank";
            return;
        }

        status = "Banking willow logs";
        Rs2Bank.depositAll(WILLOW_LOG_ID);
        sleepUntil(() -> Rs2Inventory.count(WILLOW_LOG_ID) < before, 3000);

        int after = Rs2Inventory.count(WILLOW_LOG_ID);
        logsBanked += Math.max(0, before - after);

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        status = "Clicking willow directly";
        clickWillowDirect();
    }

    private void handleBurnMode() {
        Rs2TileObjectModel fire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = fire != null;

        trackBurnProgress();

        if (burningActive) {
            if (Rs2Inventory.count(WILLOW_LOG_ID) == 0) {
                burningActive = false;
                lastBurnLogCount = -1;
                status = "Burn complete";
                return;
            }

            if (System.currentTimeMillis() - lastBurnProgressMillis < 5000) {
                status = "Burning willow logs";
                return;
            }

            fire = findCampfire(Rs2Player.getWorldLocation(), 15);
            if (fire != null) {
                startCampfireBurn(fire);
                return;
            }

            burningActive = false;
            lastBurnLogCount = -1;
        }

        if (!Rs2Inventory.isFull()) {
            if (fire == null && !Rs2Inventory.hasItem(TINDERBOX_ID) && Rs2Inventory.emptySlotCount() <= 1) {
                ensureTinderbox();
                return;
            }

            clickWillowDirect();
            return;
        }

        fire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = fire != null;

        if (fire == null) {
            createCampfire();
            return;
        }

        startCampfireBurn(fire);
    }

    private void ensureTinderbox() {
        if (Rs2Inventory.hasItem(TINDERBOX_ID)) {
            return;
        }

        if (Rs2Inventory.emptySlotCount() <= 0) {
            status = "Need slot for tinderbox";
            return;
        }

        status = "Getting tinderbox";
        if (!Rs2Bank.openBank() || !sleepUntil(Rs2Bank::isOpen, 4000)) {
            status = "Need tinderbox / nearby bank";
            return;
        }

        Rs2Bank.withdrawItem(true, "Tinderbox");
        sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_ID), 3000);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2500);
    }

    private void createCampfire() {
        if (!Rs2Inventory.hasItem(TINDERBOX_ID)) {
            if (Rs2Inventory.emptySlotCount() > 0) {
                ensureTinderbox();
            } else {
                status = "Making room for tinderbox";
                Rs2Inventory.drop(WILLOW_LOG_ID);
            }
            return;
        }

        if (!Rs2Inventory.hasItem(WILLOW_LOG_ID)) {
            return;
        }

        status = "Creating campfire";
        int before = Rs2Inventory.count(WILLOW_LOG_ID);
        Rs2Inventory.combine("Tinderbox", "Willow logs");

        if (Rs2Player.waitForXpDrop(Skill.FIREMAKING, 5000)) {
            int after = Rs2Inventory.count(WILLOW_LOG_ID);
            int used = Math.max(1, before - after);
            logsBurned += used;
            campfiresLit++;
            campfireNearby = true;
            lastBurnLogCount = after;
            lastBurnProgressMillis = System.currentTimeMillis();
            status = "Campfire created";
            return;
        }

        status = "Repositioning for fire";
        WorldPoint spot = findLightableTile(Rs2Player.getWorldLocation());
        if (spot != null) {
            Rs2Walker.walkTo(spot, 0);
            sleepUntil(() -> !Rs2Player.isMoving(), 3000);
        }
    }

    private void startCampfireBurn(Rs2TileObjectModel fire) {
        if (fire == null || !Rs2Inventory.hasItem(WILLOW_LOG_ID)) {
            return;
        }

        int count = Rs2Inventory.count(WILLOW_LOG_ID);
        status = "Adding logs to campfire";

        Rs2Inventory.use(WILLOW_LOG_ID);
        if (!sleepUntil(Rs2Inventory::isItemSelected, 2000)) {
            status = "Selecting willow log";
            return;
        }

        Rs2GameObject.interact(fire);
        if (!sleepUntil(() -> Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null, 5000)) {
            status = "Waiting for Burn dialog";
            return;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        burningActive = true;
        lastBurnLogCount = count;
        lastBurnProgressMillis = System.currentTimeMillis();
        status = "Burning willow logs";
    }

    private void trackBurnProgress() {
        if (!burningActive || lastBurnLogCount < 0) {
            return;
        }

        int count = Rs2Inventory.count(WILLOW_LOG_ID);
        if (count < lastBurnLogCount) {
            logsBurned += (lastBurnLogCount - count);
            lastBurnLogCount = count;
            lastBurnProgressMillis = System.currentTimeMillis();
        }
    }

    private void clickWillowDirect() {
        if (Rs2Bank.isOpen()) {
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            status = "Chopping willow";
            return;
        }

        if (System.currentTimeMillis() - lastWillowClickMillis < 1800) {
            return;
        }

        Rs2TileObjectModel willow = Microbot.getRs2TileObjectCache()
                .query()
                .withName("willow tree")
                .nearestOnClientThread();

        if (willow == null) {
            status = "No willow tree loaded";
            return;
        }

        status = "Clicking willow";
        if (willow.click("Chop down")) {
            lastWillowClickMillis = System.currentTimeMillis();
            Rs2Player.waitForAnimation(3000);
            status = "Chopping willow";
        } else {
            status = "Willow click failed";
        }
    }

    private Rs2TileObjectModel findCampfire(WorldPoint anchor, int radius) {
        if (anchor == null) {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Rs2TileObjectModel target = Microbot.getRs2TileObjectCache()
                    .query()
                    .withNameContains("ampfire")
                    .nearest(anchor, radius);

            if (target == null) {
                target = Microbot.getRs2TileObjectCache()
                        .query()
                        .where(o -> o.getId() == FIRE_ID || o.getId() == FIRE_ID_ALT)
                        .nearest(anchor, radius);
            }

            return target;
        }).orElse(null);
    }

    private WorldPoint findLightableTile(WorldPoint from) {
        if (from == null) {
            return null;
        }

        int[][] offsets = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
        };

        for (int[] offset : offsets) {
            WorldPoint candidate = new WorldPoint(
                    from.getX() + offset[0],
                    from.getY() + offset[1],
                    from.getPlane()
            );

            if (Rs2Tile.isWalkable(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private int skillXp(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(skill)
        ).orElse(0);
    }

    public String getStatus() { return status; }
    public boolean isCampfireNearby() { return campfireNearby; }
    public boolean isBurningActive() { return burningActive; }
    public boolean hasSessionStarted() { return sessionStarted && startTimeMillis > 0; }
    public long getRuntimeMillis() { return hasSessionStarted() ? Math.max(0L, System.currentTimeMillis() - startTimeMillis) : 0L; }
    public int getWoodcuttingXpGained() { return Math.max(0, skillXp(Skill.WOODCUTTING) - startWoodcuttingXp); }
    public int getFiremakingXpGained() { return Math.max(0, skillXp(Skill.FIREMAKING) - startFiremakingXp); }
    public int getLogsBanked() { return logsBanked; }
    public int getLogsBurned() { return logsBurned; }
    public int getCampfiresLit() { return campfiresLit; }
}
