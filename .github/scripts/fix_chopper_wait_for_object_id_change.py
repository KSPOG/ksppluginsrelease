from pathlib import Path

PATH = Path('kspwillowchopper/KspWillowChopperScript.java')
text = PATH.read_text(encoding='utf-8')

# Inventory changes are statistics only; they must never release the current tree target.
for line in [
    'import net.runelite.api.Item;\n',
    'import net.runelite.api.ItemContainer;\n',
    'import net.runelite.api.events.ItemContainerChanged;\n',
    'import net.runelite.api.InventoryID;\n',
]:
    text = text.replace(line, '')

old_doc = ''' * Chopping is event driven in two ways:\n *  - when the currently clicked tree changes id/despawns;\n *  - when the selected resource count increases in the player inventory.\n * Either signal immediately selects another valid tree and bypasses the normal\n * animation/retry suppression.\n'''
new_doc = ''' * Chopping is object driven. After a tree is clicked, that exact object remains\n * locked as the active target until its object id changes/despawns/replaces.\n * Inventory gains, animation changes and retry timers never select another tree\n * while the active tree object is still unchanged.\n'''
if old_doc not in text:
    raise RuntimeError('Chopping documentation block not found')
text = text.replace(old_doc, new_doc, 1)

text = text.replace('    private volatile int lastInventoryEventResourceCount = -1;\n', '')

# Remove all obsolete inventory-event baseline assignments.
lines = []
for line in text.splitlines(True):
    if 'lastInventoryEventResourceCount =' in line:
        continue
    lines.append(line)
text = ''.join(lines)

old_handler = '''    @Subscribe\n    public void onItemContainerChanged(ItemContainerChanged event) {\n        if (!sessionStarted || event == null) return;\n\n        ItemContainer container = event.getItemContainer();\n        if (container == null || event.getContainerId() != InventoryID.INVENTORY.getId()) {\n            return;\n        }\n\n        int current = countItem(container, activeTree.getResourceId());\n        int previous = lastInventoryEventResourceCount;\n\n        if (previous < 0 || current <= previous || state != RuntimeState.CHOPPING) {\n            return;\n        }\n\n        // A selected log/resource was added to inventory. Immediately rotate to\n        // another tree, bypassing the old chopping animation and retry timer.\n        WorldPoint previousTree = activeTreeObjectLocation;\n        requestImmediateRetarget(previousTree, "Inventory changed");\n    }\n\n    private int countItem(ItemContainer container, int itemId) {\n        int total = 0;\n        Item[] items = container.getItems();\n        if (items == null) return 0;\n        for (Item item : items) {\n            if (item != null && item.getId() == itemId) {\n                total += Math.max(1, item.getQuantity());\n            }\n        }\n        return total;\n    }\n\n'''
if old_handler not in text:
    raise RuntimeError('Inventory retarget handler block not found')
text = text.replace(old_handler, '', 1)

old_chop = '''    private void chopSelectedTree(boolean force, WorldPoint avoidLocation) {\n        if (Rs2Bank.isOpen()) {\n            Rs2Bank.closeBank();\n            return;\n        }\n\n        if (!force && isPlayerBusy()) {\n            status = "Chopping " + activeTree;\n            return;\n        }\n'''
new_chop = '''    private void chopSelectedTree(boolean force, WorldPoint avoidLocation) {\n        if (Rs2Bank.isOpen()) {\n            Rs2Bank.closeBank();\n            return;\n        }\n\n        // The active tree object is the authoritative interaction lock. Once a\n        // click succeeds, never click this or any other tree until RuneLite tells\n        // us that exact object changed id/despawned/replaced. This prevents log\n        // inventory gains, animation gaps and retry timers from double-clicking.\n        if (activeTreeObjectLocation != null) {\n            status = "Chopping " + activeTree + " - waiting for object ID change";\n            return;\n        }\n\n        if (!force && isPlayerBusy()) {\n            status = "Chopping " + activeTree;\n            return;\n        }\n'''
if old_chop not in text:
    raise RuntimeError('chopSelectedTree header not found')
text = text.replace(old_chop, new_chop, 1)

# Validate there is no remaining inventory event based retarget path.
for forbidden in ['ItemContainerChanged', 'InventoryID.INVENTORY', 'lastInventoryEventResourceCount', '"Inventory changed"']:
    if forbidden in text:
        raise RuntimeError(f'Forbidden inventory-retarget residue remains: {forbidden}')

if 'waiting for object ID change' not in text:
    raise RuntimeError('Tree interaction lock was not inserted')

PATH.write_text(text, encoding='utf-8')

# Self-clean temporary patch artifacts so the PR only contains Chopper source.
Path('.github/workflows/fix-chopper-wait-for-object-id-change.yml').unlink(missing_ok=True)
Path('.github/scripts/fix_chopper_wait_for_object_id_change.py').unlink(missing_ok=True)
