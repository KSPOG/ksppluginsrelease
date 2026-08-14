package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/** Plans, executes and verifies one complete bank organization pass. */
@Slf4j
final class BankOrganizerEngine
{
    private final BankSnapshotReader snapshotReader;
    private final BankActuator actuator;
    private final SmartSorter sorter;
    private final AutoCategorizer categorizer = new AutoCategorizer();

    private volatile String phase = "Idle";
    private volatile OperationMode activeMode = OperationMode.PREVIEW;
    private volatile String lastMessage = "";
    private volatile int plannedCount;
    private volatile int movedCount;
    private volatile int sortedCount;
    private volatile int misplacedCount;
    private volatile BankSnapshot latestSnapshot;
    private volatile Map<Integer, ItemCategory> categoriesById = Collections.emptyMap();
    private volatile Map<Integer, Integer> targetsById = Collections.emptyMap();

    @Inject
    BankOrganizerEngine(BankSnapshotReader snapshotReader, BankActuator actuator, SmartSorter sorter)
    {
        this.snapshotReader = snapshotReader;
        this.actuator = actuator;
        this.sorter = sorter;
    }

    RunResult run(KspBankOrganizerConfig config)
    {
        return run(config, config.operationMode());
    }

    RunResult run(KspBankOrganizerConfig config, OperationMode mode)
    {
        reset(mode);
        try
        {
            setPhase("Opening bank");
            if (!actuator.ensureBankOpen())
            {
                return fail("Could not open the nearest bank.");
            }

            categorizer.configure(config);
            BankSnapshot initial = readSnapshot();
            List<PlannedItem> plan = buildPlan(initial, config);
            plannedCount = plan.size();
            misplacedCount = countMisplaced(initial, plan);

            if (mode == OperationMode.PREVIEW)
            {
                setPhase("Preview complete");
                lastMessage = "Previewed " + plannedCount + " bank stacks; " + misplacedCount + " are outside their mapped destination.";
                return RunResult.ok(lastMessage, movedCount, sortedCount);
            }

            BankActuator.ActuatorResult insertMode = actuator.ensureBankInsertMode();
            if (!insertMode.success())
            {
                return fail(insertMode.message());
            }

            int baselineCount = initial.stackCount();
            Map<Integer, Integer> baselineQuantities = quantityMap(initial);

            setPhase("Preparing bank rearrangement");
            BankActuator.ActuatorResult swapMode = actuator.ensureBankSwapMode();
            if (!swapMode.success())
            {
                return fail(swapMode.message());
            }

            setPhase("Moving categories");
            String movementError = executeCategoryMoves(plan, config, baselineCount, baselineQuantities);
            if (movementError != null)
            {
                return fail(movementError);
            }

            if (config.sortWithinTabs())
            {
                BankActuator.ActuatorResult insertMode = actuator.ensureBankInsertMode();
                if (!insertMode.success())
                {
                    return fail(insertMode.message());
                }

                setPhase("Smart sorting");
                String sortError = sortConfiguredTabs(plan, config, baselineCount, baselineQuantities);
                if (sortError != null)
                {
                    return fail(sortError);
                }
            }

            BankSnapshot finalSnapshot = readSnapshot();
            String finalVerification = verifyUnchanged(baselineCount, baselineQuantities, finalSnapshot);
            if (finalVerification != null)
            {
                return fail("Final bank verification failed: " + finalVerification);
            }

            misplacedCount = countMisplaced(finalSnapshot, plan);
            if (config.closeBankWhenFinished())
            {
                Rs2Bank.closeBank();
            }

            setPhase("Complete");
            lastMessage = "Organized bank: moved " + movedCount + " stacks and sorted " + sortedCount
                + " positions. Bank quantities verified.";
            return RunResult.ok(lastMessage, movedCount, sortedCount);
        }
        catch (Throwable t)
        {
            log.warn("KSP Bank Organizer execution failed", t);
            return fail(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
    }

    private List<PlannedItem> buildPlan(BankSnapshot snapshot, KspBankOrganizerConfig config)
    {
        List<PlannedItem> raw = new ArrayList<>();
        Map<Integer, ItemCategory> categories = new HashMap<>();

        // First classify everything without assigning physical tab numbers.
        Map<ItemCategory, Integer> categoryConfiguredTarget = new EnumMap<>(ItemCategory.class);
        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            ItemCategory category = categorizer.categorize(stack);
            int configuredTarget = targetFor(category, config);
            categoryConfiguredTarget.putIfAbsent(category, configuredTarget);
            categories.put(stack.itemId(), category);
            raw.add(new PlannedItem(stack.itemId(), stack.name(), category, configuredTarget));
        }

        // OSRS cannot create an empty tab gap. Treat configured tab numbers as
        // category ordering priorities and compact only populated categories
        // into sequential physical tabs. Main/Ignore remain fixed.
        List<ItemCategory> populatedTabCategories = new ArrayList<>();
        for (ItemCategory category : ItemCategory.values())
        {
            int target = categoryConfiguredTarget.getOrDefault(category, -1);
            if (target > 0)
            {
                populatedTabCategories.add(category);
            }
        }
        populatedTabCategories.sort(Comparator
            .comparingInt((ItemCategory c) -> categoryConfiguredTarget.getOrDefault(c, Integer.MAX_VALUE))
            .thenComparingInt(Enum::ordinal));

        Map<ItemCategory, Integer> effectiveTargets = new EnumMap<>(ItemCategory.class);
        int nextPhysicalTab = 1;
        for (ItemCategory category : populatedTabCategories)
        {
            // Only categories actually present in the bank need a physical tab.
            boolean populated = false;
            for (PlannedItem item : raw)
            {
                if (item.category() == category)
                {
                    populated = true;
                    break;
                }
            }
            if (populated)
            {
                effectiveTargets.put(category, nextPhysicalTab++);
            }
        }

        List<PlannedItem> plan = new ArrayList<>(raw.size());
        Map<Integer, Integer> targets = new HashMap<>();
        for (PlannedItem item : raw)
        {
            int configuredTarget = item.targetTab();
            int effectiveTarget;
            if (configuredTarget < 0)
            {
                effectiveTarget = -1;
            }
            else if (configuredTarget == 0)
            {
                effectiveTarget = 0;
            }
            else
            {
                effectiveTarget = effectiveTargets.getOrDefault(item.category(), configuredTarget);
            }

            PlannedItem resolved = new PlannedItem(
                item.itemId(), item.name(), item.category(), effectiveTarget);
            plan.add(resolved);
            targets.put(item.itemId(), effectiveTarget);
        }

        categoriesById = Collections.unmodifiableMap(categories);
        targetsById = Collections.unmodifiableMap(targets);
        return plan;
    }

    private String executeCategoryMoves(
        List<PlannedItem> plan,
        KspBankOrganizerConfig config,
        int baselineCount,
        Map<Integer, Integer> baselineQuantities)
    {
        List<PlannedItem> ordered = new ArrayList<>(plan);
        ordered.sort(Comparator
            .comparingInt(PlannedItem::targetTab)
            .thenComparing(sorter.comparator(config)));

        for (PlannedItem planned : ordered)
        {
            if (Thread.currentThread().isInterrupted())
            {
                return "Organizer interrupted.";
            }
            int target = planned.targetTab();
            if (target < 0)
            {
                continue;
            }

            BankSnapshot snapshot = readSnapshot();
            BankSnapshot.BankStack current = stackByItemId(snapshot, planned.itemId());
            if (current == null)
            {
                return "Could not find " + planned.name() + " (" + planned.itemId() + ") before moving it.";
            }
            if (current.tab() == target)
            {
                continue;
            }

            if (wouldCollapseSourceTab(current.tab()))
            {
                return "Safety stop: moving " + planned.name() + " would empty bank tab " + current.tab()
                    + " and shift later tab indices. Add another stack to that tab, remap it, or move it manually first.";
            }

            BankActuator.ActuatorResult move;
            if (target == 0)
            {
                move = actuator.moveToMain(planned.itemId(), current.tab());
            }
            else if (actuator.tabCount(target) > 0)
            {
                move = actuator.moveToExistingTab(planned.itemId(), current.tab(), target);
            }
            else
            {
                if (!config.createMissingTabs())
                {
                    return "Destination tab " + target + " does not exist and Create missing tabs is disabled.";
                }
                int appendable = actuator.realTabCount() + 1;
                if (target != appendable)
                {
                    return "Cannot create destination tab " + target + " yet; the next appendable bank tab is " + appendable
                        + ". Empty destination gaps cannot be created in OSRS.";
                }
                move = actuator.moveToNewTab(planned.itemId(), current.tab());
            }

            if (!move.success())
            {
                return planned.name() + ": " + move.message();
            }
            movedCount++;

            BankSnapshot after = readSnapshot();
            if (config.strictVerification())
            {
                String verification = verifyUnchanged(baselineCount, baselineQuantities, after);
                if (verification != null)
                {
                    return "After moving " + planned.name() + ": " + verification;
                }
            }
            misplacedCount = countMisplaced(after, plan);
        }
        return null;
    }

    private String sortConfiguredTabs(
        List<PlannedItem> plan,
        KspBankOrganizerConfig config,
        int baselineCount,
        Map<Integer, Integer> baselineQuantities)
    {
        Set<Integer> targetTabs = new HashSet<>();
        for (PlannedItem item : plan)
        {
            if (item.targetTab() > 0)
            {
                targetTabs.add(item.targetTab());
            }
        }
        List<Integer> tabs = new ArrayList<>(targetTabs);
        Collections.sort(tabs);

        for (int tab : tabs)
        {
            if (actuator.tabCount(tab) <= 0)
            {
                continue;
            }
            BankActuator.ActuatorResult opened = actuator.openTab(tab);
            if (!opened.success())
            {
                return opened.message();
            }

            for (int targetPosition = 0; ; targetPosition++)
            {
                if (Thread.currentThread().isInterrupted())
                {
                    return "Organizer interrupted while sorting.";
                }

                BankSnapshot snapshot = readSnapshot();
                List<BankSnapshot.BankStack> stacks = tabStacks(snapshot, tab);
                List<PlannedItem> desired = desiredPresent(plan, stacks, tab, config);
                if (targetPosition >= desired.size())
                {
                    break;
                }
                if (targetPosition >= stacks.size())
                {
                    return "Tab " + tab + " has fewer stacks than expected during sorting.";
                }

                int desiredId = desired.get(targetPosition).itemId();
                if (stacks.get(targetPosition).itemId() == desiredId)
                {
                    continue;
                }

                int sourcePosition = indexOf(stacks, desiredId);
                if (sourcePosition < 0)
                {
                    return "Could not find item " + desiredId + " while sorting tab " + tab + ".";
                }
                if (sourcePosition < targetPosition)
                {
                    return "Sorting prefix drifted in tab " + tab + ".";
                }

                BankSnapshot.BankStack source = stacks.get(sourcePosition);
                BankSnapshot.BankStack target = stacks.get(targetPosition);
                BankActuator.ActuatorResult drag = actuator.moveWithinOpenTab(source, target);
                if (!drag.success())
                {
                    return "Tab " + tab + ": " + drag.message();
                }
                sortedCount++;

                final int prefixLength = targetPosition + 1;
                boolean verified = net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> {
                    BankSnapshot after = safeReadSnapshot();
                    return after != null
                        && verifyUnchanged(baselineCount, baselineQuantities, after) == null
                        && isOrderedPrefix(after, plan, tab, config, prefixLength);
                }, 5000);
                if (!verified)
                {
                    return "Tab " + tab + " order was not verified after moving " + source.name() + ".";
                }
            }
        }
        return null;
    }

