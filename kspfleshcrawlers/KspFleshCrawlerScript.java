package net.runelite.client.plugins.microbot.kspfleshcrawlers;


import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@SuppressWarnings({"deprecation", "removal"})
public class KspFleshCrawlerScript extends Script {
    private static final String NPC_NAME = "Flesh Crawler";
    private static final long ATTACK_COMMIT_GRACE_MS = 3_000L;
    private static final long COMBAT_STALL_TIMEOUT_MS = 6_000L;
    private static final long COMBAT_RECOVERY_COOLDOWN_MS = 4_000L;

    private final CombatTrainingController trainingController = new CombatTrainingController();
    private final StrongholdNavigator navigator = new StrongholdNavigator();

    private volatile FleshCrawlerState state = FleshCrawlerState.INITIALIZING;
    private volatile String lastAction = "Starting";
    private volatile int kills;
    private volatile int itemsLooted;
    private volatile int bonesBuried;
    private volatile int foodEaten;

    private volatile boolean bankTripActive;
    private volatile boolean returningFromBank;
    private volatile long attackCommitUntilMs;
    private volatile int trackedNpcIndex = -1;
    private volatile boolean trackedNpcCounted;
    private volatile boolean retaliateConfigured;

    // Combat watchdog. RuneLite can keep interaction pointers alive even when no
    // attack cycle is actually progressing. Track real combat activity so a stale
    // pointer cannot leave the script in FIGHTING forever.
    private volatile long lastCombatActivityMs;
    private volatile long lastCombatRecoveryMs;
    private volatile int lastObservedOpponentIndex = -1;
    private volatile int lastObservedPlayerAnimation = -1;
    private volatile int lastObservedNpcAnimation = -1;
    private volatile int lastObservedPlayerHp = -1;
    private volatile int lastObservedNpcHealthRatio = Integer.MIN_VALUE;
    private volatile String combatWatchdogStatus = "Idle";

