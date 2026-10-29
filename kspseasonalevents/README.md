# KSP Seasonal Events v0.2.17

A **standalone Microbot plugin**, independent of Chopper, with its own
config, navigation panel, Start / Pause / Resume / Stop / Reset progress,
runtime script, and hidden-until-Start overlay.

## Halloween 2026 — All Trick, No Treats

Source: https://oldschool.runescape.wiki/w/2026_Halloween_event
The complete written walkthrough was read on 28 October 2026. This
plugin implements a **staged workflow based on that walkthrough**; the
precise 2026 object IDs, Diango reward interface details,
dialogue branch strings and completion varbits remain to be verified in
an actual Microbot client. The child and adult trick-or-treater NPC ID
ranges are now known from live game inspection. Passing CI does not
guarantee event completion.

| Stage | Wiki requirements | Implementation |
|---|---|---|
| Introduction | Speak to Angel or Henja in Rimmington | Read live RuneLite dialogue widgets; interact with the in-scene NPC before requesting travel; recover candy stage directly from event-exclusive inventory |
| Smash lanterns | Smash all eight Jack-O-Lanterns | Live scene objects with Smash action; track disappearance and persist each confirmed world tile; search Rimmington when objects not loaded |
| Report lanterns | Speak to Angel or Henja | Return to their house |
| Halloween costume | Obtain previous Halloween rewards from Diango in Draynor and wear at least four items | Travel to Diango, request rewards via dialogue, equip known carried costume pieces, wait for four equipped pieces; does not blindly click reward widgets |
| Scare children | Spook *child* trick-or-treaters, not adults | Target only NPC IDs **16778–16782** with a live Spook action; confirm each exact NPC's disappearance in RuneLite's loaded scene before counting it |
| Report children | Speak to Angel or Henja | Return and progress to Chemist |
| Chemist | Obtain sugar by solving COFFIN periodic-table riddle: C(6)+O(8)+F(9)+F(9)+I(53)+N(7) = **92** | Talk to Chemist, select explicit 92 dialogue option when offered; otherwise log unknown widget for review |
| Cook sugar | Cook sugar on the house range or Chemist fireplace | Resolve a live Range/Fireplace and use sugar on it; wait for Molten sugar inventory |
| Collect onions | Collect five event onions from **Crate of onions** behind the house; inventory displays **Old inion** | Prioritize visible crate without WebWalker; require inventory receipt; count remaining Old inion + prepared Candy onions + verified deliveries toward five |
| Make candy | Combine molten sugar with Old inion | Use the observed inventory item name, accept `Old onion` as client-name fallback, and verify each Candy onion receipt |
| Serve adults | Give candy onions to five adult trick-or-treaters | Target only NPC IDs **16794–16798**; use Candy onion on a loaded undelivered adult and confirm inventory consumption before counting |
| Finale | Complete final dialogue and watch cutscene | While RuneLite CUTSCENE_STATUS is active, do nothing except explicitly requested dialogue; stop the event script and remove its overlay only when the visible Eventscroll.CONGRATULATIONS (807:5) reads **Event complete!** |

The wiki doesn't provide every precise live interaction, so certain steps
may require manual review rather than repeated speculative clicks. The
plugin logs stage transitions, visible dialogue options when unknown, NPC
IDs and world tiles of successfully targeted event objects. No guessed
NPC/object IDs are treated as authoritative.

## Verified child scares from RuneLite live scene (0.2.17)

A live October 29 log revealed that five `Spook` menu actions were
sent in eight seconds, but Angel/Henja still required the child-scare
objective. Later the plugin got stuck at 2/5: it had counted clicks
as successful scares, permanently excluded those NPC IDs, searched
near adults (which also have `Spook` actions), and repeatedly returned
to Angel/Henja after only partial attempts.

- On every valid child `Spook`, remember the exact **NPC ID + RuneLite
  scene index + world tile** as pending. Do **not** count the click.
- Examine the **live `getTopLevelWorldView().npcs()`** collection on
  RuneLite's client thread, with the player's proximity to the clicked
  tile checked. The exact scene index must remain **absent for at least
  450 ms** before the scare counts toward 5/5.
- A temporarily unavailable scene, still-visible NPC or elapsed seven
  seconds cannot count as success. Unconfirmed interactions are logged,
  returned to the candidate pool after a short cooldown and retried.
