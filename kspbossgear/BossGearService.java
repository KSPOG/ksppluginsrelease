package net.runelite.client.plugins.microbot.kspbossgear;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;

/** Owns the selected Wiki loadout and exposes thread-safe highlight state. */
final class BossGearService
{
    private final WikiGearService wikiGearService;
    private volatile ExecutorService executor;
    private static final long OWNERSHIP_SNAPSHOT_MS = 1_500L;

    private final Map<String, Integer> itemIdCache = new ConcurrentHashMap<>();
    private final AtomicLong requestSequence = new AtomicLong();
    private volatile OwnershipSnapshot ownershipSnapshot = OwnershipSnapshot.empty();
    private volatile long ownershipSnapshotAt;

    private volatile WikiGearPage page;
    private volatile String selectedMethod;
    private volatile String selectedInventoryMethod;
    private volatile GearTier selectedTier = GearTier.MID;
    private volatile Selection selection = Selection.empty();
    private volatile boolean loading;
    private volatile String status = "Search for a boss or raid to load Wiki gear/inventory.";

    @Inject
    BossGearService(WikiGearService wikiGearService)
    {
        this.wikiGearService = wikiGearService;
        this.executor = newExecutor();
    }

    synchronized void start()
    {
        if (executor == null || executor.isShutdown()) executor = newExecutor();
    }

    CompletableFuture<WikiGearPage> loadBoss(String boss, boolean forceRefresh)
    {
        start();
        final long request = requestSequence.incrementAndGet();
        loading = true;
        status = "Loading OSRS Wiki loadouts...";

        return CompletableFuture.supplyAsync(() -> {
            try
            {
                WikiGearPage loaded = wikiGearService.load(boss, forceRefresh);
                resolvePageItemIds(loaded);
                if (request == requestSequence.get())
                {
                    synchronized (this)
                    {
                        page = loaded;
                        List<String> equipmentMethods = getMethodNames();
                        selectedMethod = equipmentMethods.isEmpty() ? null : equipmentMethods.get(0);
                        selectedInventoryMethod = null;
                        ensureSelectedInventoryMethod();
                        rebuildSelection();
                        loading = false;
                        status = selection.getRows().isEmpty()
                            ? "Wiki page loaded, but no resolvable loadout items were found."
                            : "Live gear/inventory loaded from the OSRS Wiki.";
                    }
                }
                return loaded;
            }
            catch (Exception ex)
            {
                if (request == requestSequence.get())
                {
                    loading = false;
                    status = readableMessage(ex);
                }
                throw new CompletionException(ex);
            }
        }, executor);
    }

    synchronized void setSelectedMethod(String method)
    {
        selectedMethod = method;
        ensureSelectedInventoryMethod();
        rebuildSelection();
    }

    synchronized void setSelectedInventoryMethod(String method)
    {
        selectedInventoryMethod = method;
        ensureSelectedInventoryMethod();
        rebuildSelection();
    }

    synchronized void setSelectedTier(GearTier tier)
    {
        if (tier == null) return;
        selectedTier = tier;
        rebuildSelection();
    }

    WikiGearPage getPage()
    {
        return page;
    }

    String getSelectedMethod()
    {
        return selectedMethod;
    }

    String getSelectedInventoryMethod()
    {
        return selectedInventoryMethod;
    }

    GearTier getSelectedTier()
    {
        return selectedTier;
    }

    Selection getSelection()
    {
        return selection;
    }

    boolean isLoading()
    {
        return loading;
    }

    String getStatus()
    {
        return status;
    }

    List<String> getMethodNames()
    {
        WikiGearPage p = page;
        if (p == null) return Collections.emptyList();

        List<String> equipment = new ArrayList<>();
        for (WikiGearPage.GearMethod method : p.getMethods())
        {
            if (hasEquipmentRows(method)) equipment.add(method.getName());
        }
        if (!equipment.isEmpty()) return equipment;

        // Inventory-only pages (some raid/setup pages) still need a primary selector.
        List<String> fallback = new ArrayList<>();
        for (WikiGearPage.GearMethod method : p.getMethods()) fallback.add(method.getName());
        return fallback;
    }

