package net.runelite.client.plugins.microbot.kspf2pgatheringprofit;

import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class KspF2pGatheringProfitScript extends Script
{
    public enum EscapeState { SAFE, THREAT, RETREATING, HOPPING, COOLDOWN }

    private static final WorldPoint PORT_SARIM_DOCK = new WorldPoint(3029, 3217, 0);
    private static final WorldPoint KARAMJA_DOCK = new WorldPoint(2956, 3146, 0);
    private static final String[] KARAMJA_SAILORS = {"Seaman Lorris", "Seaman Thresnor", "Captain Tobias"};
    private static final String COINS = "Coins";

    private static final ToolTier[] MINING_TOOLS = {
            new ToolTier("Rune pickaxe", 41), new ToolTier("Adamant pickaxe", 31),
            new ToolTier("Mithril pickaxe", 21), new ToolTier("Black pickaxe", 11),
            new ToolTier("Steel pickaxe", 6), new ToolTier("Iron pickaxe", 1),
            new ToolTier("Bronze pickaxe", 1)
    };
    private static final ToolTier[] WOODCUTTING_TOOLS = {
            new ToolTier("Rune axe", 41), new ToolTier("Adamant axe", 31),
            new ToolTier("Mithril axe", 21), new ToolTier("Black axe", 11),
            new ToolTier("Steel axe", 6), new ToolTier("Iron axe", 1),
            new ToolTier("Bronze axe", 1)
    };

    private KspF2pGatheringProfitConfig config;
    private volatile GatheringMethod method;
    private volatile WorldPoint location;
    private volatile String status = "Starting";
    private volatile EscapeState escape = EscapeState.SAFE;
    private volatile int gpHour, xpHour, realGpHour, failures, hops, gathered;
    private volatile boolean halted;

    private long lastEval, start, noNodeSince, lastHop, lastGatherClick;
    private int miningStartXp, woodcuttingStartXp, fishingStartXp, lastInventoryQuantity, lastUnitValue;

    public boolean run(KspF2pGatheringProfitConfig config)
    {
        this.config = config;
        method = null;
        location = null;
        status = "Starting";
        escape = EscapeState.SAFE;
        gpHour = xpHour = realGpHour = failures = hops = gathered = 0;
        halted = false;
        lastEval = noNodeSince = lastHop = lastGatherClick = 0L;
        start = System.currentTimeMillis();
        miningStartXp = skillXp(Skill.MINING);
        woodcuttingStartXp = skillXp(Skill.WOODCUTTING);
        fishingStartXp = skillXp(Skill.FISHING);
        lastInventoryQuantity = 0;
        lastUnitValue = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop()
    {
        try
        {
            if (!super.run() || !Microbot.isLoggedIn() || halted) return;
            if (config.stopIfMembersWorld() && Rs2Player.isInMemberWorld())
            {
                halt("F2P world required");
                return;
            }

            trackYield();
            if (method != null && method.wilderness() && wildernessSafety()) return;
            if (method == null || System.currentTimeMillis() - lastEval >= config.reevaluateMinutes() * 60_000L) evaluate();
            if (method == null)
            {
                halt("No unlocked method");
                return;
            }
            if (!ensureToolAndSupplies()) return;

            if (method.karamjaFishing())
            {
                handleKaramjaFishing();
                return;
            }

            if (Rs2Inventory.isFull())
            {
                if (config.bankWhenFull()) bank();
                else Rs2Inventory.dropAll(item -> item != null && isMethodProduct(item.getName()));
                return;
            }

            WorldPoint player = Rs2Player.getWorldLocation();
            if (location == null) location = chooseLocation(method, player);
            if (player == null || player.distanceTo(location) > 18)
            {
                status = "Walking: " + method.name();
                Rs2Walker.walkTo(location, 4);
                return;
            }
            gather();
        }
        catch (Exception ex)
        {
            recover(ex.getClass().getSimpleName());
            Microbot.log("KSP F2P Gathering Profit: " + ex.getMessage());
        }
    }

    private void evaluate()
    {
        GatheringMethod best = null;
        double bestScore = -1.0;
        for (GatheringMethod candidate : GatheringMethod.values())
        {
            int level = config.progressive() ? Rs2Player.getRealSkillLevel(candidate.skill) : startingLevel(candidate.skill);
            if (!allowed(candidate) || (candidate.wilderness() && config.avoidWilderness()) || level < candidate.level) continue;
            int gp = estimate(candidate);
            double candidateScore = score(gp, candidate.xpHour);
            if (candidateScore > bestScore)
            {
                best = candidate;
                bestScore = candidateScore;
            }
        }

        boolean changed = best != method;
        method = best;
        if (best != null)
        {
            location = chooseLocation(best, Rs2Player.getWorldLocation());
            gpHour = estimate(best);
            xpHour = best.xpHour;
            lastUnitValue = unitValue(best);
            lastInventoryQuantity = productQuantity(best);
            failures = 0;
            status = (changed && config.progressive() ? "Progressed/selected: " : "Selected: ") + best.name();
        }
        lastEval = System.currentTimeMillis();
    }

    private WorldPoint chooseLocation(GatheringMethod candidate, WorldPoint from)
    {
        return config.autoLocation() ? candidate.closest(from) : candidate.locations[0];
    }

    private int startingLevel(Skill skill)
    {
        int xp;
        switch (skill)
        {
            case MINING: xp = miningStartXp; break;
            case WOODCUTTING: xp = woodcuttingStartXp; break;
            case FISHING: xp = fishingStartXp; break;
            default: return Rs2Player.getRealSkillLevel(skill);
        }
        return net.runelite.api.Experience.getLevelForXp(Math.max(0, xp));
    }

    private boolean allowed(GatheringMethod candidate)
    {
        if (config.skillMode() == KspF2pGatheringProfitConfig.SkillMode.MINING && candidate.skill != Skill.MINING) return false;
        if (config.skillMode() == KspF2pGatheringProfitConfig.SkillMode.WOODCUTTING && candidate.skill != Skill.WOODCUTTING) return false;
        if (config.skillMode() == KspF2pGatheringProfitConfig.SkillMode.FISHING && candidate.skill != Skill.FISHING) return false;
        if (candidate.skill == Skill.MINING && config.miningTarget() != KspF2pGatheringProfitConfig.MiningTarget.AUTO)
            return candidate.name().equals(config.miningTarget().name());
        if (candidate.skill == Skill.WOODCUTTING && config.woodcuttingTarget() != KspF2pGatheringProfitConfig.WoodcuttingTarget.AUTO)
            return candidate.name().equals(config.woodcuttingTarget().name());
        if (candidate.skill == Skill.FISHING && config.fishingTarget() != KspF2pGatheringProfitConfig.FishingTarget.AUTO)
            return candidate.name().equals(config.fishingTarget().name());
        return true;
    }

    private double score(int gp, int xp)
    {
        switch (config.objective())
        {
            case MAX_GP: return gp;
            case MAX_XP: return xp;
            case GP_PRIORITY: return gp * .75 + xp * .25;
            case XP_PRIORITY: return gp * .25 + xp * .75;
            default:
                int total = Math.max(1, config.gpWeight() + config.xpWeight());
                return gp * (config.gpWeight() / (double) total) + xp * (config.xpWeight() / (double) total);
        }
    }

    private int unitValue(GatheringMethod candidate)
    {
        WikiPrice price = Rs2GrandExchange.getRealTimePrices(candidate.productId);
        if (price == null) return 0;
        int sell = Math.min(price.buyPrice, price.sellPrice);
        if (sell <= 0) sell = Math.max(price.buyPrice, price.sellPrice);
        return Math.max(0, sell - (int) Math.floor(sell * .02));
    }

    private int estimate(GatheringMethod candidate)
    {
        return unitValue(candidate) * candidate.unitsHour;
    }

    private void trackYield()
    {
        if (method == null) return;
        int quantity = productQuantity(method);
        if (quantity > lastInventoryQuantity) gathered += quantity - lastInventoryQuantity;
        lastInventoryQuantity = quantity;
        long elapsed = Math.max(1L, System.currentTimeMillis() - start);
        realGpHour = (int) ((long) gathered * lastUnitValue * 3_600_000L / elapsed);
    }

    private int productQuantity(GatheringMethod candidate)
    {
        if (candidate == GatheringMethod.TROUT_SALMON)
            return Rs2Inventory.count("Raw trout") + Rs2Inventory.count("Raw salmon");
        if (candidate == GatheringMethod.TUNA_SWORDFISH)
            return Rs2Inventory.count("Raw tuna") + Rs2Inventory.count("Raw swordfish");
        return Rs2Inventory.itemQuantity(candidate.productName);
    }

    private boolean isMethodProduct(String name)
    {
        if (name == null || method == null) return false;
        if (method == GatheringMethod.TROUT_SALMON)
            return name.equalsIgnoreCase("Raw trout") || name.equalsIgnoreCase("Raw salmon");
        if (method == GatheringMethod.TUNA_SWORDFISH)
            return name.equalsIgnoreCase("Raw tuna") || name.equalsIgnoreCase("Raw swordfish");
        return method.productName.equalsIgnoreCase(name);
    }

    private boolean ensureToolAndSupplies()
    {
        if (method.skill == Skill.MINING)
        {
            int level = Rs2Player.getRealSkillLevel(Skill.MINING);
            return hasUsableTool(MINING_TOOLS, level) || withdrawBestUsable(MINING_TOOLS, level);
        }
        if (method.skill == Skill.WOODCUTTING)
        {
            int level = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            return hasUsableTool(WOODCUTTING_TOOLS, level) || withdrawBestUsable(WOODCUTTING_TOOLS, level);
        }

        if (method.tool != null && !hasTool(method.tool) && !withdraw(method.tool, false)) return false;
        if (method == GatheringMethod.TROUT_SALMON && !Rs2Inventory.hasItem("Feather") && !withdraw("Feather", true)) return false;
        return !method.karamjaFishing() || ensureKaramjaTravelCoins();
    }

    private boolean hasUsableTool(ToolTier[] tools, int skillLevel)
    {
        for (ToolTier tool : tools)
            if (skillLevel >= tool.level && hasTool(tool.name)) return true;
        return false;
    }

    private boolean hasTool(String name)
    {
        return Rs2Inventory.hasItem(name) || Rs2Equipment.isWearing(name);
    }

    private boolean withdrawBestUsable(ToolTier[] tools, int skillLevel)
    {
        if (!KspVerifiedBank.walkToBankAndOpenBank())
        {
            recover("Bank/tool");
            return false;
        }
        for (ToolTier tool : tools)
        {
            if (skillLevel < tool.level || !Rs2Bank.hasItem(tool.name)) continue;
            status = "Withdrawing " + tool.name;
            boolean started = Rs2Bank.withdrawX(tool.name, 1, true);
            if (started) started = sleepUntil(() -> Rs2Inventory.hasItem(tool.name), 2_500);
            Rs2Bank.closeBank();
            return started;
        }
        Rs2Bank.closeBank();
        halt("No suitable tool in bank");
        return false;
    }

    private boolean withdraw(String name, boolean all)
    {
        if (name == null || name.isBlank()) return true;
        if (!KspVerifiedBank.walkToBankAndOpenBank())
        {
            recover("Bank/tool");
            return false;
        }
        if (!Rs2Bank.hasItem(name))
        {
            Rs2Bank.closeBank();
            halt("Missing " + name);
            return false;
        }

        boolean started;
        if (all)
        {
            int before = Rs2Inventory.itemQuantity(name);
            Rs2Bank.withdrawAll(name);
            started = sleepUntil(() -> Rs2Inventory.itemQuantity(name) > before, 2_500);
        }
        else
        {
            started = Rs2Bank.withdrawX(name, 1, true);
            if (started) started = sleepUntil(() -> Rs2Inventory.hasItem(name), 2_500);
        }
        Rs2Bank.closeBank();
        return started;
    }

    private void gather()
    {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) return;
        if (System.currentTimeMillis() - lastGatherClick < 1_200L) return;
        status = "Gathering: " + method.name();

        boolean clicked;
        if (method.skill == Skill.FISHING)
        {
            Rs2NpcModel spot = Microbot.getRs2NpcCache().query()
                    .withName(method.nodeName)
                    .where(npc -> hasAction(npc.getActions(), method.action))
                    .nearestOnClientThread();
            clicked = spot != null && spot.click(method.action);
        }
        else
        {
            Rs2TileObjectModel node = Microbot.getRs2TileObjectCache().query()
                    .withName(method.nodeName)
                    .where(object -> hasAction(object.getActions(), method.action))
                    .nearestOnClientThread(20);
            clicked = node != null && node.click(method.action);
        }

        if (!clicked)
        {
            handleMissingNode();
            return;
        }
        lastGatherClick = System.currentTimeMillis();
        noNodeSince = 0L;
        failures = 0;
        Rs2Player.waitForXpDrop(method.skill, 12_000, true);
    }

    private boolean hasAction(String[] actions, String wanted)
    {
        if (actions == null || wanted == null) return false;
        for (String action : actions)
            if (action != null && action.equalsIgnoreCase(wanted)) return true;
        return false;
    }

    private void handleMissingNode()
    {
        if (noNodeSince == 0L) noNodeSince = System.currentTimeMillis();
        long waited = (System.currentTimeMillis() - noNodeSince) / 1_000L;
        int grace = method.skill == Skill.MINING ? config.respawnGraceSeconds() : config.competitionSeconds();
        int nearby = nearbyPlayers(config.playerCompetitionRadius());
        status = "Waiting respawn " + waited + "s; players=" + nearby;
        if (config.worldHop() && waited >= grace && nearby > 0) hop("Competition");
        else if (config.worldHop() && waited >= Math.max(grace, config.competitionSeconds()) * 2L) hop("Depleted");
    }

    private int nearbyPlayers(int radius)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null
                    || Microbot.getClient().getTopLevelWorldView() == null) return 0;
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            WorldPoint local = localPlayer.getWorldLocation();
            if (local == null) return 0;
            return (int) Microbot.getClient().getTopLevelWorldView().players().stream()
                    .filter(Objects::nonNull)
                    .filter(player -> player != localPlayer && player.getWorldLocation() != null)
                    .filter(player -> player.getWorldLocation().getPlane() == local.getPlane())
                    .filter(player -> player.getWorldLocation().distanceTo(local) <= radius)
                    .count();
        }).orElse(0);
    }

    private boolean wildernessSafety()
    {
        int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        int max = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int percent = max <= 0 ? 100 : hp * 100 / max;
        int threats = nearbyPlayers(config.wildernessThreatRadius());
        if (threats <= 0 && percent > config.wildernessEscapeHp())
        {
            if (escape != EscapeState.SAFE && System.currentTimeMillis() - lastHop > 8_000L) escape = EscapeState.SAFE;
            return false;
        }

        escape = EscapeState.THREAT;
        status = "Wilderness threat: " + threats + " player(s), HP " + percent + "%";
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && player.getY() > 3520)
        {
            escape = EscapeState.RETREATING;
            Rs2Walker.walkTo(new WorldPoint(player.getX(), 3520, player.getPlane()), 4);
            return true;
        }
        escape = EscapeState.HOPPING;
        hop("Wilderness safety");
        return true;
    }

    private void hop(String reason)
    {
        if (!config.worldHop() || System.currentTimeMillis() - lastHop < 12_000L) return;
        int world = Login.getRandomWorld(false);
        if (world <= 0)
        {
            recover("No F2P world available");
            return;
        }
        status = "World hop: " + reason + " -> " + world;
        if (method != null && method.wilderness()) escape = EscapeState.HOPPING;
        if (!Microbot.hopToWorld(world))
        {
            recover("World hop failed");
            return;
        }
        hops++;
        lastHop = System.currentTimeMillis();
        noNodeSince = 0L;
        failures = 0;
        sleepUntil(() -> !Microbot.isHopping(), 10_000);
        if (method != null) location = chooseLocation(method, Rs2Player.getWorldLocation());
        escape = EscapeState.COOLDOWN;
    }

    private void bank()
    {
        status = "Banking";
        if (!KspVerifiedBank.walkToBankAndOpenBank())
        {
            recover("Bank");
            return;
        }
        Set<Integer> keep = new HashSet<>();
        for (Rs2ItemModel item : Rs2Inventory.all())
        {
            String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
            if (isGatheringTool(name) || name.equals("feather") || name.equals("coins")) keep.add(item.getId());
        }
        if (keep.isEmpty()) Rs2Bank.depositAll();
        else Rs2Bank.depositAllExcept(keep.toArray(new Integer[0]));
        Rs2Inventory.waitForInventoryChanges(1_200);
        Rs2Bank.closeBank();
        lastInventoryQuantity = 0;
        failures = 0;
    }

    private boolean isGatheringTool(String name)
    {
        return name.contains("pickaxe") || name.endsWith(" axe") || name.equals("harpoon")
                || name.equals("lobster pot") || name.equals("fly fishing rod");
    }

    private void handleKaramjaFishing()
    {
        if (Rs2DepositBox.isOpen())
        {
            depositKaramjaFish();
            return;
        }
        if (Rs2Inventory.isFull())
        {
            if (!config.bankWhenFull()) Rs2Inventory.dropAll(item -> item != null && isKaramjaFish(item.getName()));
            else if (isKaramja()) returnToPortSarim();
            else usePortSarimDepositBox();
            return;
        }
        if (isKaramja())
        {
            WorldPoint player = Rs2Player.getWorldLocation();
            if (player == null || player.distanceTo(location) > 18)
            {
                status = "Walking: " + method.name();
                Rs2Walker.walkTo(location, 4);
            }
            else gather();
            return;
        }
        if (isPortSarim()) travelToMusaPoint();
        else
        {
            status = "Walking to Port Sarim";
            Rs2Walker.walkTo(PORT_SARIM_DOCK, 4);
        }
    }

    private boolean ensureKaramjaTravelCoins()
    {
        if (coinCount() >= 60) return true;
        if (isKaramja())
        {
            halt("Not enough coins to leave Karamja");
            return false;
        }
        if (!KspVerifiedBank.walkToBankAndOpenBank())
        {
            recover("Bank/coins");
            return false;
        }
        int need = Math.max(0, 1_000 - coinCount());
        if (need > 0 && (!Rs2Bank.hasItem(COINS) || !Rs2Bank.withdrawX(COINS, need, true)))
        {
            Rs2Bank.closeBank();
            halt("Need coins for Karamja ferry");
            return false;
        }
        sleepUntil(() -> coinCount() >= 60, 2_500);
        Rs2Bank.closeBank();
        return coinCount() >= 60;
    }

    private void travelToMusaPoint()
    {
        if (coinCount() < 30)
        {
            ensureKaramjaTravelCoins();
            return;
        }
        Rs2NpcModel sailor = findNpc(KARAMJA_SAILORS);
        if (sailor == null)
        {
            status = "Walking to Karamja sailors";
            Rs2Walker.walkTo(PORT_SARIM_DOCK, 4);
            return;
        }
        status = "Travelling to Musa Point";
        if (clickAny(sailor, "Musa Point", "Travel", "Pay-fare", "Pay-Fare")) sleepUntil(this::isKaramja, 15_000);
    }

    private void returnToPortSarim()
    {
        if (coinCount() < 30)
        {
            halt("Need 30 coins to return from Karamja");
            return;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.distanceTo(KARAMJA_DOCK) > 12)
        {
            status = "Walking to Customs officer";
            Rs2Walker.walkTo(KARAMJA_DOCK, 4);
            return;
        }
        Rs2NpcModel officer = findNpc("Customs officer");
        if (officer == null)
        {
            status = "Finding Customs officer";
            return;
        }
        status = "Travelling to Port Sarim";
        if (clickAny(officer, "Port Sarim", "Travel", "Pay-fare", "Pay-Fare")) sleepUntil(this::isPortSarim, 15_000);
    }

    private void usePortSarimDepositBox()
    {
        status = "Walking to Port Sarim deposit box";
        Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.PORT_SARIM);
    }

    private void depositKaramjaFish()
    {
        status = "Depositing fish";
        Rs2DepositBox.depositAllExcept("Harpoon", "Lobster pot", "Coins");
        sleepUntil(() -> karamjaFishCount() == 0, 3_000);
        Rs2DepositBox.closeDepositBox();
        lastInventoryQuantity = 0;
        failures = 0;
        status = "Returning to Musa Point";
    }

    private int karamjaFishCount()
    {
        return Rs2Inventory.count("Raw lobster") + Rs2Inventory.count("Raw tuna") + Rs2Inventory.count("Raw swordfish");
    }

    private boolean isKaramjaFish(String name)
    {
        return name != null && (name.equalsIgnoreCase("Raw lobster") || name.equalsIgnoreCase("Raw tuna")
                || name.equalsIgnoreCase("Raw swordfish"));
    }

    private Rs2NpcModel findNpc(String... names)
    {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null && Arrays.stream(names).anyMatch(name -> name.equalsIgnoreCase(npc.getName())))
                .nearestOnClientThread();
    }

    private boolean clickAny(Rs2NpcModel npc, String... actions)
    {
        for (String action : actions) if (npc.click(action)) return true;
        return false;
    }

    private int coinCount()
    {
        Rs2ItemModel coins = Rs2Inventory.get(COINS);
        return coins == null ? 0 : coins.getQuantity();
    }

    private boolean isKaramja()
    {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && point.getX() < 3000 && point.getY() >= 3100 && point.getY() < 3250;
    }

    private boolean isPortSarim()
    {
        WorldPoint point = Rs2Player.getWorldLocation();
        return point != null && point.getX() >= 3000 && point.getX() < 3075 && point.getY() >= 3180 && point.getY() < 3260;
    }

    private int skillXp(Skill skill)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() == null ? 0 : Microbot.getClient().getSkillExperience(skill)).orElse(0);
    }

    private void recover(String reason)
    {
        failures++;
        status = "Recovery " + failures + "/" + config.failureLimit() + ": " + reason;
        if (failures >= config.failureLimit()) halt(reason);
    }

    private void halt(String reason)
    {
        halted = true;
        status = "Stopped: " + reason;
    }

    public String getStatus() { return status; }
    public GatheringMethod getMethod() { return method; }
    public String getLocation() { return location == null ? "-" : location.getX() + "," + location.getY(); }
    public EscapeState getEscape() { return escape; }
    public int getGpHour() { return gpHour; }
    public int getRealGpHour() { return realGpHour; }
    public int getXpHour() { return xpHour; }
    public int getFailures() { return failures; }
    public int getHops() { return hops; }
    public int getGathered() { return gathered; }

    public int getXpGained()
    {
        return Math.max(0, skillXp(Skill.MINING) - miningStartXp)
                + Math.max(0, skillXp(Skill.WOODCUTTING) - woodcuttingStartXp)
                + Math.max(0, skillXp(Skill.FISHING) - fishingStartXp);
    }

    public int getActualXpHour()
    {
        long elapsed = Math.max(1L, System.currentTimeMillis() - start);
        return (int) (getXpGained() * 3_600_000L / elapsed);
    }

    @Override
    public void shutdown()
    {
        method = null;
        location = null;
        status = "Stopped";
        gpHour = xpHour = realGpHour = failures = hops = gathered = 0;
        halted = false;
        escape = EscapeState.SAFE;
        super.shutdown();
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
