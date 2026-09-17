package net.runelite.client.plugins.microbot.kspf2pgatheringprofit;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class KspF2pGatheringProfitScript extends Script
{
    public enum EscapeState { SAFE, THREAT, RETREATING, HOPPING, COOLDOWN }

    private static final ToolTier[] MINING_TOOLS = {
            new ToolTier("Rune pickaxe", 41),
            new ToolTier("Adamant pickaxe", 31),
            new ToolTier("Mithril pickaxe", 21),
            new ToolTier("Black pickaxe", 11),
            new ToolTier("Steel pickaxe", 6),
            new ToolTier("Iron pickaxe", 1),
            new ToolTier("Bronze pickaxe", 1)
    };

    private static final ToolTier[] WOODCUTTING_TOOLS = {
            new ToolTier("Rune axe", 41),
            new ToolTier("Adamant axe", 31),
            new ToolTier("Mithril axe", 21),
            new ToolTier("Black axe", 11),
            new ToolTier("Steel axe", 6),
            new ToolTier("Iron axe", 1),
            new ToolTier("Bronze axe", 1)
    };

    private KspF2pGatheringProfitConfig config;
    private volatile GatheringMethod method;
    private volatile WorldPoint location;
    private volatile String status = "Starting";
    private volatile EscapeState escape = EscapeState.SAFE;
    private volatile int gpHour;
    private volatile int xpHour;
    private volatile int realGpHour;
    private volatile int failures;
    private volatile int hops;
    private volatile int gathered;
    private volatile boolean halted;

    private long lastEval;
    private long start;
    private long noNodeSince;
    private long lastHop;
    private int miningStartXp;
    private int woodcuttingStartXp;
    private int fishingStartXp;
    private int lastInventoryQuantity;
    private int lastUnitValue;

    public boolean run(KspF2pGatheringProfitConfig config)
    {
        this.config = config;
        method = null;
        location = null;
        status = "Starting";
        escape = EscapeState.SAFE;
        gpHour = xpHour = realGpHour = failures = hops = gathered = 0;
        halted = false;
        lastEval = noNodeSince = lastHop = 0L;
        start = System.currentTimeMillis();
        miningStartXp = skillXp(Skill.MINING);
        woodcuttingStartXp = skillXp(Skill.WOODCUTTING);
        fishingStartXp = skillXp(Skill.FISHING);
        lastInventoryQuantity = 0;
        lastUnitValue = 0;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::loop, 0, 600, TimeUnit.MILLISECONDS);
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

            if (config.stopIfMembersWorld() && Rs2Player.isInMemberWorld())
            {
                halt("F2P world required");
                return;
            }

            trackYield();

            if (method != null && method.wilderness() && wildernessSafety())
            {
                return;
            }

            if (method == null || System.currentTimeMillis() - lastEval >= config.reevaluateMinutes() * 60_000L)
            {
                evaluate();
            }

            if (method == null)
            {
                halt("No unlocked method");
                return;
            }

            if (!ensureTool())
            {
                return;
            }

            if (Rs2Inventory.isFull())
            {
                if (config.bankWhenFull())
                {
                    bank();
                }
                else
                {
                    Rs2Inventory.dropAll(method.productName);
                }
                return;
            }

            WorldPoint player = Rs2Player.getWorldLocation();
            if (location == null)
            {
                location = method.closest(player);
            }

            if (player == null || player.distanceTo(location) > 18)
            {
                status = "Walking: " + method.name();
                Rs2Walker.walkTo(location);
                return;
            }

            gather();
        }
        catch (Exception e)
        {
            recover(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    private void evaluate()
    {
        GatheringMethod best = null;
        double bestScore = -1.0;

        for (GatheringMethod candidate : GatheringMethod.values())
        {
            if (!allowed(candidate)
                    || (candidate.wilderness() && config.avoidWilderness())
                    || Rs2Player.getRealSkillLevel(candidate.skill) < candidate.level)
            {
                continue;
            }

            int gp = estimate(candidate);
            int xp = candidate.xpHour;
            double score = score(gp, xp);
            if (score > bestScore)
            {
                bestScore = score;
                best = candidate;
            }
        }

        boolean changed = best != method;
        method = best;

        if (best != null)
        {
            location = best.closest(Rs2Player.getWorldLocation());
            gpHour = estimate(best);
            xpHour = best.xpHour;
            lastUnitValue = unitValue(best);
            status = (changed && config.progressive() ? "Progressed/selected: " : "Selected: ") + best.name();
            failures = 0;
            lastInventoryQuantity = Rs2Inventory.itemQuantity(best.productName);
        }

        lastEval = System.currentTimeMillis();
    }

    private boolean allowed(GatheringMethod candidate)
    {
        if (config.skillMode() == KspF2pGatheringProfitConfig.SkillMode.MINING && candidate.skill != Skill.MINING)
        {
            return false;
        }
        if (config.skillMode() == KspF2pGatheringProfitConfig.SkillMode.WOODCUTTING && candidate.skill != Skill.WOODCUTTING)
        {
            return false;
        }
        if (config.skillMode() == KspF2pGatheringProfitConfig.SkillMode.FISHING && candidate.skill != Skill.FISHING)
        {
            return false;
        }

        if (config.progressive())
        {
            return true;
        }

        if (candidate.skill == Skill.MINING && config.miningTarget() != KspF2pGatheringProfitConfig.MiningTarget.AUTO)
        {
            return candidate.name().equals(config.miningTarget().name());
        }
        if (candidate.skill == Skill.WOODCUTTING && config.woodcuttingTarget() != KspF2pGatheringProfitConfig.WoodcuttingTarget.AUTO)
        {
            return candidate.name().equals(config.woodcuttingTarget().name());
        }
        if (candidate.skill == Skill.FISHING && config.fishingTarget() != KspF2pGatheringProfitConfig.FishingTarget.AUTO)
        {
            return candidate.name().equals(config.fishingTarget().name());
        }

        return true;
    }

    private double score(int gp, int xp)
    {
        switch (config.objective())
        {
            case MAX_GP:
                return gp;
            case MAX_XP:
                return xp;
            case GP_PRIORITY:
                return gp * 0.75 + xp * 0.25;
            case XP_PRIORITY:
                return gp * 0.25 + xp * 0.75;
            default:
                int total = Math.max(1, config.gpWeight() + config.xpWeight());
                return gp * (config.gpWeight() / (double) total)
                        + xp * (config.xpWeight() / (double) total);
        }
    }

    private int unitValue(GatheringMethod candidate)
    {
        WikiPrice price = Rs2GrandExchange.getRealTimePrices(candidate.productId);
        if (price == null)
        {
            return 0;
        }

        int sell = Math.min(price.buyPrice, price.sellPrice);
        if (sell <= 0)
        {
            sell = Math.max(price.buyPrice, price.sellPrice);
        }

        return Math.max(0, sell - (int) Math.floor(sell * 0.02));
    }

    private int estimate(GatheringMethod candidate)
    {
        return unitValue(candidate) * candidate.unitsHour;
    }

    private void trackYield()
    {
        if (method == null)
        {
            return;
        }

        int quantity = Rs2Inventory.itemQuantity(method.productName);
        if (quantity > lastInventoryQuantity)
        {
            gathered += quantity - lastInventoryQuantity;
        }
        lastInventoryQuantity = quantity;

        long elapsed = Math.max(1L, System.currentTimeMillis() - start);
        realGpHour = (int) ((long) gathered * lastUnitValue * 3_600_000L / elapsed);
    }

    private boolean ensureTool()
    {
        if (method.skill == Skill.MINING)
        {
            int level = Rs2Player.getRealSkillLevel(Skill.MINING);
            if (hasUsableTool(MINING_TOOLS, level))
            {
                return true;
            }
            return withdrawBestUsableTool(MINING_TOOLS, level);
        }

        if (method.skill == Skill.WOODCUTTING)
        {
            int level = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            if (hasUsableTool(WOODCUTTING_TOOLS, level))
            {
                return true;
            }
            return withdrawBestUsableTool(WOODCUTTING_TOOLS, level);
        }

        if (method.tool != null && (Rs2Inventory.hasItem(method.tool) || Rs2Equipment.isWearing(method.tool)))
        {
            if (method == GatheringMethod.TROUT_SALMON && !Rs2Inventory.hasItem("Feather"))
            {
                return withdraw("Feather", true);
            }
            return true;
        }

        return withdraw(method.tool, false);
    }

    private boolean hasUsableTool(ToolTier[] tools, int skillLevel)
    {
        for (ToolTier tool : tools)
        {
            if (skillLevel < tool.skillLevel)
            {
                continue;
            }
            if (Rs2Inventory.hasItem(tool.name) || Rs2Equipment.isWearing(tool.name))
            {
                return true;
            }
        }
        return false;
    }

    private boolean withdrawBestUsableTool(ToolTier[] tools, int skillLevel)
    {
        if (!KspVerifiedBank.walkToBankAndOpenBank())
        {
            recover("Bank/tool");
            return false;
        }

        for (ToolTier tool : tools)
        {
            if (skillLevel < tool.skillLevel || !Rs2Bank.hasItem(tool.name))
            {
                continue;
            }

            status = "Withdrawing " + tool.name;
            boolean started = Rs2Bank.withdrawX(tool.name, 1, true);
            if (started)
            {
                sleepUntil(() -> Rs2Inventory.hasItem(tool.name) || Rs2Equipment.isWearing(tool.name), 2_500);
            }
            Rs2Bank.closeBank();
            return started;
        }

        Rs2Bank.closeBank();
        halt("No suitable tool in bank");
        return false;
    }

    private boolean withdraw(String itemName, boolean all)
    {
        if (itemName == null || itemName.isBlank())
        {
            halt("Missing gathering tool configuration");
            return false;
        }

        if (!KspVerifiedBank.walkToBankAndOpenBank())
        {
            recover("Bank/tool");
            return false;
        }

        if (!Rs2Bank.hasItem(itemName))
        {
            Rs2Bank.closeBank();
            halt("Missing " + itemName);
            return false;
        }

        status = "Withdrawing " + itemName;
        boolean started;
        if (all)
        {
            int before = Rs2Inventory.itemQuantity(itemName);
            Rs2Bank.withdrawAll(itemName);
            started = sleepUntil(() -> Rs2Inventory.itemQuantity(itemName) > before, 2_500);
        }
        else
        {
            started = Rs2Bank.withdrawX(itemName, 1, true);
            if (started)
            {
                started = sleepUntil(() -> Rs2Inventory.hasItem(itemName), 2_500);
            }
        }
        Rs2Bank.closeBank();
        return started;
    }

    private void gather()
    {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            return;
        }

        status = "Gathering: " + method.name();
        boolean clicked;

        if (method.skill == Skill.FISHING)
        {
            Rs2NpcModel spot = Microbot.getRs2NpcCache().query()
                    .withName("Fishing spot")
                    .withAction(method.action)
                    .nearestOnClientThread();
            clicked = spot != null && spot.click(method.action);
        }
        else
        {
            Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query()
                    .withName(method.nodeName)
                    .withAction(method.action)
                    .nearest(20);
            clicked = object != null && object.click(method.action);
        }

        if (!clicked)
        {
            handleMissingNode();
            return;
        }

        noNodeSince = 0L;
        failures = 0;
        Rs2Player.waitForXpDrop(method.skill, 12_000, true);
    }

    private void handleMissingNode()
    {
        if (noNodeSince == 0L)
        {
            noNodeSince = System.currentTimeMillis();
        }

        long waitedSeconds = (System.currentTimeMillis() - noNodeSince) / 1_000L;
        int grace = method.skill == Skill.MINING ? config.respawnGraceSeconds() : config.competitionSeconds();
        int nearby = nearbyPlayers(config.playerCompetitionRadius());
        status = "Waiting respawn " + waitedSeconds + "s; players=" + nearby;

        if (config.worldHop() && waitedSeconds >= grace && nearby > 0)
        {
            hop("Competition");
        }
        else if (config.worldHop() && waitedSeconds >= Math.max(grace, config.competitionSeconds()) * 2L)
        {
            hop("Depleted");
        }
    }

    private int nearbyPlayers(int radius)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Client client = Microbot.getClient();
            if (client == null || client.getLocalPlayer() == null || client.getTopLevelWorldView() == null)
            {
                return 0;
            }

            Player localPlayer = client.getLocalPlayer();
            WorldPoint local = localPlayer.getWorldLocation();
            if (local == null)
            {
                return 0;
            }

            return (int) client.getTopLevelWorldView().players().stream()
                    .filter(Objects::nonNull)
                    .filter(player -> player != localPlayer)
                    .filter(player -> player.getWorldLocation() != null)
                    .filter(player -> player.getWorldLocation().getPlane() == local.getPlane())
                    .filter(player -> player.getWorldLocation().distanceTo(local) <= radius)
                    .count();
        }).orElse(0);
    }

    private boolean wildernessSafety()
    {
        int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int hpPercent = maxHp <= 0 ? 100 : hp * 100 / maxHp;
        int threats = nearbyPlayers(config.wildernessThreatRadius());

        if (threats > 0 || hpPercent <= config.wildernessEscapeHp())
        {
            escape = EscapeState.THREAT;
            status = "Wilderness threat: " + threats + " player(s), HP " + hpPercent + "%";

            WorldPoint player = Rs2Player.getWorldLocation();
            if (player != null && player.getY() > 3520)
            {
                escape = EscapeState.RETREATING;
                Rs2Walker.walkTo(new WorldPoint(player.getX(), 3520, player.getPlane()));
                return true;
            }

            escape = EscapeState.HOPPING;
            hop("Wilderness safety");
            return true;
        }

        if (escape != EscapeState.SAFE && System.currentTimeMillis() - lastHop > 8_000L)
        {
            escape = EscapeState.SAFE;
        }
        return false;
    }

    private void hop(String reason)
    {
        if (!config.worldHop() || System.currentTimeMillis() - lastHop < 12_000L)
        {
            return;
        }

        int world = LoginManager.getRandomWorld(false);
        if (world <= 0)
        {
            recover("No F2P world available");
            return;
        }

        status = "World hop: " + reason + " -> " + world;
        if (method != null && method.wilderness())
        {
            escape = EscapeState.HOPPING;
        }

        if (Microbot.hopToWorld(world))
        {
            hops++;
            lastHop = System.currentTimeMillis();
            noNodeSince = 0L;
            failures = 0;
            sleepUntil(() -> !Microbot.isHopping(), 10_000);
            if (method != null)
            {
                location = method.closest(Rs2Player.getWorldLocation());
            }
            escape = EscapeState.COOLDOWN;
        }
        else
        {
            recover("World hop failed");
        }
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
            if (name.contains("pickaxe")
                    || name.endsWith(" axe")
                    || name.equals("harpoon")
                    || name.equals("lobster pot")
                    || name.equals("fly fishing rod")
                    || name.equals("feather"))
            {
                keep.add(item.getId());
            }
        }

        boolean depositStarted = keep.isEmpty()
                ? Rs2Bank.depositAll()
                : Rs2Bank.depositAllExcept(keep.toArray(new Integer[0]));

        if (depositStarted)
        {
            Rs2Inventory.waitForInventoryChanges(1_200);
        }
        Rs2Bank.closeBank();
        lastInventoryQuantity = 0;
        failures = 0;
    }

    private int skillXp(Skill skill)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Client client = Microbot.getClient();
            return client == null ? 0 : client.getSkillExperience(skill);
        }).orElse(0);
    }

    private void recover(String reason)
    {
        failures++;
        status = "Recovery " + failures + "/" + config.failureLimit() + ": " + reason;
        if (failures >= config.failureLimit())
        {
            halt(reason);
        }
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
        private final int skillLevel;

        private ToolTier(String name, int skillLevel)
        {
            this.name = name;
            this.skillLevel = skillLevel;
        }
    }
}