- No new `Spook` is sent until the previous interaction is confirmed or
  timed out. The plugin reports to Angel/Henja only after **five
  confirmed disappearances**, not after five attempted clicks.
- The UI now labels progress **Child scares verified**; Angel/Henja's
  separate **Child objective** remains unverified until a confirming
  dialogue state.
- Old `halloweenChildNpcIdsAttempted` saved values are discarded as
  unverified; confirmed IDs use a new key,
  `halloweenChildNpcIdsConfirmed`. **Other progress, including all
  previously confirmed lantern tiles, is preserved.**

**Limitation:** An NPC can leave the loaded scene for reasons other
than a successful scare. The near-tile and repeated-absence checks
reduce false positives, but Angel/Henja's objective dialogue is still
the authority on quest progression. Compilation does not replace a
live client acceptance test.

## Eighth-lantern handoff and progress persistence (0.2.16)

On 29 October 2026, a live log showed eight Jack-O-Lanterns being
individually confirmed (the final one at 21:24:54), yet the plugin
remained in `SMASH_LANTERNS`. The prior implementation only advanced
at 8/8 when a particular game-chat confirmation was received. The
introductory Angel/Henja dialogue also left `sweepRequiredByStarter`
enabled, so the general 7/8 fallback did not run.

A newly confirmed eighth lantern, whether evidenced by the Smash
action disappearing in the loaded scene **or** a game message, now
moves directly to `REPORT_LANTERNS` to talk to Angel/Henja.
Local 8/8 does **not** mark the event/server objective complete:
Angel/Henja's actual dialogue determines whether to proceed to the
costume/children stage or return to searching for lanterns.

All confirmed tiles remain saved across a stop/reload. Progress is not
reset to recover from this regression. If Angel/Henja still requests
lantern smashing, the server dialogue overrides the local count and
keeps the search open rather than ping-ponging between reporting stages.

## Cutscene handling and completion scroll (0.2.15)

- **During any Halloween cutscene**, the Microbot
  `Rs2Dialogue.isInCutScene()` guard (RuneLite
  `CUTSCENE_STATUS` varbit 542) prevents inventory reconciliation,
  walking, NPC interactions, and ordinary stage actions.
- Only an **actual on-screen dialogue Continue** is advanced; choices
  are processed when prompted, but a skip-cutscene prompt is refused
  using **No** rather than choosing a generic affirmative option.
- After final dialogue, `WAIT_FOR_REWARD` never returns to Angel/Henja
  just because a reward appears. It waits for the exact visible RuneLite
  widget `Eventscroll.CONGRATULATIONS`, **group 807, child 5**,
  widget ID **52887557**, and exact text **Event complete!**.
- This completion widget is checked on the script loop **and** on game
  ticks, including while the user has paused the script. Upon
  confirmation the script saves `COMPLETED`, stops, and the owning
  plugin removes its overlay; the plugin is not forcibly disabled in
  RuneLite, leaving the side panel available.
- Chat messages and NPC dialogue that say the event is finished do
  not stop gameplay prematurely before the actual completion scroll.

The event selector is now the reusable **`HALLOWEEN` / “Halloween”**,
not an annual `HALLOWEEN_2026` enum. The old event selection is
migrated automatically while preserving saved objective progress.
The **2026-specific handler** remains separate and can be replaced or
extended for later Halloween event mechanics without renaming the
user-facing event.

Automated compilation and regression tests validate source behavior;
the full final cutscene and widget appearance must still be validated
in-game.

## Verified 2026 child/adult NPC IDs for both tasks (0.2.14)

The five **adult trick-or-treater NPC IDs** were identified in-game:
`16794`, `16795`, `16796`, `16797`, `16798`.
The other 2026 group is the five **children**:
`16778`, `16779`, `16780`, `16781`, `16782`.

These verified IDs now control **both** the child Spook and adult Candy
onion stages. No height comparison, guessed action-to-age mapping,
NPC name substring or persisted group classification can override them.
Spook additionally requires the live Spook menu action; candy delivery
uses an eligible, undelivered adult already in the loaded scene first
and counts completion only after observing the candy inventory decrease.
Older persisted NPC ID lists are filtered to the correct age groups
on startup, and the obsolete inferred group configuration is cleared.

When adult NPCs are absent from the scene, the usual bounded search
continues; loaded child NPCs are ignored as delivery targets.
Live success of all five interactions still requires in-game validation.