    private List<PlannedItem> desiredPresent(
        List<PlannedItem> plan,
        List<BankSnapshot.BankStack> currentStacks,
        int tab,
        KspBankOrganizerConfig config)
    {
        Set<Integer> presentIds = new HashSet<>();
        for (BankSnapshot.BankStack stack : currentStacks)
        {
            presentIds.add(stack.itemId());
        }

        List<PlannedItem> desired = new ArrayList<>();
        for (PlannedItem item : plan)
        {
            if (item.targetTab() == tab && presentIds.contains(item.itemId()))
            {
                desired.add(item);
            }
        }
        desired.sort(sorter.comparator(config));
        return desired;
    }

    private boolean isOrderedPrefix(
        BankSnapshot snapshot,
        List<PlannedItem> plan,
        int tab,
        KspBankOrganizerConfig config,
        int prefixLength)
    {
        List<BankSnapshot.BankStack> stacks = tabStacks(snapshot, tab);
        List<PlannedItem> desired = desiredPresent(plan, stacks, tab, config);
        if (stacks.size() < prefixLength || desired.size() < prefixLength)
        {
            return false;
        }
        for (int i = 0; i < prefixLength; i++)
        {
            if (stacks.get(i).itemId() != desired.get(i).itemId())
            {
                return false;
            }
        }
        return true;
    }

