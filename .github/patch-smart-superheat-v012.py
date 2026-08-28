from pathlib import Path

root = Path(__file__).resolve().parents[1]
script = root / 'kspsmartsuperheat' / 'KspSmartSuperheatScript.java'
plugin = root / 'kspsmartsuperheat' / 'KspSmartSuperheatPlugin.java'
prices = root / 'kspsmartsuperheat' / 'SuperheatPriceService.java'

s = script.read_text()
s = s.replace('import net.runelite.api.Skill;\n', 'import net.runelite.api.Skill;\nimport net.runelite.api.widgets.Widget;\n')
s = s.replace('import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;\n', 'import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;\nimport net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;\n')
s = s.replace('private static final int LOOP_MS = 650, BANK_GROUP = 12, BANK_ROOT = 1, MAX_GE_ATTEMPTS = 5;', 'private static final int LOOP_MS = 650, BANK_GROUP = 12, BANK_ROOT = 1, GE_PRICE_X_CHILD = 12, MAX_GE_ATTEMPTS = 5;')

old_sell = '''        int qty = Math.min(wanted, Rs2Bank.count(activeRecipe.getOutputId()));
        if (qty <= 0) { unsold.put(activeRecipe, 0); state = SmartSuperheatState.RESTOCKING; return; }
        status = "Withdrawing noted " + activeRecipe.getOutputName();
        if (!Rs2Bank.withdrawX(activeRecipe.getOutputName(), qty, true) || !sleepUntil(() -> Rs2Inventory.itemQuantity(activeRecipe.getOutputName(), true) >= qty, 5000)) return;
'''
new_sell = '''        int bankQty = Rs2Bank.count(activeRecipe.getOutputId());
        if (bankQty <= 0) { unsold.put(activeRecipe, 0); state = SmartSuperheatState.RESTOCKING; return; }
        status = "Withdrawing all noted " + activeRecipe.getOutputName();
        if (!Rs2Bank.withdrawAll(activeRecipe.getOutputName(), true)
            || !sleepUntil(() -> Rs2Inventory.itemQuantity(activeRecipe.getOutputName(), true) >= bankQty, 5000)) return;
        int qty = Rs2Inventory.itemQuantity(activeRecipe.getOutputName(), true);
        if (qty <= 0) { status = "Noted output withdrawal failed"; return; }
'''
if old_sell not in s:
    raise SystemExit('sell block not found')
s = s.replace(old_sell, new_sell, 1)

old_timeout = '        if (o.placedAt > 0 && System.currentTimeMillis() - o.placedAt >= config.geOfferTimeoutSeconds() * 1000L) cancelOrder(o, s.filled);\n'
new_timeout = '        if (o.placedAt > 0 && System.currentTimeMillis() - o.placedAt >= config.geOfferTimeoutSeconds() * 1000L) modifyOrder(o);\n'
if old_timeout not in s:
    raise SystemExit('timeout call not found')
s = s.replace(old_timeout, new_timeout, 1)

start = s.index('    private void cancelOrder(GeOrder o, int filled)')
end = s.index('    private void finishOrder(GeOrder o, int filled, boolean partial)', start)
replacement = '''    private void modifyOrder(GeOrder o)
    {
        if (o.slot == null) { o.placedAt = 0; return; }
        if (o.retry >= MAX_GE_ATTEMPTS)
        {
            o.placedAt = System.currentTimeMillis();
            status = "Waiting at final " + (o.action == GrandExchangeAction.BUY ? "buy" : "sell") + " price: " + o.itemName;
            return;
        }
        int next = o.retry + 1;
        int price = o.action == GrandExchangeAction.BUY
            ? prices.buyOfferPrice(o.itemId, config.buyMarkupPercent(), next)
            : prices.sellOfferPrice(o.itemId, config.sellDiscountPercent(), next);
        if (price <= 0 || price == o.price)
        {
            o.placedAt = System.currentTimeMillis();
            status = "No updated GE price - keeping offer: " + o.itemName;
            return;
        }
        if (!modifyGeOffer(o.slot, price)) return;
        o.retry = next;
        o.price = price;
        o.placedAt = System.currentTimeMillis();
        status = "Modified " + (o.action == GrandExchangeAction.BUY ? "buy" : "sell") + " price: " + o.itemName + " (retry " + o.retry + ")";
    }

    private boolean modifyGeOffer(GrandExchangeSlots slot, int newPrice)
    {
        if (slot == null || newPrice <= 0 || !ensureGeOverview()) return false;
        OfferSnapshot current = offer(slot);
        if (current != null && current.price == newPrice) return true;

        Widget slotWidget = Rs2Widget.getWidget(InterfaceID.GeOffers.INDEX_0 + slot.ordinal());
        if (slotWidget == null) { status = "Waiting for GE slot widget"; return false; }
        status = "Opening GE Modify offer";
        Rs2Widget.clickWidgetFast(slotWidget, 2, 3);
        if (!sleepUntil(() -> geSubScreen() || !Rs2GrandExchange.isOpen(), 3500) || !Rs2GrandExchange.isOpen()) return false;

        if (!Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP))
        {
            Widget modify = Rs2Widget.getWidget(InterfaceID.GeOffers.DETAILS_MODIFY);
            if (modify == null || !Rs2Widget.clickWidget(modify)
                || !sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP) || !Rs2GrandExchange.isOpen(), 3000)) return false;
        }

        Widget setup = Rs2Widget.getWidget(InterfaceID.GeOffers.SETUP);
        Widget priceX = setup == null ? null : setup.getChild(GE_PRICE_X_CHILD);
        if (priceX == null || !Rs2Widget.clickWidget(priceX)
            || !sleepUntil(() -> gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 2500)) return false;

        Rs2GrandExchange.setChatboxValue(newPrice);
        sleep(120, 220);
        Rs2Keyboard.enter();
        if (!sleepUntil(() -> !gePriceInputOpen() || !Rs2GrandExchange.isOpen(), 2500) || !Rs2GrandExchange.isOpen()) return false;

        status = "Confirming modified GE price";
        if (!Rs2Widget.clickWidget(InterfaceID.GeOffers.SETUP_CONFIRM)) return false;
        sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP) || Rs2Widget.hasWidget("Your offer is much") || !Rs2GrandExchange.isOpen(), 3000);
        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            Rs2Widget.clickWidget("Yes");
            sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP) || !Rs2GrandExchange.isOpen(), 3000);
        }
        return sleepUntil(() -> { OfferSnapshot snapshot = offer(slot); return snapshot != null && snapshot.price == newPrice; }, 3000);
    }

    private boolean gePriceInputOpen() { return Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null; }

'''
s = s[:start] + replacement + s[end:]