## Historical adult delivery diagnosis (0.2.13; superseded by verified IDs)

Logs from 29 October 2026 show that all five candy onions were created,
but `GIVE_CANDY` had only NPC IDs 16794–16798 in the scene. The
previous classifier required **both** trick-or-treater ID ranges to be
seen for model-height comparison, and wandered across generic Rimmington
waypoints when it couldn't classify the visible NPCs.

Version 0.2.13 tried to infer adults from transformed NPC menu actions
and model heights. This was insufficient because one NPC group could be
outside the loaded scene. **Version 0.2.14 replaces the inference
entirely with the verified ID ranges above**, while preserving
loaded-target-first navigation and inventory-confirmed deliveries.

## Onion collection and inventory-state recovery (0.2.12)

The **Crate of onions** is detected by its exact live name and location near
Angel/Henja's house, before issuing any walking command. The item has been
observed as **Old inion** (with **Old onion** accepted as a compatibility
spelling). Collect only the outstanding amount required for five total:
`Old inion` still held + `Candy onion` still held + **verified** deliveries.
Each crate interaction waits for the next inventory receipt. If the crate is
not loaded and the player is already near the house, the script logs the
missing crate instead of repeatedly WebWalking to the starter waypoint.

The event stage is also reconciled from inventory (sugar, molten sugar, Old
inion, candy onions) once the adult-candy objective is established. On
restart, unique candy-objective inventory can resume the local stage
without a needless visit to Angel/Henja. Ordinary sugar alone is used
for this purpose only when the saved Angel/Henja dialogue already
confirmed the adult-candy objective.

For all named event NPCs (Angel/Henja, Chemist, Diango and adult targets),
the script prioritizes loaded scene targets and interaction before any
fallback to WebWalker. Server-side dialogue remains authoritative for
the overall event objective when inventory evidence is inconclusive.

## Angel/Henja visible dialogue capture and recovery (0.2.11)

Starter conversations now inspect visible RuneLite NPC/player/sprite/option
widget text on the client thread, in addition to Microbot's dialogue
helpers. The older helper can return an empty string for a visibly open
conversation. An empty or unclassified reading does **not** authorize a
stage transition or immediate review stop. The plugin captures the visible
text and retries the starter conversation up to three times before stopping
with explicit diagnostics. It resolves the event objective only from a
recognized Angel/Henja conversation, and ignores single-frame gaps
between dialogue pages.

## Angel/Henja dialogue is authoritative (0.2.8)

Global 2026 event state is now resolved from the dialogue produced after
talking to **Angel or Henja**. Saved stages, overlay counters, inventory
items and successful click dispatches remain useful recovery/operation data,
but they no longer decide which event objective is active.

On every non-completed plugin start the script first returns to Angel/Henja
and reads their dialogue. The shared starter-state classifier currently
recognises these objective families:

- smash remaining jack-o'-lanterns;
- obtain/equip a Halloween costume and scare child trick-or-treaters;
- solve the Chemist / molten sugar / old onion / candy onion objective;
- final reward dialogue;
- explicit event completion.

Once an objective is known, inventory state may select the appropriate
**subtask** (for example cook sugar versus collect old onions), but it does
not override the objective supplied by Angel/Henja.

Local 8/8 lantern acknowledgements now trigger a return to the starter for
verification instead of completing the lantern objective by themselves.
Likewise, five local adult-delivery acknowledgements lead to a final starter
conversation; if Angel/Henja still describes the adult-candy objective,
the local 5/5 bookkeeping is treated as stale and safely reopened.

The old **Confirm all 8 lanterns smashed** override has been removed.
The side panel now provides **Recheck state with Angel / Henja**, which
changes no counters and simply requests a fresh authoritative dialogue
sync. Unknown dialogue stops for review rather than falling back to a saved
stage.

The final Vampyre reward item is supporting evidence only. If a completion
game message was missed, the script talks to Angel/Henja again instead of
marking the event complete from inventory alone.

## NPC-guided child searching and client-thread load (0.2.7)

After the client has confirmed the smaller 2026 Trick-or-treater model
group as children, the script now stops repeatedly requesting their
model heights from RuneLite's client thread. Initial model heights are
captured in one client-thread batch.