    private boolean wouldCollapseSourceTab(int sourceTab)
    {
        return sourceTab > 0 && actuator.tabCount(sourceTab) <= 1;
    }

    private BankSnapshot readSnapshot()
    {
        latestSnapshot = snapshotReader.read();
        return latestSnapshot;
    }

    private BankSnapshot safeReadSnapshot()
    {
        try
        {
            return readSnapshot();
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static List<BankSnapshot.BankStack> tabStacks(BankSnapshot snapshot, int tab)
    {
        List<BankSnapshot.BankStack> result = new ArrayList<>();
        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            if (stack.tab() == tab)
            {
                result.add(stack);
            }
        }
        result.sort(Comparator.comparingInt(BankSnapshot.BankStack::allItemsIndex));
        return result;
    }

    private static int indexOf(List<BankSnapshot.BankStack> stacks, int itemId)
    {
        for (int i = 0; i < stacks.size(); i++)
        {
            if (stacks.get(i).itemId() == itemId)
            {
                return i;
            }
        }
        return -1;
    }

    private static BankSnapshot.BankStack stackByItemId(BankSnapshot snapshot, int itemId)
    {
        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            if (stack.itemId() == itemId)
            {
                return stack;
            }
        }
        return null;
    }

    private int countMisplaced(BankSnapshot snapshot, List<PlannedItem> plan)
    {
        Map<Integer, Integer> targets = new HashMap<>();
        for (PlannedItem item : plan)
        {
            targets.put(item.itemId(), item.targetTab());
        }
        int count = 0;
        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            Integer target = targets.get(stack.itemId());
            if (target != null && target >= 0 && stack.tab() != target)
            {
                count++;
            }
        }
        return count;
    }

    private static Map<Integer, Integer> quantityMap(BankSnapshot snapshot)
    {
        Map<Integer, Integer> quantities = new HashMap<>();
        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            quantities.merge(stack.itemId(), stack.quantity(), Integer::sum);
        }
        return quantities;
    }

    private static String verifyUnchanged(int baselineCount, Map<Integer, Integer> baselineQuantities, BankSnapshot snapshot)
    {
        if (snapshot.stackCount() != baselineCount)
        {
            return "bank stack count changed from " + baselineCount + " to " + snapshot.stackCount() + ".";
        }
        Map<Integer, Integer> current = quantityMap(snapshot);
        if (!current.equals(baselineQuantities))
        {
            return "one or more item quantities changed during organization.";
        }
        return null;
    }

    private static int targetFor(ItemCategory category, KspBankOrganizerConfig config)
    {
        BankTarget target;
        switch (category)
        {
            case TELEPORTS: target = config.teleportsTarget(); break;
            case GEAR: target = config.gearTarget(); break;
            case POTIONS: target = config.potionsTarget(); break;
            case FOOD: target = config.foodTarget(); break;
            case SKILLING: target = config.skillingTarget(); break;
            case RAW_MATERIALS: target = config.materialsTarget(); break;
            case HIGH_ALCH: target = config.highAlchTarget(); break;
            case CURRENCY: target = config.currencyTarget(); break;
            case QUEST_MISC:
            default: target = config.questMiscTarget(); break;
        }
        return target.getTabIndex();
    }

    private void reset(OperationMode mode)
    {
        activeMode = mode == null ? OperationMode.PREVIEW : mode;
        phase = "Starting";
        lastMessage = "";
        plannedCount = 0;
        movedCount = 0;
        sortedCount = 0;
        misplacedCount = 0;
        latestSnapshot = null;
        categoriesById = Collections.emptyMap();
        targetsById = Collections.emptyMap();
    }

    private void setPhase(String value)
    {
        phase = value;
        Microbot.status = "Bank Organizer: " + value;
    }

    private RunResult fail(String message)
    {
        phase = "Stopped";
        lastMessage = message == null || message.isEmpty() ? "Unknown organizer error." : message;
        Microbot.status = "Bank Organizer: " + lastMessage;
        return RunResult.fail(lastMessage, movedCount, sortedCount);
    }

    String phase() { return phase; }
    OperationMode activeMode() { return activeMode; }
    String lastMessage() { return lastMessage; }
    int plannedCount() { return plannedCount; }
    int movedCount() { return movedCount; }
    int sortedCount() { return sortedCount; }
    int misplacedCount() { return misplacedCount; }
    BankSnapshot latestSnapshot() { return latestSnapshot; }
    Map<Integer, ItemCategory> categoriesById() { return categoriesById; }
    Map<Integer, Integer> targetsById() { return targetsById; }

    void markStoppedByUser()
    {
        phase = "Stopped";
        lastMessage = "Stopped by user.";
        Microbot.status = "Bank Organizer: Stopped by user.";
    }

    static final class RunResult
    {
        private final boolean success;
        private final String message;
        private final int moved;
        private final int sorted;

        private RunResult(boolean success, String message, int moved, int sorted)
        {
            this.success = success;
            this.message = message;
            this.moved = moved;
            this.sorted = sorted;
        }

        static RunResult ok(String message, int moved, int sorted) { return new RunResult(true, message, moved, sorted); }
        static RunResult fail(String message, int moved, int sorted) { return new RunResult(false, message, moved, sorted); }
        boolean success() { return success; }
        String message() { return message; }
        int moved() { return moved; }
        int sorted() { return sorted; }
    }
}
