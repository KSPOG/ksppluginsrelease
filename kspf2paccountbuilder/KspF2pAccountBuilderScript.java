package net.runelite.client.plugins.microbot.kspf2paccountbuilder;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaiofighter.KspAioFighterPlugin;
import net.runelite.client.plugins.microbot.kspdirectfishing.KspDirectFishingPlugin;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.mining.AutoMiningPlugin;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KspF2pAccountBuilderScript extends Script
{
    private static final WorldPoint LUMBRIDGE_CHICKENS = new WorldPoint(3231, 3297, 0);

    private static final List<String> SELL_OUTPUTS = Arrays.asList(
            "Logs",
            "Oak logs",
            "Willow logs",
            "Yew logs",
            "Copper ore",
            "Tin ore",
            "Iron ore",
            "Coal",
            "Mithril ore",
            "Adamantite ore",
            "Runite ore",
            "Raw shrimps",
            "Shrimps",
            "Raw anchovies",
            "Anchovies",
            "Raw sardine",
            "Sardine",
            "Raw herring",
            "Herring",
            "Raw trout",
            "Trout",
            "Raw salmon",
            "Salmon",
            "Raw lobster",
            "Lobster"
    );

    private static final ToolTier[] AXES = {
            new ToolTier("Rune axe", 41),
            new ToolTier("Adamant axe", 31),
            new ToolTier("Mithril axe", 21),
            new ToolTier("Black axe", 11),
            new ToolTier("Steel axe", 6),
            new ToolTier("Iron axe", 1),
            new ToolTier("Bronze axe", 1)
    };

    private static final ToolTier[] PICKAXES = {
            new ToolTier("Rune pickaxe", 41),
            new ToolTier("Adamant pickaxe", 31),
            new ToolTier("Mithril pickaxe", 21),
            new ToolTier("Black pickaxe", 11),
            new ToolTier("Steel pickaxe", 6),
            new ToolTier("Iron pickaxe", 1),
            new ToolTier("Bronze pickaxe", 1)
    };

    private static final ToolTier[] SCIMITARS = {
            new ToolTier("Rune scimitar", 40),
            new ToolTier("Adamant scimitar", 30),
            new ToolTier("Mithril scimitar", 20),
            new ToolTier("Black scimitar", 10),
            new ToolTier("Steel scimitar", 5),
            new ToolTier("Iron scimitar", 1),
            new ToolTier("Bronze scimitar", 1)
    };

    private enum TrainingModule
    {
        NONE,
        WOODCUTTING,
        MINING,
        FISHING,
        COMBAT
    }

    @Inject
    private ConfigManager configManager;

    private KspF2pAccountBuilderConfig config;
    private volatile F2pBuilderPhase phase = F2pBuilderPhase.STARTING;
    private volatile String status = "Starting";
    private volatile String activeModule = "-";
    private volatile String currentQuest = "-";
    private volatile String nextUpgrade = "-";
    private volatile F2pTradeRestrictionTracker.Snapshot restriction =
            new F2pTradeRestrictionTracker.Snapshot(0, 0, 0);

    private volatile boolean halted;
    private volatile boolean economyRequested;
    private TrainingModule trainingModule = TrainingModule.NONE;
    private String trainingVariant = "";
    private Quest activeQuest;
    private long lastEconomyAt;
    private long lastQuestStartAttempt;
    private final Map<String, Long> sellRetryAfter = new HashMap<>();

    public boolean run(KspF2pAccountBuilderConfig config)
    {
        this.config = config;
        this.phase = F2pBuilderPhase.STARTING;
        this.status = "Reading account progress";
        this.activeModule = "-";
        this.currentQuest = "-";
        this.nextUpgrade = "-";
        this.halted = false;
        this.economyRequested = false;
        this.trainingModule = TrainingModule.NONE;
        this.trainingVariant = "";
        this.activeQuest = null;
        this.lastEconomyAt = 0L;
        this.lastQuestStartAttempt = 0L;
        this.sellRetryAfter.clear();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::loop, 0, 1200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop()
    {
        try
        {
            if (!super.run() || !Microbot.isLoggedIn() || halted)
            {
                return;
            }

            restriction = F2pTradeRestrictionTracker.snapshot();

            if (config.stopIfMembersWorld() && Rs2Player.isInMemberWorld())
            {
                stopTrainingModules();
                phase = F2pBuilderPhase.PAUSED;
                status = "F2P world required";
                return;
            }

            if (goalComplete())
            {
                stopTrainingModules();
                phase = F2pBuilderPhase.GOAL_COMPLETE;
                status = goalCompleteStatus();
                if (config.stopAtGoal()
                        && config.goalProfile() != KspF2pAccountBuilderConfig.GoalProfile.CONTINUOUS)
                {
                    halted = true;
                }
                return;
            }

            if (activeQuest != null)
            {
                handleActiveQuest();
                return;
            }

            if (economyRequested || shouldRunEconomy())
            {
                if (runEconomyPass())
                {
                    economyRequested = false;
                    lastEconomyAt = System.currentTimeMillis();
                }
                return;
            }

            if (needsSeedMoney())
            {
                if (hasSellableOutput())
                {
                    economyRequested = true;
                    phase = F2pBuilderPhase.SEED_MONEY;
                    status = "Liquidating early unrestricted output";
                    return;
                }

                if (config.autoSkill())
                {
                    phase = F2pBuilderPhase.SEED_MONEY;
                    status = "Building starter cash with normal logs";
                    ensureTraining(TrainingModule.WOODCUTTING);
                    return;
                }
            }

            Quest nextQuest = config.autoQuest()
                    ? F2pQuestPlanner.nextQuest(config.goalProfile(), restriction, config.skipPartnerQuests())
                    : null;

            if (nextQuest != null && canFundQuesting())
            {
                if (startQuest(nextQuest))
                {
                    return;
                }
            }

            if (config.autoSkill())
            {
                TrainingModule desired = chooseTrainingModule();
                if (desired != TrainingModule.NONE)
                {
                    phase = restriction.hasTotalLevel()
                            ? F2pBuilderPhase.WAITING_FOR_TRADE_UNLOCK
                            : F2pBuilderPhase.SKILLING;
                    ensureTraining(desired);
                    return;
                }
            }

            stopTrainingModules();
            phase = F2pBuilderPhase.PAUSED;
            status = nextQuest != null
                    ? "Waiting for quest cash/prerequisites"
                    : "No eligible progression action";
        }
        catch (Throwable ex)
        {
            phase = F2pBuilderPhase.ERROR;
            status = "Recovery: " + ex.getClass().getSimpleName();
            Microbot.log("KSP F2P Account Builder: " + ex.getMessage());
            economyRequested = true;
        }
    }

    private boolean goalComplete()
    {
        switch (config.goalProfile())
        {
            case TRADE_UNLOCK:
                return restriction.isTradeUnlocked();
            case ALL_SOLO_F2P_QUESTS:
                return restriction.isTradeUnlocked()
                        && F2pQuestPlanner.allConfiguredSoloQuestsFinished(config.skipPartnerQuests());
            case CONTINUOUS:
            default:
                return false;
        }
    }

    private String goalCompleteStatus()
    {
        if (config.goalProfile() == KspF2pAccountBuilderConfig.GoalProfile.TRADE_UNLOCK)
        {
            return "Trade restriction removed";
        }
        return "Configured F2P build complete";
    }

    private boolean needsSeedMoney()
    {
        return config.autoQuest()
                && restriction.getQuestPoints() < F2pTradeRestrictionTracker.REQUIRED_QUEST_POINTS
                && liquidCoins() < config.minimumQuestCash();
    }

    private boolean canFundQuesting()
    {
        return liquidCoins() >= config.minimumQuestCash()
                || restriction.isTradeUnlocked()
                || config.minimumQuestCash() == 0;
    }

    private boolean shouldRunEconomy()
    {
        if (!config.autoSellOutputs() && !config.autoBuyUpgrades() && !config.autoRestock())
        {
            return false;
        }

        long interval = Math.max(1, config.economyIntervalMinutes()) * 60_000L;
        return System.currentTimeMillis() - lastEconomyAt >= interval;
    }

    private boolean runEconomyPass()
    {
        stopTrainingModules();
        phase = F2pBuilderPhase.ECONOMY;
        status = "Economy pass";

        if (config.autoSellOutputs() && hasSellableOutput())
        {
            String output = firstSellableOutput();
            if (output != null)
            {
                sellBankedOutput(output);
                return true;
            }
        }

        if (config.autoRestock() && restockOneMissingInput())
        {
            return true;
        }

        if (config.autoBuyUpgrades() && buyOneUpgrade())
        {
            return true;
        }

        nextUpgrade = describeNextUpgrade();
        status = "Economy ready";
        return true;
    }

    private boolean hasSellableOutput()
    {
        return firstSellableOutput() != null;
    }

    private String firstSellableOutput()
    {
        long now = System.currentTimeMillis();
        for (String item : SELL_OUTPUTS)
        {
            Long retryAt = sellRetryAfter.get(item);
            if (retryAt != null && retryAt > now)
            {
                continue;
            }

            if (F2pTradeRestrictionTracker.isRestrictedItem(item) && !restriction.isTradeUnlocked())
            {
                continue;
            }

            if (!restriction.isTradeUnlocked() && !config.sellBeforeTradeUnlock())
            {
                continue;
            }

            if (Rs2Bank.count(item, true) > 0 || Rs2Inventory.itemQuantity(item) > 0)
            {
                return item;
            }
        }

        return null;
    }

    private void sellBankedOutput(String itemName)
    {
        phase = F2pBuilderPhase.ECONOMY;
        status = "Selling " + itemName;

        int itemId = Rs2ItemManager.getItemIdByName(itemName, false);
        if (itemId <= 0)
        {
            quarantineSale(itemName);
            return;
        }

        if (Rs2GrandExchange.hasSellOffer(itemId) != null)
        {
            Rs2GrandExchange.collectAllToBank();
            status = "Existing " + itemName + " offer pending";
            return;
        }

        if (!Rs2Bank.walkToBankAndUseBank())
        {
            status = "Walking to bank for sale";
            return;
        }

        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 2500);

        int quantity = Rs2Bank.count(itemName, true);
        if (quantity <= 0)
        {
            Rs2Bank.closeBank();
            return;
        }

        Rs2Bank.setWithdrawAsNote();
        if (!Rs2Bank.withdrawAll(itemName, true))
        {
            Rs2Bank.setWithdrawAsItem();
            Rs2Bank.closeBank();
            quarantineSale(itemName);
            return;
        }

        sleepUntil(() -> Rs2Inventory.itemQuantity(itemName) > 0, 2500);
        quantity = Rs2Inventory.itemQuantity(itemName);
        Rs2Bank.setWithdrawAsItem();
        Rs2Bank.closeBank();

        if (quantity <= 0)
        {
            quarantineSale(itemName);
            return;
        }

        int market = Rs2GrandExchange.getSellPrice(itemId);
        if (market <= 0)
        {
            market = Rs2GrandExchange.getPrice(itemId);
        }
        int price = Math.max(1, market > 0 ? (int) Math.floor(market * 0.92) : 1);

        if (!Rs2GrandExchange.sellItem(itemName, quantity, price))
        {
            quarantineSale(itemName);
            status = "Sell failed; retry delayed: " + itemName;
            return;
        }

        sleepUntil(Rs2GrandExchange::hasFinishedSellingOffers,
                Math.max(5, config.geWaitSeconds()) * 1000L);
        Rs2GrandExchange.collectAllToBank();
        status = "Sold/placed: " + itemName;
    }

    private void quarantineSale(String itemName)
    {
        sellRetryAfter.put(itemName, System.currentTimeMillis() + 30L * 60_000L);
    }

    private boolean restockOneMissingInput()
    {
        int fishing = Rs2Player.getRealSkillLevel(Skill.FISHING);

        if (!ownsAnywhere("Small fishing net"))
        {
            phase = F2pBuilderPhase.RESTOCKING;
            status = "Buying Small fishing net";
            return buyToBank("Small fishing net", 1, false);
        }

        if (fishing >= 5 && !ownsAnywhere("Fishing rod"))
        {
            phase = F2pBuilderPhase.RESTOCKING;
            status = "Buying Fishing rod";
            return buyToBank("Fishing rod", 1, false);
        }

        if (fishing >= 5 && totalOwned("Fishing bait") < 250)
        {
            phase = F2pBuilderPhase.RESTOCKING;
            status = "Restocking Fishing bait";
            return buyToBank("Fishing bait", 1000, false);
        }

        return false;
    }

    private boolean buyOneUpgrade()
    {
        ToolTier axe = bestTier(AXES, Rs2Player.getRealSkillLevel(Skill.WOODCUTTING));
        if (axe != null && !ownsAnywhere(axe.name))
        {
            nextUpgrade = axe.name;
            phase = F2pBuilderPhase.UPGRADING;
            status = "Buying " + axe.name;
            if (buyToBank(axe.name, 1, true))
            {
                return true;
            }
        }

        ToolTier pickaxe = bestTier(PICKAXES, Rs2Player.getRealSkillLevel(Skill.MINING));
        if (pickaxe != null && !ownsAnywhere(pickaxe.name))
        {
            nextUpgrade = pickaxe.name;
            phase = F2pBuilderPhase.UPGRADING;
            status = "Buying " + pickaxe.name;
            if (buyToBank(pickaxe.name, 1, true))
            {
                return true;
            }
        }

        if (config.goalProfile() != KspF2pAccountBuilderConfig.GoalProfile.TRADE_UNLOCK)
        {
            ToolTier scimitar = bestTier(SCIMITARS, Rs2Player.getRealSkillLevel(Skill.ATTACK));
            if (scimitar != null && !ownsAnywhere(scimitar.name))
            {
                nextUpgrade = scimitar.name;
                phase = F2pBuilderPhase.UPGRADING;
                status = "Buying " + scimitar.name;
                return buyToBank(scimitar.name, 1, true);
            }
        }

        return false;
    }

    private boolean buyToBank(String itemName, int quantity, boolean optionalUpgrade)
    {
        int itemId = Rs2ItemManager.getItemIdByName(itemName, false);
        if (itemId <= 0 || quantity <= 0)
        {
            return false;
        }

        if (Rs2GrandExchange.hasBuyOffer(itemId) != null)
        {
            Rs2GrandExchange.collectAllToBank();
            return true;
        }

        int market = Rs2GrandExchange.getSellPrice(itemId);
        if (market <= 0)
        {
            market = Rs2GrandExchange.getPrice(itemId);
        }
        if (market <= 0)
        {
            return false;
        }

        int unitPrice = Math.max(1, (int) Math.ceil(market * 1.12));
        long requiredLong = (long) unitPrice * quantity;
        int required = requiredLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) requiredLong;
        int available = liquidCoins();

        if (optionalUpgrade && available - required < config.cashReserve())
        {
            status = "Saving GP for " + itemName;
            return false;
        }

        if (available < required)
        {
            status = "Need GP for " + itemName;
            return false;
        }

        if (!ensureCoinsInInventory(required))
        {
            return false;
        }

        if (!Rs2GrandExchange.buyItem(itemName, unitPrice, quantity))
        {
            status = "Buy failed: " + itemName;
            return false;
        }

        sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers,
                Math.max(5, config.geWaitSeconds()) * 1000L);
        Rs2GrandExchange.collectAllToBank();
        return true;
    }

    private boolean ensureCoinsInInventory(int required)
    {
        int inventoryCoins = Rs2Inventory.itemQuantity("Coins");
        if (inventoryCoins >= required)
        {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank())
        {
            status = "Walking to bank for coins";
            return false;
        }

        int missing = Math.max(0, required - Rs2Inventory.itemQuantity("Coins"));
        if (missing > 0)
        {
            Rs2Bank.withdrawX("Coins", missing, true);
            sleepUntil(() -> Rs2Inventory.itemQuantity("Coins") >= required, 2500);
        }
        Rs2Bank.closeBank();
        return Rs2Inventory.itemQuantity("Coins") >= required;
    }

    private int liquidCoins()
    {
        return Math.max(0, Rs2Inventory.itemQuantity("Coins"))
                + Math.max(0, Rs2Bank.count("Coins", true));
    }

    private int totalOwned(String itemName)
    {
        int total = Rs2Inventory.itemQuantity(itemName) + Rs2Bank.count(itemName, true);
        return Math.max(0, total);
    }

    private boolean ownsAnywhere(String itemName)
    {
        return Rs2Inventory.hasItem(itemName, true)
                || Rs2Equipment.isWearing(itemName)
                || Rs2Bank.hasBankItem(itemName, true);
    }

    private void handleActiveQuest()
    {
        stopTrainingModules();

        if (F2pQuestPlanner.isFinished(activeQuest))
        {
            status = "Completed: " + activeQuest.getName();
            currentQuest = "-";
            activeQuest = null;
            lastEconomyAt = 0L;
            return;
        }

        phase = F2pBuilderPhase.QUESTING;
        currentQuest = activeQuest.getName();
        status = "Questing: " + currentQuest;

        QuestHelperPlugin helper = Microbot.getPlugin(QuestHelperPlugin.class);
        if (helper == null || !Microbot.isPluginEnabled(QuestHelperPlugin.class))
        {
            ensureQuestHelper();
            return;
        }

        if (helper.getSelectedQuest() == null
                && System.currentTimeMillis() - lastQuestStartAttempt > 5000L)
        {
            lastQuestStartAttempt = System.currentTimeMillis();
            helper.startQuestHelper(activeQuest.getName());
        }
    }

    private boolean startQuest(Quest quest)
    {
        stopTrainingModules();
        phase = F2pBuilderPhase.QUESTING;
        status = "Preparing quest: " + quest.getName();

        if (!ensureQuestHelper())
        {
            return false;
        }

        QuestHelperPlugin helper = Microbot.getPlugin(QuestHelperPlugin.class);
        if (helper == null)
        {
            return false;
        }

        configManager.setConfiguration("questhelper", "TurnOn", true);
        configManager.setConfiguration("questhelper", "obtainMissingItems", "YES");

        if (helper.getSelectedQuest() != null)
        {
            status = "Waiting for Quest Helper";
            return false;
        }

        lastQuestStartAttempt = System.currentTimeMillis();
        if (!helper.startQuestHelper(quest.getName()))
        {
            status = "Quest Helper unavailable: " + quest.getName();
            return false;
        }

        activeQuest = quest;
        currentQuest = quest.getName();
        status = "Questing: " + currentQuest;
        return true;
    }

    private boolean ensureQuestHelper()
    {
        configManager.setConfiguration("questhelper", "TurnOn", true);
        configManager.setConfiguration("questhelper", "obtainMissingItems", "YES");

        QuestHelperPlugin helper = Microbot.getPlugin(QuestHelperPlugin.class);
        if (helper == null)
        {
            status = "Quest Helper not available";
            return false;
        }

        if (!Microbot.isPluginEnabled(QuestHelperPlugin.class))
        {
            Microbot.startPlugin(QuestHelperPlugin.class);
            status = "Starting Quest Helper";
            return false;
        }

        return true;
    }

    private TrainingModule chooseTrainingModule()
    {
        if (config.goalProfile() != KspF2pAccountBuilderConfig.GoalProfile.TRADE_UNLOCK)
        {
            if (!F2pQuestPlanner.isFinished(Quest.THE_KNIGHTS_SWORD)
                    && Rs2Player.getRealSkillLevel(Skill.MINING) < 10)
            {
                return TrainingModule.MINING;
            }

            if (restriction.getQuestPoints() >= 32
                    && !F2pQuestPlanner.isFinished(Quest.DRAGON_SLAYER_I)
                    && !dragonSlayerCombatReady())
            {
                return TrainingModule.COMBAT;
            }
        }

        if (!restriction.hasTotalLevel())
        {
            int woodcutting = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            int mining = Rs2Player.getRealSkillLevel(Skill.MINING);
            int fishing = Rs2Player.getRealSkillLevel(Skill.FISHING);

            if (woodcutting <= mining && woodcutting <= fishing)
            {
                return TrainingModule.WOODCUTTING;
            }
            if (mining <= fishing)
            {
                return TrainingModule.MINING;
            }
            return TrainingModule.FISHING;
        }

        int rotation = (restriction.getPlaytimeMinutes() / 30) % 3;
        if (rotation == 0)
        {
            return TrainingModule.WOODCUTTING;
        }
        if (rotation == 1)
        {
            return TrainingModule.MINING;
        }
        return TrainingModule.FISHING;
    }

    private boolean dragonSlayerCombatReady()
    {
        return Rs2Player.getRealSkillLevel(Skill.ATTACK) >= 30
                && Rs2Player.getRealSkillLevel(Skill.STRENGTH) >= 30
                && Rs2Player.getRealSkillLevel(Skill.DEFENCE) >= 30;
    }

    private void ensureTraining(TrainingModule desired)
    {
        String desiredVariant = trainingVariant(desired);
        if (trainingModule == desired
                && desiredVariant.equals(trainingVariant)
                && isModuleEnabled(desired))
        {
            activeModule = desired.name();
            status = trainingStatus(desired);
            return;
        }

        stopTrainingModules();

        if (!prepareTrainingLoadout(desired))
        {
            economyRequested = true;
            status = "Preparing " + desired.name().toLowerCase() + " inputs";
            return;
        }

        configureTrainingModule(desired);
        startModule(desired);
        trainingModule = desired;
        trainingVariant = desiredVariant;
        activeModule = desired.name();
        status = trainingStatus(desired);
    }

    private String trainingVariant(TrainingModule module)
    {
        switch (module)
        {
            case WOODCUTTING:
                int wc = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                if (wc >= 60) return "YEW";
                if (wc >= 30) return "WILLOW";
                if (wc >= 15) return "OAK";
                return "TREE";
            case FISHING:
                return Rs2Player.getRealSkillLevel(Skill.FISHING) >= 5
                        ? "SARDINE_HERRING"
                        : "SHRIMP_ANCHOVIES";
            case COMBAT:
                return "CHICKEN_30";
            case MINING:
                return "PROGRESSIVE";
            default:
                return "";
        }
    }

    private String trainingStatus(TrainingModule module)
    {
        if (module == TrainingModule.WOODCUTTING)
        {
            return "Training Woodcutting: " + trainingVariant(module);
        }
        if (module == TrainingModule.FISHING)
        {
            return "Training Fishing: " + trainingVariant(module);
        }
        if (module == TrainingModule.COMBAT)
        {
            return "Training melee for Dragon Slayer";
        }
        return "Progressive Mining";
    }

    private boolean prepareTrainingLoadout(TrainingModule module)
    {
        switch (module)
        {
            case WOODCUTTING:
                return ensureToolCarried(AXES, Rs2Player.getRealSkillLevel(Skill.WOODCUTTING));
            case MINING:
                return ensureToolCarried(PICKAXES, Rs2Player.getRealSkillLevel(Skill.MINING));
            case FISHING:
                return ensureFishingLoadout();
            case COMBAT:
                return ensureCombatLoadout();
            default:
                return true;
        }
    }

    private boolean ensureToolCarried(ToolTier[] tiers, int level)
    {
        ToolTier bestOwned = bestOwnedTier(tiers, level);
        if (bestOwned != null
                && (Rs2Inventory.hasItem(bestOwned.name, true) || Rs2Equipment.isWearing(bestOwned.name)))
        {
            return true;
        }

        if (bestOwned == null)
        {
            economyRequested = true;
            return false;
        }

        if (!Rs2Bank.walkToBankAndUseBank())
        {
            return false;
        }

        boolean withdrawn = Rs2Bank.withdrawOne(bestOwned.name, true);
        Rs2Bank.closeBank();
        return withdrawn && sleepUntil(() -> Rs2Inventory.hasItem(bestOwned.name, true), 2500);
    }

    private boolean ensureFishingLoadout()
    {
        boolean baitMode = Rs2Player.getRealSkillLevel(Skill.FISHING) >= 5;
        String tool = baitMode ? "Fishing rod" : "Small fishing net";

        if (!ownsAnywhere(tool) || (baitMode && totalOwned("Fishing bait") <= 0))
        {
            economyRequested = true;
            return false;
        }

        if (Rs2Inventory.hasItem(tool, true)
                && (!baitMode || Rs2Inventory.itemQuantity("Fishing bait") > 0))
        {
            return true;
        }

        if (!Rs2Bank.walkToBankAndUseBank())
        {
            return false;
        }

        if (!Rs2Inventory.hasItem(tool, true))
        {
            Rs2Bank.withdrawOne(tool, true);
        }

        if (baitMode && Rs2Inventory.itemQuantity("Fishing bait") < 100)
        {
            int available = Rs2Bank.count("Fishing bait", true);
            int amount = Math.min(Math.max(0, available), 1000);
            if (amount > 0)
            {
                Rs2Bank.withdrawX("Fishing bait", amount, true);
            }
        }

        Rs2Bank.closeBank();
        return Rs2Inventory.hasItem(tool, true)
                && (!baitMode || Rs2Inventory.itemQuantity("Fishing bait") > 0);
    }

    private boolean ensureCombatLoadout()
    {
        ToolTier weapon = bestOwnedTier(SCIMITARS, Rs2Player.getRealSkillLevel(Skill.ATTACK));
        if (weapon == null)
        {
            economyRequested = true;
            return false;
        }

        if (!Rs2Equipment.isWearing(weapon.name))
        {
            if (!Rs2Bank.walkToBankAndUseBank())
            {
                return false;
            }

            if (Rs2Inventory.hasItem(weapon.name, true))
            {
                Rs2Bank.wearItem(weapon.name, true);
            }
            else
            {
                Rs2Bank.withdrawAndEquip(weapon.name);
            }

            Rs2Bank.closeBank();
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || here.distanceTo(LUMBRIDGE_CHICKENS) > 14)
        {
            status = "Walking to Lumbridge chickens";
            Rs2Walker.walkTo(LUMBRIDGE_CHICKENS, 5);
            return false;
        }

        return Rs2Equipment.isWearing(weapon.name) || Rs2Inventory.hasItem(weapon.name, true);
    }

    private void configureTrainingModule(TrainingModule module)
    {
        switch (module)
        {
            case WOODCUTTING:
                configManager.setConfiguration("KspWillowChopper", "tree", trainingVariant(module));
                configManager.setConfiguration("KspWillowChopper", "bankLogs", true);
                configManager.setConfiguration("KspWillowChopper", "enableForestry", false);
                break;
            case MINING:
                configManager.setConfiguration("Mining", "progressiveMode", true);
                configManager.setConfiguration("Mining", "UseBank", true);
                configManager.setConfiguration("Mining", "maxPlayersInArea", 0);
                break;
            case FISHING:
                configManager.setConfiguration("kspDirectFishing", "fishingMode", trainingVariant(module));
                configManager.setConfiguration("kspDirectFishing", "fishingLocation", "DRAYNOR_VILLAGE");
                configManager.setConfiguration("kspDirectFishing", "dropFish", false);
                configManager.setConfiguration("kspDirectFishing", "waitForFire", false);
                break;
            case COMBAT:
                configManager.setConfiguration("kspaiofighter", "npcNames", "Chicken");
                configManager.setConfiguration("kspaiofighter", "attackRadius", 12);
                configManager.setConfiguration("kspaiofighter", "bankForGear", false);
                configManager.setConfiguration("kspaiofighter", "trainAttack", true);
                configManager.setConfiguration("kspaiofighter", "attackTarget", 30);
                configManager.setConfiguration("kspaiofighter", "trainStrength", true);
                configManager.setConfiguration("kspaiofighter", "strengthTarget", 30);
                configManager.setConfiguration("kspaiofighter", "trainDefence", true);
                configManager.setConfiguration("kspaiofighter", "defenceTarget", 30);
                configManager.setConfiguration("kspaiofighter", "trainRanged", false);
                configManager.setConfiguration("kspaiofighter", "trainMagic", false);
                configManager.setConfiguration("kspaiofighter", "useHealing", false);
                configManager.setConfiguration("kspaiofighter", "usePotions", false);
                configManager.setConfiguration("kspaiofighter", "lootItems", false);
                configManager.setConfiguration("kspaiofighter", "buryBones", false);
                configManager.setConfiguration("kspaiofighter", "useAttackArea", false);
                configManager.setConfiguration("kspaiofighter", "useSafeSpot", false);
                break;
            default:
                break;
        }
    }

    private void startModule(TrainingModule module)
    {
        switch (module)
        {
            case WOODCUTTING:
                Microbot.startPlugin(KspWillowChopperPlugin.class);
                break;
            case MINING:
                Microbot.startPlugin(AutoMiningPlugin.class);
                break;
            case FISHING:
                Microbot.startPlugin(KspDirectFishingPlugin.class);
                break;
            case COMBAT:
                Microbot.startPlugin(KspAioFighterPlugin.class);
                break;
            default:
                break;
        }
    }

    private boolean isModuleEnabled(TrainingModule module)
    {
        switch (module)
        {
            case WOODCUTTING:
                return Microbot.isPluginEnabled(KspWillowChopperPlugin.class);
            case MINING:
                return Microbot.isPluginEnabled(AutoMiningPlugin.class);
            case FISHING:
                return Microbot.isPluginEnabled(KspDirectFishingPlugin.class);
            case COMBAT:
                return Microbot.isPluginEnabled(KspAioFighterPlugin.class);
            default:
                return false;
        }
    }

    private void stopTrainingModules()
    {
        stopIfEnabled(KspWillowChopperPlugin.class);
        stopIfEnabled(AutoMiningPlugin.class);
        stopIfEnabled(KspDirectFishingPlugin.class);
        stopIfEnabled(KspAioFighterPlugin.class);
        trainingModule = TrainingModule.NONE;
        trainingVariant = "";
        activeModule = "-";
    }

    private void stopIfEnabled(Class<? extends Plugin> pluginClass)
    {
        if (Microbot.isPluginEnabled(pluginClass))
        {
            Plugin plugin = Microbot.getPlugin(pluginClass);
            if (plugin != null)
            {
                Microbot.stopPlugin(plugin);
            }
        }
    }

    private ToolTier bestTier(ToolTier[] tiers, int level)
    {
        for (ToolTier tier : tiers)
        {
            if (level >= tier.level)
            {
                return tier;
            }
        }
        return null;
    }

    private ToolTier bestOwnedTier(ToolTier[] tiers, int level)
    {
        for (ToolTier tier : tiers)
        {
            if (level >= tier.level && ownsAnywhere(tier.name))
            {
                return tier;
            }
        }
        return null;
    }

    private String describeNextUpgrade()
    {
        ToolTier axe = bestTier(AXES, Rs2Player.getRealSkillLevel(Skill.WOODCUTTING));
        if (axe != null && !ownsAnywhere(axe.name))
        {
            return axe.name;
        }

        ToolTier pickaxe = bestTier(PICKAXES, Rs2Player.getRealSkillLevel(Skill.MINING));
        if (pickaxe != null && !ownsAnywhere(pickaxe.name))
        {
            return pickaxe.name;
        }

        return "-";
    }

    @Override
    public void shutdown()
    {
        stopTrainingModules();
        super.shutdown();
    }

    public F2pBuilderPhase getPhase()
    {
        return phase;
    }

    public String getStatus()
    {
        return status;
    }

    public String getActiveModule()
    {
        return activeModule;
    }

    public String getCurrentQuest()
    {
        return currentQuest;
    }

    public String getNextUpgrade()
    {
        return nextUpgrade;
    }

    public F2pTradeRestrictionTracker.Snapshot getRestriction()
    {
        return restriction;
    }

    public int getLiquidCoins()
    {
        return liquidCoins();
    }

    private static final class ToolTier
    {
        private final String name;
        private final int level;

        private ToolTier(String name, int level)
        {
            this.name = name;
            this.level = level;
        }
    }
}