During `SPOOK_CHILDREN`, the script takes one visible-NPC snapshot,
selects an **unattempted verified child** with the `Spook` action, and
excludes adults from every Spook click. If no eligible child appears,
it first allows a short scene-settle interval and then scouts up to
four **actual NPC locations** discovered in the scene. It can walk near
adults to load neighboring children, but never attempts to Spook
adults. The previous generic eight-point Rimmington route is not
used for this stage.

If further children remain undiscoverable, it returns to Angel/Henja
for authoritative progress verification rather than wandering
arbitrary waypoints. A negative response is retried only a bounded
number of times; partial 2/5 progress is preserved. The plugin logs
currently visible NPC IDs, classification, action availability and
world tiles when it exhausts the scene-based search.

The supplied `ClientThread did not run task within 2000ms` warning
occurred *after* the bot was paused and cannot be conclusively
attributed to Seasonal Events. Reducing repetitive height reads
removes an avoidable source of client-thread pressure, but it does not
claim to fix unrelated client event-thread stalls.

No reset is required. Saved lanterns, outfit, child attempted IDs,
classification and adult progress are preserved. Live testing remains
necessary to confirm whether all five children are accessible in the
active scene and whether the event server accepts the attempts.

## Child scare progress in overlay and side panel (0.2.6)

The overlay and sidebar previously omitted child progress entirely.
They now display **Child Spook attempts: X/5** and a separate
**Child objective: Not verified / Confirmed** status. Five is the
known checkpoint for returning to Angel/Henja, not proof that the game
accepted every scare. A Spook click increases *attempted* progress,
while only the event starter's verified next-objective dialogue marks
the child objective **Confirmed**.

Attempted child NPC IDs remain saved across Source Loader refreshes.
A confirmed next stage is also saved and displayed after restarting.
A reply explicitly instructing more scares can clear the old attempt
set for a bounded retry. Do not interpret 0/5 after such a reset as
the game undoing earlier animation attempts.

## Explicit Angel/Henja child-objective instructions (0.2.6)

When reporting the child-scaring objective, Angel/Henja may reply:

> Get yourself a Halloween costume and scare off the trick-or-treaters!
> Diango in Draynor Village should have something.

This means the child-scaring objective is **still pending**; it is not an
unknown dialogue and does not authorize starting the Chemist step.
The script now parses the instruction in a tested, Java-only dialogue
classifier. If four wearable costume slots are already equipped it waits
for the conversation to finish, then resumes `SPOOK_CHILDREN`; otherwise
it resumes `GET_COSTUME`.

Unrecognized dialogue still stops for review rather than advancing.
Chemist progression requires a recognized next-objective instruction.
When the starter rejects a full five-child attempt, the plugin permits
one controlled retry and saves the retry count so restarting cannot
silently start an endless scare/report cycle. The stored lantern count,
Diango outfit, and completed adult count are preserved.

## Scaring children and identifying adults (0.2.5)

The child-spooking stage must not target adult trick-or-treaters.
The 2026 client logs contain two distinct NPC ID groups, but do not
independently establish which group is children.

The plugin therefore classifies the groups from observed RuneLite NPC
model heights. It requires multiple samples per group and a clear size
separation, then saves the child-group result across sessions. NPCs with
unverified age are skipped, even if they offer a Spook action.

After five distinct eligible child attempts it returns to Angel/Henja
to verify progression. Five is a verification checkpoint, not a claim
that the event is complete. Candy onions are offered only to verified
adult targets, and delivery still requires inventory confirmation.

If model heights do not distinguish the NPC groups, the script reports
the observed data and cannot safely guess. A live session will be
needed to verify the correct groups and the resulting quest dialogue.

## Batch Diango costume retrieval (0.2.4)

While `GET_COSTUME` is active, the Diango Holiday Item Retrieval window
**remains open until the plugin has four different wearable costume slots**
available across the inventory and already-worn equipment. The script
selects and validates each missing item in sequence without closing or
reopening the interface between withdrawals.

A costume slot is one of head, torso, legs, hands or feet. Owning several
pieces for the same slot does not satisfy the four-piece outfit objective.
After four distinct slots have been confirmed, the plugin closes the window
once, equips eligible inventory items, verifies four different slots are
worn, then transitions to `SPOOK_CHILDREN`.

The script still observes the per-item inventory receipt, enforces retry
limits and stops for review on unknown widget layouts or missing inventory
capacity. Any worn costume piece counts toward the requirement, so it is not
withdrawn again. Saved lantern and event progress are not reset.

