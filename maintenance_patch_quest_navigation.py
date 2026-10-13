from pathlib import Path

# Lower one-time quest selection weights so regular skilling is preferred overall.
p = Path('kspaccountbuilder/KspAccountBuilderScript.java')
s = p.read_text()
for task in (
    'COOKS_ASSISTANT', 'GOBLIN_DIPLOMACY', 'ROMEO_AND_JULIET',
    'RUNE_MYSTERIES', 'SHEEP_SHEARER', 'X_MARKS_THE_SPOT',
    'THE_RESTLESS_GHOST', 'IMP_CATCHER', 'STRONGHOLD_OF_SECURITY'
):
    s = s.replace(task + '(7)', task + '(3)', 1)
p.write_text(s)

# Shared simple quests: never web-walk to a live roaming NPC tile.
p = Path('kspaccountbuilder/tasks/questing/shared/SimpleQuestScript.java')
s = p.read_text()
old = '''        WorldPoint target = npc != null && npc.getWorldLocation() != null
                ? npc.getWorldLocation()
                : destination;
        if (!walkTo(name, target, 4)) return;
'''
new = '''        // Never rebuild WebWalk to a roaming NPC's exact tile. The stable
        // quest destination gets us into the interaction area; the NPC click
        // handles the final local approach.
        if (!walkTo(name, destination, 6)) return;
'''
assert old in s, 'SimpleQuestScript dynamic fallback not found'
s = s.replace(old, new, 1)
p.write_text(s)

# Rune Mysteries has a custom NPC helper. Base walking on the stable destination
# and click the NPC locally once the quest area is reached.
p = Path('kspaccountbuilder/tasks/questing/runemyst/RuneMystScript.java')
s = p.read_text()
old = '''        if (npc == null || playerLocation == null || npc.getWorldLocation() == null
                || playerLocation.distanceTo(npc.getWorldLocation()) > NPC_REACH_DISTANCE)
        {
            state = walkingState;
            status = walkingStatus;
            KspWalkerGuard.walkToPoint(walkKey, destination, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        state = talkingState;
'''
new = '''        if (playerLocation == null || playerLocation.getPlane() != destination.getPlane()
                || playerLocation.distanceTo(destination) > NPC_REACH_DISTANCE + 2)
        {
            state = walkingState;
            status = walkingStatus;
            KspWalkerGuard.walkToPoint(walkKey, destination, NPC_REACH_DISTANCE + 2, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        if (npc == null || npc.getWorldLocation() == null)
        {
            state = walkingState;
            status = "Searching for " + npcName;
            KspWalkerGuard.clear(walkKey);
            return;
        }

        state = talkingState;
'''
assert old in s, 'RuneMyst NPC approach block not found'
s = s.replace(old, new, 1)
p.write_text(s)

# Romeo & Juliet: remove the remaining exact-NPC-tile fallback and make the
# staircase handoff local earlier. PR #284 already added stale menu recognition
# for Father Lawrence; keep that and complete the navigation fix.
p = Path('kspaccountbuilder/tasks/questing/romeoandjuliet/romeoscript/RomeoScript.java')
s = p.read_text()
s = s.replace('private static final int NPC_REACH_DISTANCE = 4;',
              'private static final int NPC_REACH_DISTANCE = 6;', 1)

old = '''        WorldPoint target = juliet != null && juliet.getWorldLocation() != null
                ? juliet.getWorldLocation()
                : JULIET_POSITION;

        if (player.distanceTo(target) > NPC_REACH_DISTANCE)
        {
            state = potion ? RomeoState.RETURNING_POTION_TO_JULIET : RomeoState.WALKING_TO_JULIET_HALLWAY;
            status = "Walking to Juliet";
            KspWalkerGuard.walkToPoint(WALK_JULIET, target, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }
'''
new = '''        if (player.distanceTo(JULIET_POSITION) > NPC_REACH_DISTANCE)
        {
            state = potion ? RomeoState.RETURNING_POTION_TO_JULIET : RomeoState.WALKING_TO_JULIET_HALLWAY;
            status = "Walking to Juliet's room";
            KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_POSITION, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }
'''
assert old in s, 'Romeo Juliet live-target fallback not found'
s = s.replace(old, new, 1)

s = s.replace('if (player.distanceTo(JULIET_STAIR_APPROACH) > 1)',
              'if (player.distanceTo(JULIET_STAIR_APPROACH) > 3)', 1)
s = s.replace('KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_STAIR_APPROACH, 1, WALK_REFIRE_COOLDOWN_MS);',
              'KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_STAIR_APPROACH, 3, WALK_REFIRE_COOLDOWN_MS);', 1)

old = '''        WorldPoint target = npc != null && npc.getWorldLocation() != null
                ? npc.getWorldLocation()
                : destination;

        if (npc == null || !npc.isReachable() || player.distanceTo(target) > NPC_REACH_DISTANCE)
        {
            state = walkingState;
            status = "WebWalking to " + name;
            KspWalkerGuard.walkToPoint(walkKey, target, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }
'''
new = '''        if (player.getPlane() != destination.getPlane()
                || player.distanceTo(destination) > NPC_REACH_DISTANCE)
        {
            state = walkingState;
            status = "Walking near " + name;
            KspWalkerGuard.walkToPoint(walkKey, destination, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        if (npc == null || npc.getWorldLocation() == null)
        {
            KspWalkerGuard.clear(walkKey);
            status = "Searching for " + name;
            return;
        }
'''
assert old in s, 'Romeo dynamic NPC fallback not found'
s = s.replace(old, new, 1)
p.write_text(s)
