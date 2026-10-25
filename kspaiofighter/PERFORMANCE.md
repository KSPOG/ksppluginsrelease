# KSP AIO Fighter performance notes — v1.9.28 (dev)

This document describes changes from the v1.9.27 baseline. They target the
high-frequency fighter scheduler, unnecessary allocations, and repeated
Microbot/client-thread queries while preserving player-facing configuration.

## Priorities remain intact

1. Log-in, break, and task shutdown checks.
2. Recognized Stronghold dialogues and unrelated-dialogue safety pause.
3. Emergency healing and profitable inventory High Alchemy.
4. Missing supplies, banking, looting and bone burying.
5. Equipment, combat style, potions, safe-spot return and NPC attacks.

High Alch and healing are **not throttled** by the new action gates. The
independent High Alch single-flight guard still acknowledges actual
inventory/XP progress before dispatching another cast.

## Hot-loop optimizations

| Work | Old | New |
|---|---|---|
| Gear list/equipment checks | each 100ms loop | max once per 700ms unless training skill changes |
| Combat-style/weapon enum reads | each 100ms loop | max once per 800ms unless training skill changes |
| Potion checks | each 100ms loop | max once per 2.5s unless training skill changes |
| NPC target search when idle | each 100ms loop | max once per 350ms; allow 1.1s for attack acknowledgement |
| Ground-item cache scan with no loot | each 100ms loop, sometimes twice | max once per 180ms with one scan per loop |
| Active-combat NPC query | sometimes multiple per loop | one memoized result per loop |
| Parsed loot item names | string splitting and HashSet allocation each query | cached until user edits names |
| Saved safe spot / attack-area coordinates | repeated ConfigManager reads per NPC filter | immutable 200ms snapshot, live changes observed quickly |
| Identical status message publishing | repeated string and global status work | skips unchanged status |
| Overlay | allocates colors, fonts, strokes every rendered frame | shared constants and cached dimensions |
| Long overlay text | repeated substring trimming | binary-search truncation |

The 100ms scheduler remains for quick safety/loot/High-Alch response; no
extra general sleep was added.

## Limitations and validation

Short delays of up to 180ms for newly spawned eligible loot and up to 350ms
for new NPC selection are intentional CPU/load tradeoffs. Equipment and combat
style setting changes apply no later than their next timed check; **switching
training skills invalidates those gates immediately**.

The asynchronous client remains authoritative for equipment and inventory
state. The optimizations do not change GE prices, High Alch profitability
rules, configured gear, selected targets, attack areas or safe-spot boundaries.

Regression tests are in `.github/tests/aiofighter/`. The GitHub workflow
compiles against Microbot Hub and uploads the JAR. In-game profiling and
long-run memory measurements are still necessary to quantify resource
reductions and validate NPC/loot timing on live worlds.