## Diango Holiday Item Retrieval (0.2.3)

The `GET_COSTUME` stage now handles the opened Diango Holiday Item
Retrieval interface rather than repeatedly re-opening it. It identifies
the *live* RuneLite widget group by the displayed interface title, then
looks up item IDs through RuneLite's current item definitions. It selects
only eligible Halloween clothing, beginning with a complete Skeleton
set (boots, gloves, leggings and shirt), and filters out pieces already
owned or worn.

After each click the script waits to see that exact item in the
inventory/equipment before counting the retrieval as successful. The
retrieval window is closed before equipping. Item searches are bounded,
with visible scrolling when necessary. An unrecognised or exhausted
interface stops for review rather than guessing screen coordinates.

Diango's explicit dialogue option `I'd like to check holiday items please!`
is handled as well. The earlier 8/8 lantern state and saved
`GET_COSTUME` stage are preserved.

**Runtime validation:** RuneLite widget IDs and child layouts are discovered
dynamically; the exact 2026 retrieval grid still requires an in-client test.
If the grid uses an unsupported representation, the script will log
the observed widget count and stop for review. Do not reset progress.

## Costume dialogue fix (0.2.2)

When Angel/Henja offers a hat after all eight lanterns, the script now
selects the observed exact response **"Sure, I'll take the hat."** and
continues the conversation. The starter stage only advances after an actual
dialogue has been observed and completed, rather than on the initial
NPC click. This avoids travelling to Diango while the quest dialogue is
still active. Existing saved `GET_COSTUME` sessions can also resolve the
hat question without resetting the event.

The costume task equips usable Halloween items already carried before
attempting to travel to Draynor. If more pieces are needed, the script
prefers Diango's `Holiday-items` action over repeatedly starting an
unrelated conversation. The specific live Holiday Item Retrieval interface
widgets still require in-game confirmation; this version does not claim
fully automatic selection from that interface.

## Lantern progression recovery (0.2.1)

The legacy 7/8 stall occurred because a pending smash could remain unconfirmed
if the player moved out of observation range, and because the eight exploration
waypoints could repeat without new live targets. The last dispatched lantern
world tile is now persisted and revisited after a pause, stop or restart, so a
missing action is checked while the tile is actually in scene range.

With an existing 7/8 save and no target visible, the plugin first enters
`VERIFY_LANTERNS` and asks Angel/Henja. It only performs a full
discovery sweep if the starter says lanterns are still outstanding. It
credits completion only if the
dialogue positively confirms the lantern objective or the subsequent costume
task. If confirmation is unavailable it stops for review rather than endlessly
walking. The overlay may still display 7/8 while server progress is already 8/8;
that number represents *confirmed local observations*, not a directly queried
2026 quest varbit.

A guarded **Confirm all 8 lanterns smashed** control is available in the
side panel if seven or more have been recorded and the current objective is
lantern smashing/verification. Only click after independently confirming that
all eight lanterns have been smashed in-game. It preserves the stored seven
tiles, records the player's explicit confirmation, then resumes the report
objective. It is available while paused without unpausing the script.

The plugin now processes Halloween confirmation messages only from game
message types, not public or private chat. Stage transitions, pauses and
stops also cancel stale walker routes. No new object IDs or fixed spawn
coordinates are assumed.

## Progress and session behavior

The stage is saved in RuneLite ConfigManager under
`KspSeasonalEvents.halloweenStage`; smashed lantern tile keys and confirmed
adult candy deliveries are also saved so restarts don't reset objectives.
Use **Reset progress** only while stopped to intentionally start over.

The script starts only after **Start** is clicked. Pause keeps the current
stage and counters. Stop removes the overlay, halts the script and keeps
saved event progress. The side panel and overlay show lantern and adult
candy progress.

The wiki describes the event as running from 28 October to 18 November
2026. New accounts with under 24 hours of playtime may have to reclaim
their tradeable Halloween mask/pumpkin items later.

**Not RuneScape 3**: This plugin does not implement Harvest Hollow or
RuneScape 3 seasonal quests.

## Validation

`.github/workflows/seasonal-events-build.yml` compiles
`KspSeasonalEventsPlugin` with Microbot Hub's Java 11 build and produces a
JAR. A full event run against actual RuneLite scene and dialogue state
is still necessary to validate the end-to-end workflow.