    List<String> getInventoryMethodNames()
    {
        WikiGearPage p = page;
        if (p == null) return Collections.emptyList();

        WikiGearPage.GearMethod primary = findMethodExact(p, selectedMethod);
        if (primary == null || !hasEquipmentRows(primary)) return Collections.emptyList();

        String primaryStyle = styleSignature(primary.getName());
        List<String> specific = new ArrayList<>();
        List<String> generic = new ArrayList<>();
        for (WikiGearPage.GearMethod method : p.getMethods())
        {
            if (!isInventoryOnly(method)) continue;
            String inventoryStyle = styleSignature(method.getName());
            if (!inventoryStyle.isEmpty() && !inventoryStyle.equals(primaryStyle)) continue;
            if (inventoryStyle.isEmpty()) generic.add(method.getName());
            else specific.add(method.getName());
        }
        specific.addAll(generic);
        return specific;
    }

    private void ensureSelectedInventoryMethod()
    {
        List<String> choices = getInventoryMethodNames();
        if (choices.isEmpty())
        {
            selectedInventoryMethod = null;
            return;
        }
        if (selectedInventoryMethod != null)
        {
            for (String choice : choices)
                if (choice.equalsIgnoreCase(selectedInventoryMethod)) return;
        }
        selectedInventoryMethod = choices.get(0);
    }

    private static boolean hasEquipmentRows(WikiGearPage.GearMethod method)
    {
        if (method == null) return false;
        for (WikiGearPage.GearRow row : method.getRows())
            if (row.getSlot() != GearSlot.UNKNOWN) return true;
        return false;
    }

    private static boolean isInventoryOnly(WikiGearPage.GearMethod method)
    {
        if (method == null || method.getRows().isEmpty()) return false;
        for (WikiGearPage.GearRow row : method.getRows())
            if (row.getSlot() != GearSlot.UNKNOWN) return false;
        return true;
    }

    private static WikiGearPage.GearMethod findMethodExact(WikiGearPage p, String name)
    {
        if (p == null || name == null) return null;
        for (WikiGearPage.GearMethod method : p.getMethods())
            if (method.getName().equalsIgnoreCase(name)) return method;
        return null;
    }

    private static String styleSignature(String name)
    {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.contains("tribrid")) return "melee+ranged+magic";

