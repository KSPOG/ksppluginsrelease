from pathlib import Path
import subprocess

script = Path('KSPGELooter/KSPGELooterScript.java')
s = script.read_text()
s = s.replace(
    'import net.runelite.client.plugins.microbot.util.player.Rs2Player;\n',
    'import net.runelite.client.plugins.microbot.util.player.Rs2Player;\nimport net.runelite.client.plugins.microbot.util.walker.Rs2Walker;\n'
)

old = '''                updateOverlayState();
                if (!insideArea)
                {
                    releasePriorityPause("Outside GE area");
                    status = "OUTSIDE AREA - PAUSED";
                    groundItemsSeen = eligibleGroundItems = 0;
                    clearTarget();
                    if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                    return;
                }
'''
new = '''                updateOverlayState();
                if (!insideArea)
                {
                    Rs2Walker.clearWalkingRoute("ge-looter-outside-area");
                    releasePriorityPause("Outside GE area");
                    status = "OUTSIDE AREA - PAUSED";
                    groundItemsSeen = eligibleGroundItems = 0;
                    clearTarget();
                    if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                    return;
                }

                WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
                if (walkerTarget != null && !KSPGELooterArea.contains(walkerTarget))
                {
                    Rs2Walker.clearWalkingRoute("ge-looter-hard-area-guard");
                    status = "AREA GUARD - blocked outbound walk";
                }
'''
if old not in s:
    raise SystemExit('main area-guard block not found')
s = s.replace(old, new, 1)

old = '''    private void beginPriorityTakeover()
    {
        priorityTakeoverActive = true;
        if (ownsPriorityPause)
        {
            priorityPauseOwned = true;
            return;
        }

        if (Microbot.pauseAllScripts.compareAndSet(false, true))
        {
            ownsPriorityPause = priorityPauseOwned = true;
            Microbot.log("KSP GE Looter Priority Mode: paused other scripts for loot");
        }
        else
        {
            // Another component already owns the shared pause. Looting may still proceed, but this
            // plugin must never release a pause it did not acquire.
            priorityPauseOwned = false;
        }
    }
'''
new = '''    private void beginPriorityTakeover()
    {
        priorityTakeoverActive = true;

        // pauseAllScripts is cooperative, so also cancel any in-flight shared walker route now.
        Rs2Walker.clearWalkingRoute("ge-looter-priority-takeover");

        if (ownsPriorityPause)
        {
            priorityPauseOwned = true;
            return;
        }

        if (Microbot.pauseAllScripts.compareAndSet(false, true))
        {
            ownsPriorityPause = priorityPauseOwned = true;
            Microbot.log("KSP GE Looter Priority Mode: paused other scripts and cancelled active walk for loot");
        }
        else
        {
            // Another component already owns the shared pause. Looting may still proceed, but this
            // plugin must never release a pause it did not acquire.
            priorityPauseOwned = false;
        }
    }
'''
if old not in s:
    raise SystemExit('priority block not found')
s = s.replace(old, new, 1)
script.write_text(s)

Path('KSPGELooter/KSPGELooterArea.java').write_text('''package net.runelite.client.plugins.microbot.KSPGELooter;\n\nimport net.runelite.api.coords.WorldPoint;\n\n/** Exact hard guard for Area(3148, 3506, 3182, 3473). */\npublic final class KSPGELooterArea\n{\n    private static final int MIN_X = 3148, MAX_X = 3182, MIN_Y = 3473, MAX_Y = 3506, PLANE = 0;\n\n    private KSPGELooterArea() {}\n\n    public static boolean contains(WorldPoint point)\n    {\n        return point != null && point.getPlane() == PLANE\n                && point.getX() >= MIN_X && point.getX() <= MAX_X\n                && point.getY() >= MIN_Y && point.getY() <= MAX_Y;\n    }\n}\n''')

plugin = Path('KSPGELooter/KSPGELooterPlugin.java')
p = plugin.read_text()
if 'VERSION = "0.1.4"' not in p:
    raise SystemExit('expected GE Looter v0.1.4 not found')
plugin.write_text(p.replace('VERSION = "0.1.4"', 'VERSION = "0.1.5"', 1))

subprocess.run(['git', 'config', 'user.name', 'github-actions[bot]'], check=True)
subprocess.run(['git', 'config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com'], check=True)
subprocess.run(['git', 'add', 'KSPGELooter'], check=True)
subprocess.run(['git', 'commit', '-m', 'fix: hard-preempt active walking and enforce GE area'], check=True)
subprocess.run(['git', 'rm', '.github/workflows/patch-ge-looter-v015.yml', '.github/scripts/patch_ge_looter_v015.py'], check=True)
subprocess.run(['git', 'commit', '-m', 'chore: remove temporary GE Looter patch files'], check=True)
subprocess.run(['git', 'push', 'origin', 'HEAD:fix/ge-looter-hard-priority-area-v015'], check=True)
