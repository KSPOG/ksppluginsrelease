from pathlib import Path

PATH = Path('kspwillowchopper/KspWillowChopperScript.java')
text = PATH.read_text(encoding='utf-8')

old = '''        // The active tree object is the authoritative interaction lock. Once a\n        // click succeeds, never click this or any other tree until RuneLite tells\n        // us that exact object changed id/despawned/replaced. This prevents log\n        // inventory gains, animation gaps and retry timers from double-clicking.\n        if (activeTreeObjectLocation != null) {\n            status = "Chopping " + activeTree + " - waiting for object ID change";\n            return;\n        }\n\n        if (!force && isPlayerBusy()) {\n'''
new = '''        // The active tree object is the authoritative interaction lock. Never\n        // switch to another tree while this exact object id/tile is still alive.\n        // If RuneScape drops the interaction without changing the object, retry\n        // only this same tree after a short idle grace period.\n        if (activeTreeObjectLocation != null) {\n            if (isPlayerBusy()) {\n                status = "Chopping " + activeTree + " - waiting for object ID change";\n                return;\n            }\n\n            long lockedNow = System.currentTimeMillis();\n            boolean recentLockedClick = lastTreeClickMillis > 0L\n                    && lockedNow - lastTreeClickMillis < 1_500L;\n            boolean recentLockedProgress = lastTreeProgressMillis > 0L\n                    && lockedNow - lastTreeProgressMillis < 1_500L;\n            if (recentLockedClick || recentLockedProgress) {\n                status = "Chopping " + activeTree + " - waiting for object ID change";\n                return;\n            }\n\n            Rs2TileObjectModel lockedTree = findActiveTreeObject();\n            if (lockedTree == null) {\n                // Do not select a different tree merely because a cache lookup\n                // missed one tick. The despawn/spawn event is authoritative for\n                // releasing this lock.\n                status = "Waiting for active " + activeTree + " object update";\n                return;\n            }\n\n            status = "Woodcutting stopped - retrying same " + activeTree;\n            if (lockedTree.click(activeTree.getAction())) {\n                rememberActiveTreeTarget(lockedTree);\n                lastTreeClickMillis = lockedNow;\n                status = "Chopping " + activeTree + " - waiting for object ID change";\n            } else {\n                status = "Same " + activeTree + " retry failed - waiting";\n            }\n            return;\n        }\n\n        if (!force && isPlayerBusy()) {\n'''
if old not in text:
    raise RuntimeError('active-tree lock block not found')
text = text.replace(old, new, 1)

marker = '''    private void rememberActiveTreeTarget(Rs2TileObjectModel tree) {\n'''
helper = '''    private Rs2TileObjectModel findActiveTreeObject() {\n        WorldPoint location = activeTreeObjectLocation;\n        int objectId = activeTreeObjectId;\n        if (location == null || objectId < 0) return null;\n\n        return Microbot.getRs2TileObjectCache()\n                .query()\n                .where(object -> object != null\n                        && object.getId() == objectId\n                        && location.equals(object.getWorldLocation())\n                        && KspTileObjectSupport.hasAction(object, activeTree.getAction()))\n                .nearestOnClientThread();\n    }\n\n'''
if marker not in text:
    raise RuntimeError('rememberActiveTreeTarget marker not found')
text = text.replace(marker, helper + marker, 1)

old_doc = ''' * Chopping is object driven. After a tree is clicked, that exact object remains\n * locked as the active target until its object id changes/despawns/replaces.\n * Inventory gains, animation changes and retry timers never select another tree\n * while the active tree object is still unchanged.\n'''
new_doc = ''' * Chopping is object driven. After a tree is clicked, that exact object remains\n * locked as the active target until its object id changes/despawns/replaces.\n * Inventory gains and animation changes never select another tree while the\n * active object is unchanged. If the interaction itself stops unexpectedly, the\n * same locked tree may be re-clicked; a different tree is never selected first.\n'''
if old_doc in text:
    text = text.replace(old_doc, new_doc, 1)

PATH.write_text(text, encoding='utf-8')

plugin = Path('kspwillowchopper/KspWillowChopperPlugin.java').read_text(encoding='utf-8')
if 'VERSION = "0.1.3"' not in plugin:
    raise RuntimeError('Unexpected Chopper version; refusing patch')

Path('.github/workflows/fix-chopper-resume-same-tree-clean.yml').unlink(missing_ok=True)
Path('.github/scripts/fix_chopper_resume_same_tree_clean.py').unlink(missing_ok=True)
