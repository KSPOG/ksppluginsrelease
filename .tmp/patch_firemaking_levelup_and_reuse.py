from pathlib import Path

p = Path('kspaccountbuilder/tasks/skilling/firemaking/firemakingscript/FireMakingScript.java')
s = p.read_text()

s = s.replace('import net.runelite.api.widgets.Widget;\n', 'import net.runelite.api.widgets.Widget;\nimport net.runelite.api.widgets.WidgetID;\n', 1)

s = s.replace('''            if (handleBurnPrompt(targetLogName, targetLogId)) return;\n            if (!ensureSupplies(targetLogName, fireLocation != null)) return;\n''', '''            if (dismissLevelUpInterface()) return;\n            if (handleBurnPrompt(targetLogName, targetLogId)) return;\n            if (!ensureSupplies(targetLogName, fireLocation != null)) return;\n''', 1)

s = s.replace('''    private void useCampfire(int targetLogId, WorldPoint fireLocation)\n    {\n        if (!canBurnLog(targetLogId)) return;\n        if (fireLocation == null || !isInTargetArea(fireLocation)) return;\n''', '''    private void useCampfire(int targetLogId, WorldPoint fireLocation)\n    {\n        if (!canBurnLog(targetLogId)) return;\n        if (fireLocation == null) return;\n        WorldPoint player = Rs2Player.getWorldLocation();\n        if (player == null || (!isInTargetArea(fireLocation) && player.distanceTo(fireLocation) > CAMPFIRE_DISTANCE)) return;\n''', 1)

s = s.replace('''                .where(object -> object.getWorldLocation() != null\n                        && isValidFireId(object.getId())\n                        && isInTargetArea(object.getWorldLocation())\n                        && (!forestersOnly || object.getId() == FORESTERS_CAMPFIRE_ID))\n''', '''                .where(object -> object.getWorldLocation() != null\n                        && isValidFireId(object.getId())\n                        && (!forestersOnly || object.getId() == FORESTERS_CAMPFIRE_ID))\n''', 1)

marker = '''    private boolean handleBurnPrompt(String targetLogName, int targetLogId)\n    {\n'''
helper = '''    private boolean dismissLevelUpInterface()\n    {\n        Widget levelUp = Rs2Widget.getWidget(WidgetID.LEVEL_UP_GROUP_ID, 0);\n        if (levelUp == null || levelUp.isHidden())\n        {\n            return false;\n        }\n\n        // Use the dedicated RuneLite level-up group instead of treating every\n        // Continue dialogue as equivalent. Escape closes only the blocking level-up\n        // overlay and lets the normal Firemaking action resume next loop tick.\n        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);\n        awaitingFireStartAtMs = 0L;\n        expectingFiremakingXpDrop = false;\n        Microbot.status = \"Resuming Firemaking after level-up\";\n        return true;\n    }\n\n'''
if helper not in s:
    assert marker in s, 'handleBurnPrompt marker missing'
    s = s.replace(marker, helper + marker, 1)

p.write_text(s)
