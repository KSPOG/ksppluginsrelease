from pathlib import Path

p = Path('kspaccountbuilder/tasks/skilling/firemaking/firemakingscript/FireMakingScript.java')
s = p.read_text()

if 'import net.runelite.api.gameval.ObjectID;' not in s:
    s = s.replace('import net.runelite.api.coords.WorldPoint;\n', 'import net.runelite.api.coords.WorldPoint;\nimport net.runelite.api.gameval.ObjectID;\n', 1)
if 'import net.runelite.api.widgets.WidgetID;' not in s:
    s = s.replace('import net.runelite.api.widgets.Widget;\n', 'import net.runelite.api.widgets.Widget;\nimport net.runelite.api.widgets.WidgetID;\n', 1)

s = s.replace('private static final int NORMAL_FIRE_ID = 26185;', 'private static final int NORMAL_FIRE_ID = ObjectID.FIRE;')

if 'if (dismissLevelUpInterface()) return;' not in s:
    s = s.replace('''            if (handleBurnPrompt(targetLogName, targetLogId)) return;\n            if (!ensureSupplies(targetLogName, fireLocation != null)) return;\n''', '''            if (dismissLevelUpInterface()) return;\n            if (handleBurnPrompt(targetLogName, targetLogId)) return;\n            if (!ensureSupplies(targetLogName, fireLocation != null)) return;\n''', 1)

s = s.replace('''    private void useCampfire(int targetLogId, WorldPoint fireLocation)\n    {\n        if (!canBurnLog(targetLogId)) return;\n        if (fireLocation == null || !isInTargetArea(fireLocation)) return;\n''', '''    private void useCampfire(int targetLogId, WorldPoint fireLocation)\n    {\n        if (!canBurnLog(targetLogId)) return;\n        if (fireLocation == null) return;\n        WorldPoint player = Rs2Player.getWorldLocation();\n        if (player == null || (!isInTargetArea(fireLocation) && player.distanceTo(fireLocation) > CAMPFIRE_DISTANCE)) return;\n''', 1)

s = s.replace('''                .where(object -> object.getWorldLocation() != null\n                        && isValidFireId(object.getId())\n                        && isInTargetArea(object.getWorldLocation())\n                        && (!forestersOnly || object.getId() == FORESTERS_CAMPFIRE_ID))\n''', '''                .where(object -> object.getWorldLocation() != null\n                        && isValidFireId(object.getId())\n                        && (!forestersOnly || object.getId() == FORESTERS_CAMPFIRE_ID))\n''', 1)

s = s.replace('''                .where(object -> object.getWorldLocation() != null\n                        && location.equals(object.getWorldLocation())\n                        && isInTargetArea(object.getWorldLocation())\n                        && isValidFireId(object.getId()))\n''', '''                .where(object -> object.getWorldLocation() != null\n                        && location.equals(object.getWorldLocation())\n                        && isValidFireId(object.getId()))\n''', 1)

s = s.replace('''        return player != null\n                && fireLocation != null\n                && isInTargetArea(fireLocation)\n                && Rs2Player.distanceTo(fireLocation) <= CAMPFIRE_DISTANCE\n                && !Rs2Player.isAnimating();\n''', '''        return player != null\n                && fireLocation != null\n                && Rs2Player.distanceTo(fireLocation) <= CAMPFIRE_DISTANCE\n                && !Rs2Player.isAnimating();\n''', 1)

marker = '''    private boolean handleBurnPrompt(String targetLogName, int targetLogId)\n    {\n'''
helper = '''    private boolean dismissLevelUpInterface()\n    {\n        Widget levelUp = Rs2Widget.getWidget(WidgetID.LEVEL_UP_GROUP_ID, 0);\n        if (levelUp == null || levelUp.isHidden())\n        {\n            return false;\n        }\n\n        // RuneLite/Microbot exposes the dedicated level-up interface group. Do not\n        // treat every Continue dialogue as a level-up; only dismiss this exact widget.\n        // Escape removes the modal without driving dialogue state, then the normal\n        // Firemaking action retries on the next 100 ms loop.\n        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);\n        awaitingFireStartAtMs = 0L;\n        expectingFiremakingXpDrop = false;\n        Microbot.status = \"Resuming Firemaking after level-up\";\n        return true;\n    }\n\n'''
if 'private boolean dismissLevelUpInterface()' not in s:
    assert marker in s, 'handleBurnPrompt marker missing'
    s = s.replace(marker, helper + marker, 1)
else:
    start = s.index('    private boolean dismissLevelUpInterface()')
    end = s.index('    private boolean handleBurnPrompt', start)
    s = s[:start] + helper + s[end:]

p.write_text(s)
