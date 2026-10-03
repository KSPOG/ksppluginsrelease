package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.ksputil.KspGrandExchangeSafe;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@Singleton
public class KspJewelryCrafterScript extends Script
{
    private static final int LOOP_MS = 650;
    private static final int EDGEVILLE_FURNACE_ID = 16469;
    private static final int EDGEVILLE_DIRECT_BANK_RADIUS = 20;
    private static final int BANK_WIDGET_GROUP = 12;
    private static final int BANK_WIDGET_CHILD = 1;
    private static final int GE_QUANTITY_X_CHILD = 7;
    private static final int GE_PRICE_X_CHILD = 12;
    private static final int GE_SEARCH_GROUP = 162;
    private static final int GE_SEARCH_PROMPT_CHILD = 52;
    private static final int GE_SELECTED_PRICE_CHILD = 41;
    private static final int GE_VALUE_ENTRY_ATTEMPTS = 3;
    private static final int GE_PRICE_CLICK_DELAY_MIN_MS = 650;
    private static final int GE_PRICE_CLICK_DELAY_MAX_MS = 950;
    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
    private static final WorldPoint EDGEVILLE_BANK = new WorldPoint(3096, 3494, 0);
    private static final WorldPoint EDGEVILLE_FURNACE = new WorldPoint(3109, 3499, 0);
    private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3487, 0);