    public boolean run(KspFleshCrawlerConfig config) {
        Microbot.enableAutoRunOn = true;
        resetSession();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                updateKillTracking();
                CombatSnapshot combat = observeCombat();

                if (navigator.handleDialogue()) {
                    mirrorNavigationAction();
                    return;
                }

                if (bankTripActive) {
                    handleBankTrip(config);
                    return;
                }

                WorldPoint player = Rs2Player.getWorldLocation();
                boolean atFightRoom = player != null
                        && StrongholdZones.FLESH_CRAWLER_ROOM.contains(player)
                        && player.distanceTo(StrongholdNavigator.FIGHT_TARGET) <= 4;

                if (!atFightRoom) {
                    if (!config.autoTravel()) {
                        state = FleshCrawlerState.WAITING;
                        lastAction = "Auto travel disabled - move to 2040,5188";
                        return;
                    }

                    state = returningFromBank
                            ? FleshCrawlerState.RETURNING_TO_FIGHT
                            : FleshCrawlerState.WALKING_TO_FIGHT;

                    boolean arrived = navigator.tickToFight(config.useWarPortal(), config.useWebWalker());
                    mirrorNavigationAction();
                    if (navigator.getError() != null) state = FleshCrawlerState.NAVIGATION_ERROR;

                    if (!arrived) return;
                    returningFromBank = false;
                    retaliateConfigured = false;
                }

                trainingController.update(config);
                if (config.stopAtGoals() && trainingController.allEnabledGoalsReached(config)) {
                    state = FleshCrawlerState.GOALS_REACHED;
                    lastAction = "All enabled combat goals reached";
                    return;
                }

                if (handleHealing(config)) return;

                if (handleNoFood(config)) return;

                if (!retaliateConfigured) {
                    Rs2Combat.setAutoRetaliate(config.autoRetaliate());
                    retaliateConfigured = true;
                }

                if (combat.engaged) {
                    trackCurrentOpponent();
                    if (handleStalledCombat(combat)) {
                        return;
                    }
                    state = FleshCrawlerState.FIGHTING;
                    lastAction = combat.crawlerIndex >= 0
                            ? "Fighting Flesh Crawler"
                            : "Waiting for current combat to finish";
                    return;
                }

                if (config.buryBones() && handleBones(config)) return;
                if (config.lootOwnDrops() && handleOwnDrops(config)) return;
                if (config.lootEnabled() && handleConfiguredLoot(config)) return;
                if (config.usePotions() && handlePotions()) return;

                attackNextCrawler(config);
            } catch (Exception ex) {
                Microbot.log("KSP Flesh Crawlers v2 script error: " + ex.getMessage());
            }
        }, 0, 350, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        navigator.reset();
        state = FleshCrawlerState.INITIALIZING;
        lastAction = "Stopped";
        bankTripActive = false;
        returningFromBank = false;
        retaliateConfigured = false;
        resetCombatWatchdog();
    }

    private void resetSession() {
        state = FleshCrawlerState.INITIALIZING;
        lastAction = "Starting";
        kills = 0;
        itemsLooted = 0;
        bonesBuried = 0;
        foodEaten = 0;
        bankTripActive = false;
        returningFromBank = false;
        attackCommitUntilMs = 0L;
        trackedNpcIndex = -1;
        trackedNpcCounted = false;
        retaliateConfigured = false;
        resetCombatWatchdog();
        navigator.reset();
    }

    private void beginBankTrip() {
        bankTripActive = true;
        returningFromBank = false;
        retaliateConfigured = false;
        navigator.reset();
        resetCombatWatchdog();
        // Prevent auto-retaliate from immediately acquiring another crawler while
        // we are trying to leave after the current fight has ended.
        Rs2Combat.setAutoRetaliate(false);
    }

    private void handleBankTrip(KspFleshCrawlerConfig config) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        if (StrongholdZones.isOnFloor1(player) || StrongholdZones.isOnFloor2(player)) {
            state = FleshCrawlerState.WALKING_TO_BANK;
            navigator.tickToSurface(config.useWebWalker());
            mirrorNavigationAction();
            if (navigator.getError() != null) state = FleshCrawlerState.NAVIGATION_ERROR;
            return;
        }

        if (!Rs2Bank.isNearBank(10)) {
            state = FleshCrawlerState.WALKING_TO_BANK;
            lastAction = "Walking to bank";
            if (!Rs2Player.isMoving()) Rs2Bank.walkToBank();
            return;
        }

        state = FleshCrawlerState.BANKING;
        lastAction = "Banking and restocking " + config.foodName();

        if (!Rs2Bank.isOpen() && !KspVerifiedBank.openBank()) return;

        List<String> keep = new ArrayList<>();
        keep.add(config.foodName());
        keep.addAll(defaultPotionBaseNames());
        Rs2Bank.depositAllExcept(keep);

        int currentFood = Rs2Inventory.count(config.foodName(), false);
        int needed = Math.max(0, config.foodAmount() - currentFood);
        if (needed > 0) {
            Rs2Bank.withdrawX(config.foodName(), needed, false);
            sleepUntil(() -> Rs2Inventory.count(config.foodName(), false) >= Math.min(config.foodAmount(), currentFood + needed), 3_000);
        }

        if (!Rs2Inventory.contains(config.foodName(), false)) {
            state = FleshCrawlerState.OUT_OF_FOOD;
            lastAction = "No " + config.foodName() + " available in bank";
            return;
        }

        Rs2Bank.closeBank();
        bankTripActive = false;
        returningFromBank = true;
        navigator.reset();
        state = FleshCrawlerState.RETURNING_TO_FIGHT;
        lastAction = "Returning to Flesh Crawlers";
    }

    private boolean handleNoFood(KspFleshCrawlerConfig config) {
        if (!config.useHealing() || Rs2Inventory.contains(config.foodName(), false)) return false;

        if (!config.bankForFood()) {
            int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            if (hp <= Math.max(2, config.healAtHp())) {
                state = FleshCrawlerState.OUT_OF_FOOD;
                lastAction = "Out of food - banking disabled";
                return true;
            }
            return false;
        }

        if (hasActiveCombatEngagement()) {
            if (retaliateConfigured) {
                Rs2Combat.setAutoRetaliate(false);
                retaliateConfigured = false;
            }
            state = FleshCrawlerState.OUT_OF_FOOD;
            lastAction = "Out of food - finishing current fight";
            return true;
        }

        beginBankTrip();
        state = FleshCrawlerState.WALKING_TO_BANK;
        lastAction = "Starting food bank trip";
        return true;
    }

    private boolean handleHealing(KspFleshCrawlerConfig config) {
        if (!config.useHealing() || !Rs2Inventory.contains(config.foodName(), false)) return false;

        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int foodHeal = resolveFoodHeal(config.foodName(), config.unknownFoodHeal());

        // Flesh Crawler max hit is 1. Keep two hitpoints as the absolute floor,
        // while respecting a lower user-configured threshold and avoiding waste.
        int safetyFloor = 2;
        int configured = Math.min(maxHp, Math.max(safetyFloor, config.healAtHp()));
        int noWaste = Math.max(1, maxHp - foodHeal);
        int healAt = Math.max(safetyFloor, Math.min(configured, noWaste));
        if (currentHp > healAt) return false;

        int foodBefore = Rs2Inventory.count(config.foodName(), false);
        lastAction = "Eating " + config.foodName() + " at " + currentHp + " HP";
        if (Rs2Inventory.interact(config.foodName(), "Eat", false)) {
            state = FleshCrawlerState.HEALING;
            boolean foodConsumed = sleepUntil(
                    () -> Rs2Inventory.count(config.foodName(), false) < foodBefore,
                    1_500
            );
            if (foodConsumed) {
                foodEaten++;
            } else {
                state = FleshCrawlerState.FIGHTING;
                lastAction = "Waiting for " + config.foodName() + " consumption confirmation";
            }
        }
        return true;
    }

    private boolean handlePotions() {
        if (drinkIfUnboosted("Super combat potion", Skill.ATTACK)
                || drinkIfUnboosted("Combat potion", Skill.ATTACK)
                || drinkIfUnboosted("Attack potion", Skill.ATTACK)
                || drinkIfUnboosted("Super attack", Skill.ATTACK)
                || drinkIfUnboosted("Strength potion", Skill.STRENGTH)
                || drinkIfUnboosted("Super strength", Skill.STRENGTH)
                || drinkIfUnboosted("Defence potion", Skill.DEFENCE)
                || drinkIfUnboosted("Super defence", Skill.DEFENCE)) {
            state = FleshCrawlerState.DRINKING_POTION;
            return true;
        }
        return false;
    }

    private boolean drinkIfUnboosted(String potionBaseName, Skill skill) {
        if (Microbot.getClient().getBoostedSkillLevel(skill) > Microbot.getClient().getRealSkillLevel(skill)) return false;
        if (!Rs2Inventory.contains(potionBaseName, false)) return false;

        lastAction = "Drinking " + potionBaseName;
        if (Rs2Inventory.interact(potionBaseName, "Drink", false)) {
            sleep(450, 700);
            return true;
        }
        return false;
    }

    private boolean handleBones(KspFleshCrawlerConfig config) {
        List<String> boneNames = parseCsv(config.boneItems());
        for (String bone : boneNames) {
            if (Rs2Inventory.contains(bone, true)) {
                state = FleshCrawlerState.BURYING;
                lastAction = "Burying " + bone;
                if (Rs2Inventory.interact(bone, "Bury", true)) {
                    bonesBuried++;
                    sleep(350, 550);
                }
                return true;
            }
        }

        if (Rs2Inventory.isFull()) return false;
        GroundItem boneDrop = findGroundItem(boneNames, config.lootRadius(), false);
        if (boneDrop == null) return false;

        state = FleshCrawlerState.BURYING;
        lastAction = "Picking up " + boneDrop.getName();
        if (Rs2GroundItem.interact(boneDrop)) {
            sleep(450, 700);
            return true;
        }
        return false;
    }

    private boolean handleOwnDrops(KspFleshCrawlerConfig config) {
        GroundItem ownDrop = findGroundItem(null, config.lootRadius(), true);
        if (ownDrop == null) return false;

        return lootGroundItem(ownDrop, "Looting own drop: ");
    }

    private boolean handleConfiguredLoot(KspFleshCrawlerConfig config) {
        List<String> lootNames = parseCsv(config.lootItems());
        if (lootNames.isEmpty()) return false;

        GroundItem drop = findGroundItem(lootNames, config.lootRadius(), false);
        if (drop == null || Rs2Inventory.isFull(drop.getName())) return false;

        return lootGroundItem(drop, "Looting ");
    }

    /**
     * A ground-item interaction only reports that a click was sent. Confirm that
     * the item reached the inventory before recording loot or entering LOOTING;
     * another enabled plugin can otherwise consume or invalidate the same drop.
     */
    private boolean lootGroundItem(GroundItem drop, String actionPrefix) {
        int countBefore = Rs2Inventory.count(drop.getName(), false);
        lastAction = actionPrefix + drop.getName();
        if (!Rs2GroundItem.interact(drop)) return false;

        if (sleepUntil(() -> Rs2Inventory.count(drop.getName(), false) > countBefore, 1_500)) {
            itemsLooted++;
            state = FleshCrawlerState.LOOTING;
            return true;
        }

        state = FleshCrawlerState.WAITING;
        lastAction = "Loot pickup not confirmed: " + drop.getName();
        return true;
    }

    private GroundItem findGroundItem(List<String> names, int radius, boolean ownOnly) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return null;

        List<String> normalized = names == null
                ? new ArrayList<>()
                : names.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toList());

        return Rs2GroundItem.getGroundItems().values().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getLocation() != null)
                .filter(item -> item.getName() != null)
                .filter(item -> StrongholdZones.FLESH_CRAWLER_ROOM.contains(item.getLocation()))
                .filter(item -> item.getLocation().distanceTo(player) <= radius)
                .filter(Rs2GroundItem::canTakeGroundItem)
                .filter(item -> !ownOnly || item.getOwnership() == TileItem.OWNERSHIP_SELF)
                .filter(item -> ownOnly || normalized.contains(item.getName().toLowerCase(Locale.ROOT)))
                .min(Comparator.comparingInt(item -> item.getLocation().distanceTo(player)))
                .orElse(null);
    }

    private CombatSnapshot observeCombat() {
        CombatSnapshot snapshot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return CombatSnapshot.NONE;
            }

            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            int playerHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int playerAnimation = player.getAnimation();

            net.runelite.api.NPC playerTarget = null;
            Actor current = player.getInteracting();
            if (current instanceof net.runelite.api.NPC) {
                net.runelite.api.NPC npc = (net.runelite.api.NPC) current;
                if (!npc.isDead() && npc.getCombatLevel() > 0) {
                    playerTarget = npc;
                }
            }

            net.runelite.api.NPC incoming = null;
            if (Microbot.getClient().getTopLevelWorldView() != null
                    && Microbot.getClient().getTopLevelWorldView().npcs() != null) {
                for (net.runelite.api.NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
                    if (npc == null || npc.isDead() || npc.getCombatLevel() < 1) continue;
                    if (npc.getInteracting() == player) {
                        incoming = npc;
                        if (NPC_NAME.equalsIgnoreCase(npc.getName())) break;
                    }
                }
            }

            net.runelite.api.NPC preferred = playerTarget != null ? playerTarget : incoming;
            boolean engaged = preferred != null;
            int opponentIndex = preferred == null ? -1 : preferred.getIndex();
            String opponentName = preferred == null ? null : preferred.getName();
            boolean crawler = opponentName != null && NPC_NAME.equalsIgnoreCase(opponentName);
            int npcAnimation = preferred == null ? -1 : preferred.getAnimation();
            int npcHealthRatio = preferred == null ? Integer.MIN_VALUE : preferred.getHealthRatio();
            boolean incomingAttack = incoming != null;

            return new CombatSnapshot(
                    engaged,
                    crawler ? opponentIndex : -1,
                    opponentIndex,
                    playerAnimation,
                    npcAnimation,
                    playerHp,
                    npcHealthRatio,
                    incomingAttack
            );
        }).orElse(CombatSnapshot.NONE);

        long now = System.currentTimeMillis();
        if (!snapshot.engaged) {
            resetObservedCombatState();
            combatWatchdogStatus = "Idle";
            return snapshot;
        }

        boolean activity = false;
        if (snapshot.opponentIndex != lastObservedOpponentIndex) activity = true;
        if (snapshot.playerAnimation != -1) activity = true;
        if (snapshot.npcAnimation != -1) activity = true;
        if (lastObservedPlayerHp >= 0 && snapshot.playerHp != lastObservedPlayerHp) activity = true;
        if (lastObservedNpcHealthRatio != Integer.MIN_VALUE
                && snapshot.npcHealthRatio != lastObservedNpcHealthRatio) activity = true;

        if (lastCombatActivityMs == 0L || activity) {
            lastCombatActivityMs = now;
        }

        lastObservedOpponentIndex = snapshot.opponentIndex;
        lastObservedPlayerAnimation = snapshot.playerAnimation;
        lastObservedNpcAnimation = snapshot.npcAnimation;
        lastObservedPlayerHp = snapshot.playerHp;
        lastObservedNpcHealthRatio = snapshot.npcHealthRatio;

        long quietMs = Math.max(0L, now - lastCombatActivityMs);
        combatWatchdogStatus = quietMs >= COMBAT_STALL_TIMEOUT_MS
                ? "Stalled " + (quietMs / 1000L) + "s"
                : "Active";
        return snapshot;
    }

    /**
     * Recover a fight only by clicking the SAME Flesh Crawler that the client says
     * is already involved with us. This keeps the previous invariant: never switch
     * to another crawler while a real combat engagement exists.
     */
    private boolean handleStalledCombat(CombatSnapshot snapshot) {
        if (!snapshot.engaged) return false;

        long now = System.currentTimeMillis();
        if (lastCombatActivityMs == 0L || now - lastCombatActivityMs < COMBAT_STALL_TIMEOUT_MS) {
            return false;
        }
        if (now - lastCombatRecoveryMs < COMBAT_RECOVERY_COOLDOWN_MS) {
            state = FleshCrawlerState.FIGHTING;
            lastAction = "Combat watchdog waiting after retry";
            return true;
        }

        // Only recover Flesh Crawler fights. If some other NPC has us engaged, do
        // not select a crawler until that engagement genuinely ends.
        if (snapshot.crawlerIndex < 0) {
            state = FleshCrawlerState.FIGHTING;
            lastAction = "Current combat is stalled - waiting";
            return true;
        }

        final int sameNpcIndex = snapshot.crawlerIndex;
        Rs2NpcModel sameCrawler = Rs2Npc.getNpcs(npc -> {
                    String name = npc.getName();
                    return npc.getIndex() == sameNpcIndex
                            && name != null
                            && NPC_NAME.equalsIgnoreCase(name)
                            && !npc.isDead()
                            && StrongholdZones.FLESH_CRAWLER_ROOM.contains(npc.getWorldLocation());
                })
                .findFirst()
                .orElse(null);

        if (sameCrawler == null) {
            // Player-side interaction pointers can outlive a despawn/death. If no
            // NPC is actually attacking us anymore, allow normal target selection.
            if (!snapshot.incomingAttack) {
                trackedNpcIndex = -1;
                trackedNpcCounted = false;
                attackCommitUntilMs = 0L;
                resetObservedCombatState();
                combatWatchdogStatus = "Cleared stale target";
                return false;
            }

            state = FleshCrawlerState.FIGHTING;
            lastAction = "Waiting for attacking Flesh Crawler";
            return true;
        }

        lastCombatRecoveryMs = now;
        state = FleshCrawlerState.FIGHTING;
        lastAction = "Recovering stalled fight - re-attacking same crawler";
        combatWatchdogStatus = "Retry same crawler";

        if (Rs2Npc.attack(sameCrawler)) {
            trackedNpcIndex = sameNpcIndex;
            trackedNpcCounted = false;
            attackCommitUntilMs = now + ATTACK_COMMIT_GRACE_MS;
            lastCombatActivityMs = now;
        }
        return true;
    }

    private boolean hasActiveCombatEngagement() {
        if (System.currentTimeMillis() < attackCommitUntilMs) return true;
        return observeCombat().engaged;
    }

    private void resetCombatWatchdog() {
        lastCombatActivityMs = 0L;
        lastCombatRecoveryMs = 0L;
        resetObservedCombatState();
        combatWatchdogStatus = "Idle";
    }

    private void resetObservedCombatState() {
        lastObservedOpponentIndex = -1;
        lastObservedPlayerAnimation = -1;
        lastObservedNpcAnimation = -1;
        lastObservedPlayerHp = -1;
        lastObservedNpcHealthRatio = Integer.MIN_VALUE;
    }

    private void attackNextCrawler(KspFleshCrawlerConfig config) {
        CombatSnapshot combat = observeCombat();
        if (combat.engaged) {
            trackCurrentOpponent();
            if (handleStalledCombat(combat)) return;
            state = FleshCrawlerState.FIGHTING;
            lastAction = combat.crawlerIndex >= 0 ? "Fighting Flesh Crawler" : "Waiting for current combat to finish";
            return;
        }

        Rs2NpcModel target = Rs2Npc.getNpcs(npc -> {
                    String name = npc.getName();
                    if (name == null || !NPC_NAME.equalsIgnoreCase(name) || npc.isDead()) return false;
                    if (!npc.isWithinDistance(StrongholdNavigator.FIGHT_TARGET, config.fightRadius())) return false;
                    if (!StrongholdZones.FLESH_CRAWLER_ROOM.contains(npc.getWorldLocation())) return false;
                    Actor interacting = npc.getInteracting();
                    return interacting == null || interacting == Microbot.getClient().getLocalPlayer();
                })
                .findFirst()
                .orElse(null);

        if (target == null) {
            state = FleshCrawlerState.WAITING;
            lastAction = "Waiting for available Flesh Crawler";
            return;
        }

        // Second guard immediately before interaction closes the auto-retaliate race.
        CombatSnapshot beforeClick = observeCombat();
        if (beforeClick.engaged) {
            trackCurrentOpponent();
            if (handleStalledCombat(beforeClick)) return;
            state = FleshCrawlerState.FIGHTING;
            lastAction = beforeClick.crawlerIndex >= 0 ? "Fighting Flesh Crawler" : "Waiting for current combat to finish";
            return;
        }

        if (Rs2Npc.attack(target)) {
            trackedNpcIndex = target.getIndex();
            trackedNpcCounted = false;
            long now = System.currentTimeMillis();
            attackCommitUntilMs = now + ATTACK_COMMIT_GRACE_MS;
            lastCombatActivityMs = now;
            combatWatchdogStatus = "Attack committed";
            state = FleshCrawlerState.FIGHTING;
            lastAction = "Attacking Flesh Crawler (level " + target.getCombatLevel() + ")";
        }
    }

    private void trackCurrentOpponent() {
        int index = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) return -1;

            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            Actor interacting = player.getInteracting();
            if (interacting instanceof net.runelite.api.NPC) {
                net.runelite.api.NPC npc = (net.runelite.api.NPC) interacting;
                String name = npc.getName();
                if (name != null && NPC_NAME.equalsIgnoreCase(name)) return npc.getIndex();
            }

            if (Microbot.getClient().getTopLevelWorldView() != null
                    && Microbot.getClient().getTopLevelWorldView().npcs() != null) {
                for (net.runelite.api.NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
                    if (npc == null || npc.isDead() || npc.getInteracting() != player) continue;
                    String name = npc.getName();
                    if (name != null && NPC_NAME.equalsIgnoreCase(name)) return npc.getIndex();
                }
            }
            return -1;
        }).orElse(-1);

        if (index >= 0 && index != trackedNpcIndex) {
            trackedNpcIndex = index;
            trackedNpcCounted = false;
        }
    }

    private void updateKillTracking() {
        if (trackedNpcIndex < 0 || trackedNpcCounted) return;
        final int npcIndex = trackedNpcIndex;

        Boolean dead = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getTopLevelWorldView() == null
                    || Microbot.getClient().getTopLevelWorldView().npcs() == null) return null;

            for (net.runelite.api.NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
                if (npc != null && npc.getIndex() == npcIndex) return npc.isDead();
            }
            return null;
        }).orElse(null);

        if (Boolean.TRUE.equals(dead)) {
            kills++;
            trackedNpcCounted = true;
            attackCommitUntilMs = 0L;
            resetObservedCombatState();
            lastCombatActivityMs = 0L;
            combatWatchdogStatus = "Kill confirmed";
            lastAction = "Flesh Crawler defeated";
        }
    }

    private int resolveFoodHeal(String foodName, int fallback) {
        if (foodName != null) {
            for (Rs2Food food : Rs2Food.values()) {
                if (food.getName().equalsIgnoreCase(foodName.trim())) return Math.max(1, food.getHeal());
            }
        }
        return Math.max(1, fallback);
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> defaultPotionBaseNames() {
        return Arrays.asList("Super combat potion", "Combat potion", "Attack potion", "Super attack",
                "Strength potion", "Super strength", "Defence potion", "Super defence");
    }

    private void mirrorNavigationAction() {
        lastAction = navigator.getAction();
    }

    public FleshCrawlerState getState() { return state; }
    public String getLastAction() { return lastAction; }
    public int getKills() { return kills; }
    public int getItemsLooted() { return itemsLooted; }
    public int getBonesBuried() { return bonesBuried; }
    public int getFoodEaten() { return foodEaten; }
    public WorldPoint getFightTarget() { return StrongholdNavigator.FIGHT_TARGET; }
    public Skill getCurrentTrainingSkill() { return trainingController.getCurrentTrainingSkill(); }
    public String getNavigationStage() { return navigator.getStage(); }
    public String getNavigationZone() { return navigator.getCurrentZoneName(); }
    public String getNavigationError() { return navigator.getError(); }
    public String getNavigationMovementMode() { return navigator.getMovementMode(); }
    public String getNavigationNextDoor() { return navigator.getNextDoorInfo(); }
    public String getCombatWatchdogStatus() { return combatWatchdogStatus; }

    private static final class CombatSnapshot {
        private static final CombatSnapshot NONE = new CombatSnapshot(false, -1, -1, -1, -1, -1, Integer.MIN_VALUE, false);

        private final boolean engaged;
        private final int crawlerIndex;
        private final int opponentIndex;
        private final int playerAnimation;
        private final int npcAnimation;
        private final int playerHp;
        private final int npcHealthRatio;
        private final boolean incomingAttack;

        private CombatSnapshot(boolean engaged, int crawlerIndex, int opponentIndex, int playerAnimation,
                               int npcAnimation, int playerHp, int npcHealthRatio, boolean incomingAttack) {
            this.engaged = engaged;
            this.crawlerIndex = crawlerIndex;
            this.opponentIndex = opponentIndex;
            this.playerAnimation = playerAnimation;
            this.npcAnimation = npcAnimation;
            this.playerHp = playerHp;
            this.npcHealthRatio = npcHealthRatio;
            this.incomingAttack = incomingAttack;
        }
    }
}
