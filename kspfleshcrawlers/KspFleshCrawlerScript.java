package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings({"deprecation", "removal"})
public class KspFleshCrawlerScript extends Script {
    private static final String NPC_NAME = "Flesh Crawler";

    /** Dense Flesh Crawler room in the south-east of the Catacomb of Famine. */
    private static final WorldPoint DEFAULT_FIGHT_POINT = new WorldPoint(2041, 5189, 0);

    /** Current Stronghold of Security floor coordinate bands used to describe navigation progress. */
    private static final int FLOOR_1_MIN_X = 1855;
    private static final int FLOOR_1_MAX_X = 1920;
    private static final int FLOOR_1_MIN_Y = 5184;
    private static final int FLOOR_1_MAX_Y = 5248;
    private static final int FLOOR_2_MIN_X = 1983;
    private static final int FLOOR_2_MAX_X = 2048;
    private static final int FLOOR_2_MIN_Y = 5184;
    private static final int FLOOR_2_MAX_Y = 5248;

    /** Security-question answers used by the Stronghold QuestHelper. */
    private static final String[] STRONGHOLD_CORRECT_ANSWERS = {
            "No.",
            "Me.",
            "Nobody.",
            "Talk to any banker.",
            "Nothing, it's a fake.",
            "Delete it - it's a fake!",
            "Don't give them my password.",
            "Report the player for phishing.",
            "Use the Account Recovery system.",
            "No way! I'm reporting you to Jagex!",
            "No, you should never buy an account.",
            "Secure my device and reset my password.",
            "Decline the offer and report that player.",
            "The birthday of a famous person or event.",
            "Only on the Old School RuneScape website.",
            "Read the text and follow the advice given.",
            "Virus scan my device then change my password.",
            "Report the incident and do not click any links.",
            "Don't share your information and report the player.",
            "Set up two-factor authentication with my email provider.",
            "No, you should never allow anyone to level your account.",
            "No, you should never allow anyone to use your account.",
            "Authenticator and two-step login on my registered email.",
            "No way! You'll just take my gold for your own! Reported!",
            "Don't type in my password backwards and report the player.",
            "Don't give them the information and send an 'Abuse report'.",
            "Don't tell them anything and click the 'Report Abuse' button.",
            "Politely tell them no and then use the 'Report Abuse' button.",
            "Politely tell them no, then use the 'Report Abuse' button.",
            "Don't give out your password to anyone. Not even close friends.",
            "Do not visit the website and report the player who messaged you.",
            "Report the stream as a scam. Real Jagex streams have a 'verified' mark.",
            "Two-factor authentication on your account and your registered email.",
            "Nope, you're tricking me into going somewhere dangerous.",
            "It's never used on other websites or accounts."
    };

    private final CombatTrainingController trainingController = new CombatTrainingController();

    private volatile FleshCrawlerState state = FleshCrawlerState.INITIALIZING;
    private volatile String lastAction = "Starting";
    private volatile int kills;
    private volatile int itemsLooted;
    private volatile int bonesBuried;
    private volatile int foodEaten;

    private volatile WorldPoint fightAnchor;

    private ExecutorService navigationExecutor;
    private volatile Future<?> navigationFuture;
    private volatile WorldPoint navigationTarget;
    private volatile int trackedNpcIndex = -1;
    private volatile boolean trackedNpcCounted;

    public boolean run(KspFleshCrawlerConfig config) {
        Microbot.enableAutoRunOn = true;
        resetSession();
        ensureNavigationExecutor();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }

                updateKillTracking();

                if (handleStrongholdDialogue()) {
                    return;
                }

                if (config.autoRetaliate()) {
                    Rs2Combat.setAutoRetaliate(true);
                }

                if (config.stopAtGoals() && trainingController.allEnabledGoalsReached(config)) {
                    state = FleshCrawlerState.GOALS_REACHED;
                    lastAction = "All enabled combat goals reached";
                    return;
                }

                if (handleHealing(config)) {
                    return;
                }

                if (handleOutOfFood(config)) {
                    return;
                }

                if (config.usePotions() && handlePotions()) {
                    return;
                }

                if (ensureAtFleshCrawlerRoom(config)) {
                    return;
                }

                trainingController.update(config);

                if (Rs2Combat.inCombat()) {
                    trackCurrentOpponent();
                    state = FleshCrawlerState.FIGHTING;
                    lastAction = "Fighting Flesh Crawler";
                    return;
                }