s = s.replace('return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0);', 'return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0, 0);')
s = s.replace('return new OfferSnapshot(o.getItemId(), o.getState(), o.getQuantitySold());', 'return new OfferSnapshot(o.getItemId(), o.getState(), o.getQuantitySold(), o.getPrice());')

old_order = '''        final GrandExchangeAction action; final int itemId, quantity, price; final String itemName;
        GrandExchangeSlots slot; long placedAt; int attempts;
        GeOrder(GrandExchangeAction action, int itemId, String itemName, int quantity, int price) { this.action = action; this.itemId = itemId; this.itemName = itemName; this.quantity = quantity; this.price = price; }
'''
new_order = '''        final GrandExchangeAction action; final int itemId, quantity; final String itemName; int price;
        GrandExchangeSlots slot; long placedAt; int attempts, retry;
        GeOrder(GrandExchangeAction action, int itemId, String itemName, int quantity, int price) { this.action = action; this.itemId = itemId; this.itemName = itemName; this.quantity = quantity; this.price = price; }
'''
if old_order not in s:
    raise SystemExit('GeOrder block not found')
s = s.replace(old_order, new_order, 1)

old_snap = '''        final int itemId, filled; final GrandExchangeOfferState state;
        OfferSnapshot(int itemId, GrandExchangeOfferState state, int filled) { this.itemId = itemId; this.state = state; this.filled = filled; }
'''
new_snap = '''        final int itemId, filled, price; final GrandExchangeOfferState state;
        OfferSnapshot(int itemId, GrandExchangeOfferState state, int filled, int price) { this.itemId = itemId; this.state = state; this.filled = filled; this.price = price; }
'''
if old_snap not in s:
    raise SystemExit('OfferSnapshot block not found')
s = s.replace(old_snap, new_snap, 1)
script.write_text(s)

p = plugin.read_text()
if 'VERSION = "0.1.1"' not in p:
    raise SystemExit('expected v0.1.1')
plugin.write_text(p.replace('VERSION = "0.1.1"', 'VERSION = "0.1.2"', 1))

q = prices.read_text()
q = q.replace('''    private static int buyPrice(int marketHigh, int markupPercent)
    {
        return Math.max(1, (int) Math.ceil(marketHigh * (1.0 + Math.max(0, markupPercent) / 100.0)));
    }

    private static int sellPrice(int marketLow, int discountPercent)
    {
        return Math.max(1, (int) Math.floor(marketLow * Math.max(0.01, 1.0 - Math.max(0, discountPercent) / 100.0)));
    }
''', '''    public int buyOfferPrice(int itemId, int markupPercent, int retry)
    {
        MarketPrice p = get(itemId);
        return p == null ? -1 : adjustedBuy(p.high, markupPercent, retry);
    }

    public int sellOfferPrice(int itemId, int discountPercent, int retry)
    {
        MarketPrice p = get(itemId);
        return p == null ? -1 : adjustedSell(p.low, discountPercent, retry);
    }

    private static int buyPrice(int marketHigh, int markupPercent) { return adjustedBuy(marketHigh, markupPercent, 0); }
    private static int sellPrice(int marketLow, int discountPercent) { return adjustedSell(marketLow, discountPercent, 0); }
    private static int adjustedBuy(int market, int basePercent, int retry)
    {
        if (market <= 0) return -1;
        double pct = Math.max(0, basePercent) + Math.max(0, retry) * 2.0;
        return Math.max(1, (int) Math.ceil(market * (1.0 + pct / 100.0)));
    }
    private static int adjustedSell(int market, int basePercent, int retry)
    {
        if (market <= 0) return -1;
        double pct = Math.max(0, basePercent) + Math.max(0, retry) * 2.0;
        return Math.max(1, (int) Math.floor(market * Math.max(0.01, 1.0 - pct / 100.0)));
    }
''')
prices.write_text(q)
