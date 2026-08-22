package net.runelite.client.plugins.microbot.kspkaramjafishing;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class KspKaramjaFishingScript extends Script
{
    private static final WorldPoint FISH = new WorldPoint(2924, 3178, 0);
    private static final WorldPoint PORT_DOCK = new WorldPoint(3029, 3217, 0);
    private static final WorldPoint KARAMJA_DOCK = new WorldPoint(2956, 3146, 0);
    private static final String[] SAILORS = {"Seaman Lorris", "Seaman Thresnor", "Captain Tobias"};

    private KspKaramjaFishingConfig config;
    private volatile String status = "Starting";
    private int caught, trips, lastFish = -1;
    private long lastClick;

    public boolean run(KspKaramjaFishingConfig config)
    {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop()
    {
        try
        {
            if (!super.run() || !Microbot.isLoggedIn()) return;

            trackFish();

            if (Rs2DepositBox.isOpen())
            {
                deposit();
                return;
            }

            if (Rs2Inventory.isFull())
            {
                if (isKaramja()) returnToPort();
                else useDepositBox();
                return;
            }

            if (!hasTool())
            {
                status = "Missing " + tool();
                return;
            }

            if (isKaramja())
            {
                if (Rs2Player.getWorldLocation().distanceTo(FISH) > 15)
                {
                    status = "Walking to fishing spot";
                    Rs2Walker.walkTo(FISH, 4);
                }
                else fish();
                return;
            }

            if (isPortSarim()) travelToMusaPoint();
            else
            {
                status = "Walking to Port Sarim";
                Rs2Walker.walkTo(PORT_DOCK, 4);
            }
        }
        catch (Exception e)
        {
            status = "Error - check client log";
            Microbot.log("KSP Karamja Fishing: " + e.getMessage());
        }
    }

    private void fish()
    {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            status = "Fishing " + config.mode();
            return;
        }

        Rs2NpcModel spot = Microbot.getRs2NpcCache().query()
                .where(n -> Arrays.stream(FishingSpot.LOBSTER.getIds()).anyMatch(id -> id == n.getId()))
                .nearestOnClientThread();

        if (spot == null)
        {
            status = "Finding fishing spot";
            Rs2Walker.walkTo(FISH, 4);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastClick < 2_000) return;

        status = config.mode().getAction() + " fishing spot";
        if (spot.click(config.mode().getAction())) lastClick = now;
    }

    private void travelToMusaPoint()
    {
        if (coins() < 60)
        {
            status = "Need at least 60 coins";
            return;
        }

        Rs2NpcModel sailor = npc(SAILORS);
        if (sailor == null)
        {
            status = "Walking to Karamja sailors";
            Rs2Walker.walkTo(PORT_DOCK, 4);
            return;
        }

        status = "Travelling to Musa Point";
        if (clickAny(sailor, "Musa Point", "Travel", "Pay-fare", "Pay-Fare"))
            sleepUntil(this::isKaramja, 15_000);
    }

    private void returnToPort()
    {
        if (coins() < 30)
        {
            status = "Need 30 coins to return";
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(KARAMJA_DOCK) > 12)
        {
            status = "Walking to Customs officer";
            Rs2Walker.walkTo(KARAMJA_DOCK, 4);
            return;
        }

        Rs2NpcModel officer = npc("Customs officer");
        if (officer == null)
        {
            status = "Finding Customs officer";
            return;
        }

        status = "Travelling to Port Sarim";
        if (clickAny(officer, "Port Sarim", "Travel", "Pay-fare", "Pay-Fare"))
            sleepUntil(this::isPortSarim, 15_000);
    }

    private void useDepositBox()
    {
        status = "Walking to Port Sarim deposit box";
        Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.PORT_SARIM);
    }

    private void deposit()
    {
        status = "Depositing fish";
        int before = fishCount();
        Rs2DepositBox.depositAllExcept("Harpoon", "Lobster pot", "Coins");
        sleepUntil(() -> fishCount() == 0, 3_000);
        if (before > 0 && fishCount() == 0) trips++;
        lastFish = fishCount();
        Rs2DepositBox.closeDepositBox();
        status = "Returning to Musa Point";
    }

    private Rs2NpcModel npc(String... names)
    {
        return Microbot.getRs2NpcCache().query()
                .where(n -> Arrays.stream(names).anyMatch(name -> name.equalsIgnoreCase(n.getName())))
                .nearestOnClientThread();
    }

    private boolean clickAny(Rs2NpcModel npc, String... actions)
    {
        for (String action : actions)
            if (npc.click(action)) return true;
        return false;
    }

    private void trackFish()
    {
        int now = fishCount();
        if (lastFish >= 0 && now > lastFish) caught += now - lastFish;
        lastFish = now;
    }

    private boolean isKaramja()
    {
        WorldPoint p = Rs2Player.getWorldLocation();
        return p != null && p.getX() < 3000 && p.getY() >= 3100 && p.getY() < 3250;
    }

    private boolean isPortSarim()
    {
        WorldPoint p = Rs2Player.getWorldLocation();
        return p != null && p.getX() >= 3000 && p.getX() < 3075 && p.getY() >= 3180 && p.getY() < 3260;
    }

    private String tool()
    {
        return config.mode() == KspKaramjaFishingConfig.Mode.LOBSTER ? "Lobster pot" : "Harpoon";
    }

    private boolean hasTool()
    {
        return Rs2Inventory.hasItem(tool());
    }

    public int fishCount()
    {
        return config.mode() == KspKaramjaFishingConfig.Mode.LOBSTER
                ? Rs2Inventory.count("Raw lobster")
                : Rs2Inventory.count("Raw tuna") + Rs2Inventory.count("Raw swordfish");
    }

    public int coins()
    {
        var coins = Rs2Inventory.get("Coins");
        return coins == null ? 0 : coins.getQuantity();
    }

    public String getStatus() { return status; }
    public int getCaught() { return caught; }
    public int getTrips() { return trips; }
    public boolean isToolReady() { return hasTool(); }

    @Override
    public void shutdown()
    {
        status = "Stopped";
        super.shutdown();
    }
}