                if (config.buryBones() && handleBones(config)) {
                    return;
                }

                if (config.lootEnabled() && handleLoot(config)) {
                    return;
                }

                attackNextCrawler(config);
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                state = FleshCrawlerState.WAITING;
                lastAction = "Recovered from script exception";
            }
        }, 0, 350, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        cancelNavigation();
        if (navigationExecutor != null) {
            navigationExecutor.shutdownNow();
            navigationExecutor = null;
        }
        super.shutdown();
        state = FleshCrawlerState.WAITING;
        lastAction = "Stopped";
    }

    private void resetSession() {
        state = FleshCrawlerState.INITIALIZING;
        lastAction = "Waiting for Flesh Crawlers";
        kills = 0;
        itemsLooted = 0;
        bonesBuried = 0;
        foodEaten = 0;
        fightAnchor = DEFAULT_FIGHT_POINT;
        navigationTarget = null;
        navigationFuture = null;
        trackedNpcIndex = -1;
        trackedNpcCounted = false;
    }

    private boolean ensureAtFleshCrawlerRoom(KspFleshCrawlerConfig config) {
        if (!config.autoTravel()) {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            lastAction = "Waiting for player location";
            return true;
        }

        if (isNavigationRunning()) {
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            lastAction = navigationStatus(player);
            return true;
        }

        int arrivalRadius = Math.min(Math.max(config.fightRadius() / 2, 4), 8);
        if (isOnFloor2(player) && player.distanceTo(DEFAULT_FIGHT_POINT) <= arrivalRadius) {
            return false;
        }

        state = FleshCrawlerState.WALKING_TO_FIGHT;
        lastAction = navigationStatus(player);
        startNavigation(DEFAULT_FIGHT_POINT, 4);
        return true;
    }

    private String navigationStatus(WorldPoint player) {
        if (isOnFloor1(player)) {
            return "Crossing Stronghold floor 1";
        }
        if (isOnFloor2(player)) {
            return "Walking through Catacomb of Famine";
        }
        return "Walking to Stronghold of Security";
    }

    private boolean isOnFloor1(WorldPoint point) {
        return isInside(point, FLOOR_1_MIN_X, FLOOR_1_MAX_X, FLOOR_1_MIN_Y, FLOOR_1_MAX_Y);
    }

    private boolean isOnFloor2(WorldPoint point) {
        return isInside(point, FLOOR_2_MIN_X, FLOOR_2_MAX_X, FLOOR_2_MIN_Y, FLOOR_2_MAX_Y);
    }

    private boolean isInside(WorldPoint point, int minX, int maxX, int minY, int maxY) {
        return point != null
                && point.getPlane() == 0
                && point.getX() >= minX && point.getX() <= maxX
                && point.getY() >= minY && point.getY() <= maxY;
    }

    private boolean handleStrongholdDialogue() {
        if (!isNavigationRunning() || !Rs2Dialogue.isInDialogue()) {
            return false;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            for (String answer : STRONGHOLD_CORRECT_ANSWERS) {
                if (Rs2Dialogue.hasDialogueOption(answer, true)) {
                    state = FleshCrawlerState.WALKING_TO_FIGHT;
                    lastAction = "Answering Stronghold security door";
                    if (Rs2Dialogue.clickOption(true, answer)) {
                        sleep(300, 500);
                    }
                    return true;
                }
            }
            return false;
        }

        if (Rs2Dialogue.hasContinue()) {
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            lastAction = "Continuing Stronghold dialogue";
            Rs2Dialogue.clickContinue();
            sleep(250, 450);
            return true;
        }

        return false;
    }

    private synchronized void ensureNavigationExecutor() {
        if (navigationExecutor != null && !navigationExecutor.isShutdown()) {
            return;
        }
        navigationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ksp-flesh-crawler-navigation");
            thread.setDaemon(true);
            return thread;
        });
    }

    private synchronized void startNavigation(WorldPoint target, int distance) {
        if (target == null || isNavigationRunning()) {
            return;
        }

        ensureNavigationExecutor();
        navigationTarget = target;
        navigationFuture = navigationExecutor.submit(() -> {
            try {
                boolean arrived = Rs2Walker.walkTo(target, distance);
                if (arrived) {
                    lastAction = "Arrived at Flesh Crawler room";
                } else if (!Thread.currentThread().isInterrupted()) {
                    lastAction = "Walker stopped before destination - retrying";
                }
            } catch (Exception ex) {
                Microbot.logStackTrace("KspFleshCrawlerNavigation", ex);
                lastAction = "Navigation error - retrying";
            } finally {
                navigationTarget = null;
            }
        });
    }

    private boolean isNavigationRunning() {
        Future<?> future = navigationFuture;
        return future != null && !future.isDone() && !future.isCancelled();
    }

    private synchronized void cancelNavigation() {
        Future<?> future = navigationFuture;
        boolean ownedActiveWalk = (future != null && !future.isDone()) || navigationTarget != null;
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        navigationFuture = null;
        navigationTarget = null;
        if (ownedActiveWalk) {
            Rs2Walker.setTarget(null);
        }
    }

    private boolean handleHealing(KspFleshCrawlerConfig config) {
        if (!config.useHealing()) {
            return false;
        }

        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int foodHeal = resolveFoodHeal(config.foodName(), config.unknownFoodHeal());
        int missingHp = Math.max(0, maxHp - currentHp);
        int emergencyHp = Math.max(3, maxHp / 3);

        if (missingHp < foodHeal && currentHp > emergencyHp) {
            return false;
        }

        if (!Rs2Inventory.contains(config.foodName(), false)) {
            return false;
        }

        state = FleshCrawlerState.HEALING;
        lastAction = "Eating " + config.foodName();
        if (Rs2Inventory.interact(config.foodName(), "Eat", false)) {
            foodEaten++;
            sleep(450, 700);
        }
        return true;
    }

    private boolean handleOutOfFood(KspFleshCrawlerConfig config) {
        if (!config.useHealing() || Rs2Inventory.contains(config.foodName(), false)) {
            return false;
        }

        if (!config.bankForFood()) {
            int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            if (currentHp <= Math.max(3, maxHp / 2)) {
                state = FleshCrawlerState.OUT_OF_FOOD;
                lastAction = "Out of food - combat paused";
                return true;
            }
            return false;
        }

        if (fightAnchor == null) {
            state = FleshCrawlerState.OUT_OF_FOOD;
            lastAction = "Cannot bank: fight anchor not captured";
            return true;
        }

        if (isNavigationRunning()) {
            cancelNavigation();
        }

        if (!Rs2Bank.isNearBank(10)) {
            state = FleshCrawlerState.WALKING_TO_BANK;
            lastAction = "Walking to bank for food";
            Rs2Bank.walkToBank();
            return true;
        }

        state = FleshCrawlerState.BANKING;
        lastAction = "Restocking " + config.foodName();
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) {
            return true;
        }

        List<String> keep = new ArrayList<>();
        keep.add(config.foodName());
        keep.addAll(defaultPotionBaseNames());
        Rs2Bank.depositAllExcept(keep);

        int currentFood = Rs2Inventory.count(config.foodName(), false);
        int needed = Math.max(0, config.foodAmount() - currentFood);
        if (needed > 0) {
            Rs2Bank.withdrawX(config.foodName(), needed, false);
            sleepUntil(() -> Rs2Inventory.count(config.foodName(), false) >= config.foodAmount(), 3_000);
        }

        if (!Rs2Inventory.contains(config.foodName(), false)) {
            state = FleshCrawlerState.OUT_OF_FOOD;
            lastAction = "Configured food not available in bank";
            return true;
        }

        Rs2Bank.closeBank();
        state = FleshCrawlerState.RETURNING_TO_FIGHT;
        lastAction = "Returning to Flesh Crawlers";
        startNavigation(fightAnchor, 4);
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
        if (Microbot.getClient().getBoostedSkillLevel(skill) > Microbot.getClient().getRealSkillLevel(skill)) {
            return false;
        }
        if (!Rs2Inventory.contains(potionBaseName, false)) {
            return false;
        }

        lastAction = "Drinking " + potionBaseName;
        if (Rs2Inventory.interact(potionBaseName, "Drink", false)) {
            sleep(450, 700);
            return true;
        }
        return false;
    }

    private boolean handleBones(KspFleshCrawlerConfig config) {
        List<String> boneNames = parseCsv(config.boneItems());
        for (String boneName : boneNames) {
            if (Rs2Inventory.contains(boneName, true)) {
                state = FleshCrawlerState.BURYING;
                lastAction = "Burying " + boneName;
                if (Rs2Inventory.interact(boneName, "Bury", true)) {
                    bonesBuried++;
                    sleep(350, 550);
                }
                return true;
            }
        }

        if (Rs2Inventory.isFull()) {
            return false;
        }

        for (String boneName : boneNames) {
            if (Rs2GroundItem.loot(boneName, config.lootRadius())) {
                state = FleshCrawlerState.BURYING;
                lastAction = "Picking up " + boneName;
                sleep(450, 700);
                return true;
            }
        }
        return false;
    }

    private boolean handleLoot(KspFleshCrawlerConfig config) {
        List<String> lootNames = parseCsv(config.lootItems());
        if (lootNames.isEmpty()) {
            return false;
        }

        for (String lootName : lootNames) {
            if (Rs2Inventory.isFull(lootName)) {
                continue;
            }
            if (Rs2GroundItem.loot(lootName, config.lootRadius())) {
                state = FleshCrawlerState.LOOTING;
                lastAction = "Looting " + lootName;
                itemsLooted++;
                sleep(450, 750);
                return true;
            }
        }
        return false;
    }

    private void attackNextCrawler(KspFleshCrawlerConfig config) {
        if (fightAnchor == null) {
            fightAnchor = DEFAULT_FIGHT_POINT;
        }

        Rs2NpcModel target = Rs2Npc.getNpcs(npc -> {
                    String name = npc.getName();
                    if (name == null || !NPC_NAME.equalsIgnoreCase(name) || npc.isDead()) {
                        return false;
                    }
                    if (!npc.isWithinDistance(fightAnchor, config.fightRadius())) {
                        return false;
                    }
                    Actor interacting = npc.getInteracting();
                    return interacting == null || interacting == Microbot.getClient().getLocalPlayer();
                })
                .findFirst()
                .orElse(null);

        if (target == null) {
            state = FleshCrawlerState.WAITING;
            lastAction = "Waiting for reachable Flesh Crawler";
            return;
        }

        if (Rs2Npc.attack(target)) {
            trackedNpcIndex = target.getIndex();
            trackedNpcCounted = false;
            state = FleshCrawlerState.FIGHTING;
            lastAction = "Attacking Flesh Crawler (level " + target.getCombatLevel() + ")";
        }
    }

    private void trackCurrentOpponent() {
        Actor interacting = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getInteracting();
        }).orElse(null);

        if (interacting instanceof NPC) {
            NPC npc = (NPC) interacting;
            if (npc.getName() != null && NPC_NAME.equalsIgnoreCase(npc.getName())) {
                if (trackedNpcIndex != npc.getIndex()) {
                    trackedNpcIndex = npc.getIndex();
                    trackedNpcCounted = false;
                }
            }
        }
    }

    private void updateKillTracking() {
        if (trackedNpcIndex < 0 || trackedNpcCounted) {
            return;
        }

        Rs2NpcModel tracked = Rs2Npc.getNpcByIndex(trackedNpcIndex);
        if (tracked != null && tracked.isDead()) {
            kills++;
            trackedNpcCounted = true;
            lastAction = "Flesh Crawler defeated";
        }
    }

    private int resolveFoodHeal(String foodName, int fallback) {
        if (foodName != null) {
            for (Rs2Food food : Rs2Food.values()) {
                if (food.getName().equalsIgnoreCase(foodName.trim())) {
                    return Math.max(1, food.getHeal());
                }
            }
        }
        return Math.max(1, fallback);
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> defaultPotionBaseNames() {
        return Arrays.asList(
                "Super combat potion",
                "Combat potion",
                "Attack potion",
                "Super attack",
                "Strength potion",
                "Super strength",
                "Defence potion",
                "Super defence"
        );
    }

    public FleshCrawlerState getState() {
        return state;
    }

    public String getLastAction() {
        return lastAction;
    }

    public int getKills() {
        return kills;
    }

    public int getItemsLooted() {
        return itemsLooted;
    }

    public int getBonesBuried() {
        return bonesBuried;
    }

    public int getFoodEaten() {
        return foodEaten;
    }

    public WorldPoint getFightAnchor() {
        return fightAnchor;
    }

    public Skill getCurrentTrainingSkill() {
        return trainingController.getCurrentTrainingSkill();
    }
}