        List<String> styles = new ArrayList<>();
        if (lower.contains("melee")) styles.add("melee");
        if (lower.contains("ranged") || lower.contains("range ") || lower.endsWith("range")) styles.add("ranged");
        if (lower.contains("magic") || lower.contains("mage")) styles.add("magic");
        if (!styles.isEmpty()) return String.join("+", styles);
        if (lower.contains("hybrid")) return "hybrid";
        return "";
    }

    HighlightKind classify(int itemId)
    {
        Selection s = selection;
        if (s.getPrimaryIds().contains(itemId)) return HighlightKind.PRIMARY;
        if (s.getAlternativeIds().contains(itemId)) return HighlightKind.ALTERNATIVE;
        return HighlightKind.NONE;
    }

    OwnershipMatch ownership(ResolvedGearRow row)
    {
        if (row == null) return OwnershipMatch.missing();
        OwnershipSnapshot snapshot = getOwnershipSnapshot();
        List<Integer> ids = row.getAllIds();

        for (Integer id : ids)
        {
            if (id != null && id > 0 && snapshot.equippedIds.contains(id))
                return new OwnershipMatch(Ownership.EQUIPPED, id == row.getPrimaryId());
        }
        for (Integer id : ids)
        {
            if (id != null && id > 0 && snapshot.inventoryIds.contains(id))
                return new OwnershipMatch(Ownership.INVENTORY, id == row.getPrimaryId());
        }
        for (Integer id : ids)
        {
            if (id != null && id > 0 && snapshot.bankIds.contains(id))
                return new OwnershipMatch(Ownership.BANK, id == row.getPrimaryId());
        }
        return OwnershipMatch.missing();
    }

    private OwnershipSnapshot getOwnershipSnapshot()
    {
        long now = System.currentTimeMillis();
        OwnershipSnapshot cached = ownershipSnapshot;
        if (now - ownershipSnapshotAt < OWNERSHIP_SNAPSHOT_MS) return cached;

        synchronized (this)
        {
            now = System.currentTimeMillis();
            if (now - ownershipSnapshotAt < OWNERSHIP_SNAPSHOT_MS) return ownershipSnapshot;

            Set<Integer> equipped = new HashSet<>();
            Set<Integer> inventory = new HashSet<>();
            Set<Integer> bank = new HashSet<>();

            try
            {
                Rs2Equipment.all().forEach(item -> addItemId(equipped, item));
            }
            catch (Throwable ignored)
            {
                // Keep the previous location empty if the cached equipment view is unavailable.
            }

            try
            {
                List<Rs2ItemModel> items = Rs2Inventory.all();
                if (items != null) for (Rs2ItemModel item : items) addItemId(inventory, item);
            }
            catch (Throwable ignored)
            {
                // Inventory cache can briefly invalidate during widget/container rebuilds.
            }

            try
            {
                List<Rs2ItemModel> items = Rs2Bank.bankItems();
                if (items != null) for (Rs2ItemModel item : items) addItemId(bank, item);
            }
            catch (Throwable ignored)
            {
                // Bank data is cached by Microbot and can be temporarily unavailable during refresh.
            }

            OwnershipSnapshot fresh = new OwnershipSnapshot(equipped, inventory, bank);
            ownershipSnapshot = fresh;
            ownershipSnapshotAt = now;
            return fresh;
        }
    }

    private static void addItemId(Set<Integer> target, Rs2ItemModel item)
    {
        if (item != null && item.getId() > 0) target.add(item.getId());
    }

    synchronized void shutdown()
    {
        requestSequence.incrementAndGet();
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        loading = false;
    }

    private static ExecutorService newExecutor()
    {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ksp-boss-gear-wiki");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void resolvePageItemIds(WikiGearPage loaded)
    {
        Set<String> names = new LinkedHashSet<>();
        for (WikiGearPage.GearMethod method : loaded.getMethods())
        {
            for (WikiGearPage.GearRow row : method.getRows())
            {
                for (List<String> column : row.getColumns()) names.addAll(column);
            }
        }

        for (String name : names)
        {
            String key = normalize(name);
            Integer cached = itemIdCache.get(key);
            if (cached != null && cached > 0) continue;

            int id = resolveExactItemId(name);
            // Do not cache failed lookups: the client-backed item manager may still be warming up.
            if (id > 0) itemIdCache.put(key, id);
        }
    }

    private int resolveExactItemId(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty()) return -1;
        String candidate = itemName.trim();

        try
        {
            int id = Rs2ItemManager.getItemIdByName(candidate, false);
            if (id > 0) return id;
        }
        catch (Throwable ignored)
        {
            // Fall through to the client-backed item manager used by other KSP plugins.
        }

        try
        {
            int id = Microbot.getRs2ItemManager().getItemId(candidate);
            if (id > 0) return id;
        }
        catch (Throwable ignored)
        {
            // Return unresolved below.
        }
        return -1;
    }

    private synchronized void rebuildSelection()
    {
        WikiGearPage p = page;
        if (p == null)
        {
            selection = Selection.empty();
            return;
        }

        WikiGearPage.GearMethod method = findMethodExact(p, selectedMethod);
        if (method == null)
        {
            selection = Selection.empty();
            return;
        }
        selectedMethod = method.getName();
        ensureSelectedInventoryMethod();

        List<WikiGearPage.GearMethod> activeMethods = new ArrayList<>();
        activeMethods.add(method);
        if (hasEquipmentRows(method) && selectedInventoryMethod != null)
        {
            WikiGearPage.GearMethod inventory = findMethodExact(p, selectedInventoryMethod);
            if (inventory != null && isInventoryOnly(inventory)) activeMethods.add(inventory);
        }

        List<ResolvedGearRow> rows = new ArrayList<>();
        Set<Integer> primary = new LinkedHashSet<>();
        Set<Integer> alternatives = new LinkedHashSet<>();

        for (WikiGearPage.GearMethod activeMethod : activeMethods)
        {
            for (WikiGearPage.GearRow sourceRow : activeMethod.getRows())
            {
                List<String> names = chooseTierColumn(sourceRow.getColumns(), selectedTier);
                List<ItemRef> items = resolveCandidates(names);
                if (items.isEmpty()) continue;

                ItemRef first = items.get(0);
                List<ItemRef> alts = items.size() > 1
                    ? new ArrayList<>(items.subList(1, items.size()))
                    : Collections.emptyList();
                ResolvedGearRow resolved = new ResolvedGearRow(sourceRow.getSlot(), first, alts);
                rows.add(resolved);
                primary.add(first.id);
                for (ItemRef alt : alts) alternatives.add(alt.id);
            }
        }

        alternatives.removeAll(primary);
        selection = new Selection(
            p.getBossName(),
            method.getName(),
            selectedTier,
            rows,
            primary,
            alternatives,
            p.getSourceUrl());
    }

    private List<ItemRef> resolveCandidates(List<String> names)
    {
        List<ItemRef> result = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (String name : names)
        {
            int id = itemIdCache.getOrDefault(normalize(name), -1);
            if (id > 0 && seen.add(id)) result.add(new ItemRef(name, id));
        }
        return result;
    }

    private List<String> chooseTierColumn(List<List<String>> columns, GearTier tier)
    {
        if (columns == null || columns.isEmpty()) return Collections.emptyList();

        int last = columns.size() - 1;
        int wanted;
        switch (tier)
        {
            case MAX:
                wanted = 0;
                break;
            case HIGH:
                wanted = (int) Math.round(last * 0.34d);
                break;
            case MID:
                wanted = (int) Math.round(last * 0.67d);
                break;
            case BUDGET:
            default:
                wanted = last;
                break;
        }

        if (hasResolvedItem(columns.get(wanted))) return columns.get(wanted);

        if (tier == GearTier.MAX)
        {
            for (int i = wanted + 1; i <= last; i++) if (hasResolvedItem(columns.get(i))) return columns.get(i);
        }
        else if (tier == GearTier.BUDGET)
        {
            for (int i = wanted - 1; i >= 0; i--) if (hasResolvedItem(columns.get(i))) return columns.get(i);
        }
        else
        {
            for (int distance = 1; distance <= last; distance++)
            {
                int right = wanted + distance;
                int left = wanted - distance;
                if (right <= last && hasResolvedItem(columns.get(right))) return columns.get(right);
                if (left >= 0 && hasResolvedItem(columns.get(left))) return columns.get(left);
            }
        }
        return Collections.emptyList();
    }

    private boolean hasResolvedItem(List<String> names)
    {
        for (String name : names)
        {
            if (itemIdCache.getOrDefault(normalize(name), -1) > 0) return true;
        }
        return false;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String readableMessage(Throwable ex)
    {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
            ? "Could not load gear/inventory from the OSRS Wiki."
            : message;
    }

    private static final class OwnershipSnapshot
    {
        private final Set<Integer> equippedIds;
        private final Set<Integer> inventoryIds;
        private final Set<Integer> bankIds;

        private OwnershipSnapshot(Set<Integer> equippedIds, Set<Integer> inventoryIds, Set<Integer> bankIds)
        {
            this.equippedIds = equippedIds;
            this.inventoryIds = inventoryIds;
            this.bankIds = bankIds;
        }

        private static OwnershipSnapshot empty()
        {
            return new OwnershipSnapshot(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        }
    }

    enum HighlightKind
    {
        NONE,
        PRIMARY,
        ALTERNATIVE
    }

    enum Ownership
    {
        EQUIPPED("Equipped", new Color(80, 220, 120)),
        INVENTORY("Inventory", new Color(90, 190, 255)),
        BANK("Bank", new Color(245, 190, 70)),
        MISSING("Missing", new Color(245, 105, 105));

        private final String displayName;
        private final Color color;

        Ownership(String displayName, Color color)
        {
            this.displayName = displayName;
            this.color = color;
        }

        String getDisplayName()
        {
            return displayName;
        }

        Color getColor()
        {
            return color;
        }
    }

    static final class OwnershipMatch
    {
        private final Ownership ownership;
        private final boolean primary;

        private OwnershipMatch(Ownership ownership, boolean primary)
        {
            this.ownership = ownership;
            this.primary = primary;
        }

        static OwnershipMatch missing()
        {
            return new OwnershipMatch(Ownership.MISSING, false);
        }

        Ownership getOwnership()
        {
            return ownership;
        }

        boolean isPrimary()
        {
            return primary;
        }

        String displayText()
        {
            return ownership.getDisplayName() + (ownership != Ownership.MISSING && !primary ? " (alt)" : "");
        }
    }

    static final class Selection
    {
        private final String bossName;
        private final String methodName;
        private final GearTier tier;
        private final List<ResolvedGearRow> rows;
        private final Set<Integer> primaryIds;
        private final Set<Integer> alternativeIds;
        private final String sourceUrl;

        private Selection(
            String bossName,
            String methodName,
            GearTier tier,
            List<ResolvedGearRow> rows,
            Set<Integer> primaryIds,
            Set<Integer> alternativeIds,
            String sourceUrl)
        {
            this.bossName = bossName;
            this.methodName = methodName;
            this.tier = tier;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.primaryIds = Collections.unmodifiableSet(new LinkedHashSet<>(primaryIds));
            this.alternativeIds = Collections.unmodifiableSet(new LinkedHashSet<>(alternativeIds));
            this.sourceUrl = sourceUrl;
        }

        static Selection empty()
        {
            return new Selection("", "", GearTier.MID, Collections.emptyList(),
                Collections.emptySet(), Collections.emptySet(), "");
        }

        String getBossName() { return bossName; }
        String getMethodName() { return methodName; }
        GearTier getTier() { return tier; }
        List<ResolvedGearRow> getRows() { return rows; }
        Set<Integer> getPrimaryIds() { return primaryIds; }
        Set<Integer> getAlternativeIds() { return alternativeIds; }
        String getSourceUrl() { return sourceUrl; }
    }

    static final class ResolvedGearRow
    {
        private final GearSlot slot;
        private final ItemRef primary;
        private final List<ItemRef> alternatives;

        private ResolvedGearRow(GearSlot slot, ItemRef primary, List<ItemRef> alternatives)
        {
            this.slot = slot;
            this.primary = primary;
            this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
        }

        GearSlot getSlot() { return slot; }
        String getPrimaryName() { return primary.name; }
        int getPrimaryId() { return primary.id; }

        List<String> getAlternativeNames()
        {
            List<String> result = new ArrayList<>();
            for (ItemRef item : alternatives) result.add(item.name);
            return result;
        }

        List<Integer> getAllIds()
        {
            List<Integer> result = new ArrayList<>();
            result.add(primary.id);
            for (ItemRef item : alternatives) result.add(item.id);
            return result;
        }

        String tooltip()
        {
            if (alternatives.isEmpty()) return primary.name;
            StringBuilder sb = new StringBuilder(primary.name).append(" | Alternatives: ");
            for (int i = 0; i < alternatives.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(alternatives.get(i).name);
            }
            return sb.toString();
        }
    }

    private static final class ItemRef
    {
        private final String name;
        private final int id;

        private ItemRef(String name, int id)
        {
            this.name = name;
            this.id = id;
        }
    }
}
