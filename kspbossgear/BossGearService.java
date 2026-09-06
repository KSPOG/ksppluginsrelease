package net.runelite.client.plugins.microbot.kspbossgear;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
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
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;

/** Owns the selected Wiki loadout and exposes thread-safe highlight state. */
final class BossGearService
{
    private final WikiGearService wikiGearService;
    private volatile ExecutorService executor;
    private final Map<String, Integer> itemIdCache = new ConcurrentHashMap<>();
    private final AtomicLong requestSequence = new AtomicLong();

    private volatile WikiGearPage page;
    private volatile String selectedMethod;
    private volatile GearTier selectedTier = GearTier.MID;
    private volatile Selection selection = Selection.empty();
    private volatile boolean loading;
    private volatile String status = "Search for a boss to load Wiki equipment.";

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
        status = "Loading OSRS Wiki equipment...";

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
                        selectedMethod = loaded.getMethods().isEmpty() ? null : loaded.getMethods().get(0).getName();
                        rebuildSelection();
                        loading = false;
                        status = selection.getRows().isEmpty()
                            ? "Wiki page loaded, but no resolvable equipment items were found."
                            : "Live equipment loaded from the OSRS Wiki.";
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
        List<String> result = new ArrayList<>();
        for (WikiGearPage.GearMethod method : p.getMethods()) result.add(method.getName());
        return result;
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
        List<Integer> ids = row.getAllIds();

        for (Integer id : ids)
        {
            if (id != null && id > 0 && Rs2Equipment.isWearing(id))
                return new OwnershipMatch(Ownership.EQUIPPED, id == row.getPrimaryId());
        }
        for (Integer id : ids)
        {
            if (id != null && id > 0 && Rs2Inventory.hasItem(id))
                return new OwnershipMatch(Ownership.INVENTORY, id == row.getPrimaryId());
        }
        for (Integer id : ids)
        {
            if (id != null && id > 0 && Rs2Bank.hasItem(id))
                return new OwnershipMatch(Ownership.BANK, id == row.getPrimaryId());
        }
        return OwnershipMatch.missing();
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
            itemIdCache.computeIfAbsent(normalize(name), key -> resolveExactItemId(name));
        }
    }

    private int resolveExactItemId(String itemName)
    {
        try
        {
            return Rs2ItemManager.getItemIdByName(itemName, false);
        }
        catch (Throwable ignored)
        {
            return -1;
        }
    }

    private synchronized void rebuildSelection()
    {
        WikiGearPage p = page;
        if (p == null)
        {
            selection = Selection.empty();
            return;
        }

        WikiGearPage.GearMethod method = p.findMethod(selectedMethod);
        if (method == null)
        {
            selection = Selection.empty();
            return;
        }
        selectedMethod = method.getName();

        List<ResolvedGearRow> rows = new ArrayList<>();
        Set<Integer> primary = new LinkedHashSet<>();
        Set<Integer> alternatives = new LinkedHashSet<>();

        for (WikiGearPage.GearRow sourceRow : method.getRows())
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
            ? "Could not load equipment from the OSRS Wiki."
            : message;
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
