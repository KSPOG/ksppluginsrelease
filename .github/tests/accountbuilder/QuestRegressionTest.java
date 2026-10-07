import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspQuestTask;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.sheepshearer.reqs.SheepRequirements;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.sheepshearer.sheepscript.SheepScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.xmarksthespot.xmarksscript.XMarksScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.impcatcher.impscript.ImpScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.therestlessghost.ghostscript.GhostScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.*;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import org.mockito.MockedStatic;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Callable;
import static org.mockito.Mockito.*;

/** Runs against the compiled plugin + released client with Mockito static mocks. */
public final class QuestRegressionTest
{
    private static int checks;
    private static void check(boolean condition, String description)
    {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
    private static Object invoke(SimpleQuestScript script, String method) throws Exception
    {
        Method m = SimpleQuestScript.class.getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(script);
    }
    private static class TestX extends XMarksScript
    {
        String npc;
        void step() { progressQuest(); }
        @Override protected void talkTo(String name, WorldPoint point) { npc = name; }
        int required() { return requirements().length; }
    }
    private static class TestImp extends ImpScript
    {
        int object;
        String npc;
        void step() { progressQuest(); }
        @Override protected void interactObject(int id, String action, WorldPoint point) { object = id; }
        @Override protected void talkTo(String name, WorldPoint point) { npc = name; }
    }
    private static class TestGhost extends GhostScript
    {
        int object;
        String npc;
        void step() { progressQuest(); }
        boolean restore() { return prepareQuestItems(); }
        @Override protected boolean walkTo(String target, WorldPoint point, int radius) { return true; }
        @Override protected void interactObject(int id, String action, WorldPoint point) { object = id; }
        @Override protected void talkTo(String name, WorldPoint point) { npc = name; }
    }

    public static void main(String[] args) throws Exception
    {
        check(SheepRequirements.remaining(0)[0].quantity == 20, "Unstarted wool requirement");
        check(SheepRequirements.remaining(1)[0].quantity == 20, "Started wool requirement");
        check(SheepRequirements.remaining(6)[0].quantity == 15, "Partial wool delivery");
        check(SheepRequirements.remaining(20)[0].quantity == 1, "Last wool remaining");
        check(SheepRequirements.remaining(21)[0].quantity == 0, "Delivered wool is not repurchased");
        for (String name : new String[] { "SHEEP_SHEARER", "X_MARKS_THE_SPOT", "THE_RESTLESS_GHOST", "IMP_CATCHER" })
            check(KspQuestTask.valueOf(name) != null, "Single quest registration: " + name);

        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        ItemManager prices = mock(ItemManager.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(a -> Optional.ofNullable(((Callable<?>) a.getArgument(0)).call()));
        when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[8]);
        when(client.getVarpValue(179)).thenReturn(1);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2GrandExchange> ge = mockStatic(Rs2GrandExchange.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<KspWalkerGuard> walker = mockStatic(KspWalkerGuard.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class);
             MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class))
        {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            microbot.when(Microbot::getItemManager).thenReturn(prices);
            SheepScript sheep = new SheepScript();
            inventory.when(() -> Rs2Inventory.itemQuantity(ItemID.BALL_OF_WOOL)).thenReturn(6);
            bank.when(() -> Rs2Bank.count("Ball of wool")).thenReturn(10);
            check(sheep.getMissingRequirementCost() == 4_000, "Budget counts inventory + bank quantities");
            GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
            when(offer.getItemId()).thenReturn(ItemID.BALL_OF_WOOL);
            when(offer.getTotalQuantity()).thenReturn(4);
            when(offer.getState()).thenReturn(GrandExchangeOfferState.BUYING);
            when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[] { offer });
            check(sheep.getMissingRequirementCost() == 0, "Active paid offers are excluded from budget");
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3164, 3487, 0));
            ge.when(Rs2GrandExchange::isOpen).thenReturn(true);
            ge.when(Rs2GrandExchange::getAvailableSlotsCount).thenReturn(3);
            invoke(sheep, "buyMissingRequirements");
            ge.verify(() -> Rs2GrandExchange.buyItem(anyString(), anyInt(), anyInt()), never());
            checks++;
            when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
            invoke(sheep, "buyMissingRequirements");
            ge.verify(() -> Rs2GrandExchange.collectOffer(GrandExchangeSlots.values()[0], true));
            checks++;
            ge.clearInvocations();
            when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[8]);
            inventory.when(() -> Rs2Inventory.itemQuantity(995)).thenReturn(4_000);
            invoke(sheep, "buyMissingRequirements");
            ge.verify(() -> Rs2GrandExchange.buyItem("Ball of wool", 1_000, 4));
            checks++;

            ge.when(Rs2GrandExchange::isOpen).thenReturn(false);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);
            inventory.when(() -> Rs2Inventory.itemQuantity(ItemID.BALL_OF_WOOL)).thenReturn(0);
            bank.when(() -> Rs2Bank.count("Ball of wool")).thenReturn(20);
            invoke(sheep, "prepareAtBank");
            invoke(sheep, "prepareAtBank");
            bank.verify(() -> Rs2Bank.withdrawX("Ball of wool", 20));
            checks++;
            ge.verify(() -> Rs2GrandExchange.buyItem(anyString(), anyInt(), anyInt()), times(1));
            checks++;

            WorldPoint[] tiles = { new WorldPoint(3230, 3209, 0), new WorldPoint(3203, 3212, 0),
                    new WorldPoint(3109, 3264, 0), new WorldPoint(3078, 3259, 0) };
            for (int i = 0; i < tiles.length; i++)
            {
                final int stage = i + 2;
                microbot.when(() -> Microbot.getVarbitValue(VarbitID.CLUEQUEST)).thenReturn(stage);
                player.when(Rs2Player::getWorldLocation).thenReturn(tiles[i]);
                inventory.clearInvocations();
                new TestX().step();
                inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"));
                checks++;
            }
            microbot.when(() -> Microbot.getVarbitValue(VarbitID.CLUEQUEST)).thenReturn(2);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3229, 3209, 0));
            inventory.clearInvocations();
            new TestX().step();
            inventory.verify(() -> Rs2Inventory.interact(ItemID.SPADE, "Dig"), never());
            checks++;
            microbot.when(() -> Microbot.getVarbitValue(VarbitID.CLUEQUEST)).thenReturn(6);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.CLUEQUEST_CASKET)).thenReturn(true);
            TestX x = new TestX(); x.step();
            check("Veos".equals(x.npc), "Casket delivery");
            microbot.when(() -> Microbot.getVarbitValue(VarbitID.CLUEQUEST)).thenReturn(7);
            check(new TestX().required() == 0, "Spade not repurchased after hand-in");

            for (int plane = 0; plane < 3; plane++)
            {
                player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3103, 3159, plane));
                TestImp imp = new TestImp(); imp.step();
                if (plane == 0) check(imp.object == ObjectID.FAI_WIZTOWER_SPIRALSTAIRS, "Tower ground floor");
                if (plane == 1) check(imp.object == ObjectID.FAI_WIZTOWER_SPIRALSTAIRS_MIDDLE, "Tower first floor");
                if (plane == 2) check("Wizard Mizgog".equals(imp.npc), "Tower quest delivery");
            }
            when(client.getVarpValue(107)).thenReturn(0);
            TestGhost ghost = new TestGhost(); ghost.step();
            check("Father Aereck".equals(ghost.npc), "Ghost start");
            when(client.getVarpValue(107)).thenReturn(1);
            ghost = new TestGhost(); ghost.step();
            check("Father Urhney".equals(ghost.npc), "Ghost amulet source");
            when(client.getVarpValue(107)).thenReturn(2);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.AMULET_OF_GHOSTSPEAK)).thenReturn(true);
            ghost = new TestGhost(); ghost.step();
            inventory.verify(() -> Rs2Inventory.interact(ItemID.AMULET_OF_GHOSTSPEAK, "Wear"));
            checks++;
            when(client.getVarpValue(107)).thenReturn(3);
            ghost = new TestGhost(); ghost.step();
            check(ghost.object == ObjectID.RESTLESS_GHOST_ALTAR, "Skull collection");
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.GHOSTSKULL)).thenReturn(true);
            ghost = new TestGhost(); ghost.step();
            check(ghost.object == ObjectID.SHUTGHOSTCOFFIN, "Skull return opens coffin");
            when(client.getVarpValue(107)).thenReturn(4);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.GHOSTSKULL)).thenReturn(false);
            bank.when(() -> Rs2Bank.count("Ghost's skull")).thenReturn(1);
            ghost = new TestGhost();
            check(!ghost.restore(), "Banked skull restored before closing bank");
            bank.verify(() -> Rs2Bank.withdrawX("Ghost's skull", 1));
            checks++;
            dialogue.when(Rs2Dialogue::isInCutScene).thenReturn(true);
            check((boolean) invoke(sheep, "handleDialogue"), "Cutscene blocks quest interactions");
            inventory.when(Rs2Inventory::isFull).thenReturn(true);
            check(!(boolean) invoke(sheep, "ensureInventorySpace"), "Full inventory triggers bank preparation");
            bank.verify(() -> Rs2Bank.depositAllExcept(any(java.util.Collection.class)));
            checks++;
            player.when(() -> Rs2Player.getQuestState(Quest.SHEEP_SHEARER)).thenReturn(QuestState.IN_PROGRESS);
            check(!sheep.isComplete(), "Missing wool does not imply quest complete");
            player.when(() -> Rs2Player.getQuestState(Quest.SHEEP_SHEARER)).thenReturn(QuestState.FINISHED);
            check(sheep.isComplete(), "Completion uses game quest state");
        }
        System.out.println("PASS: " + checks + " quest regression checks");
    }
}
