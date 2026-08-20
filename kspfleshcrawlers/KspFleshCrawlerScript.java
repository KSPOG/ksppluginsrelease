package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings({"deprecation", "removal"})
public class KspFleshCrawlerScript extends Script {
    private static final String NPC_NAME = "Flesh Crawler";

    private final CombatTrainingController trainingController = new CombatTrainingController();

    private volatile FleshCrawlerState state = FleshCrawlerState.INITIALIZING;
    private volatile String lastAction = "Starting";
    private volatile int kills;
    private volatile int itemsLooted;
    private volatile int bonesBuried;
    private volatile int foodEaten;

    private volatile WorldPoint fightAnchor;
    private volatile int trackedNpcIndex = -1;
    private volatile boolean trackedNpcCounted;

    public boolean run(KspFleshCrawlerConfig config) {
        Microbot.enableAutoRunOn = true;
        resetSession();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }

                captureFightAnchor();
                updateKillTracking();

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
        fightAnchor = null;
        trackedNpcIndex = -1;
        trackedNpcCounted = false;
    }

    private void captureFightAnchor() {
        if (fightAnchor != null) {
            return;
        }

        Rs2NpcModel nearby = Rs2Npc.getNpcs(NPC_NAME, true).findFirst().orElse(null);
        if (nearby != null) {
            fightAnchor = Rs2Player.getWorldLocation();
            lastAction = "Fight anchor captured";
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
        Rs2Walker.walkTo(fightAnchor);
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
            state = FleshCrawlerState.WAITING;
            lastAction = "Start near Flesh Crawlers on floor 2";
            return;
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
