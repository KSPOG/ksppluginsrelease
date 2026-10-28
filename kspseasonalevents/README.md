# KSP Seasonal Events v0.2.0

A **standalone Microbot plugin**, independent of Chopper, with its own
config, navigation panel, Start / Pause / Resume / Stop / Reset progress,
runtime script, and hidden-until-Start overlay.

## Halloween 2026 — All Trick, No Treats

Source: https://oldschool.runescape.wiki/w/2026_Halloween_event
The complete written walkthrough was read on 28 October 2026. This
plugin implements a **staged workflow based on that walkthrough**; the
live 2026 object IDs, trick-or-treater NPC IDs, Diango reward interface,
dialogue branch strings and completion varbits remain to be verified in
an actual Microbot client. Passing CI does not guarantee event completion.

| Stage | Wiki requirements | Implementation |
|---|---|---|
| Introduction | Speak to Angel or Henja in Rimmington | Start/return dialogue with existing NPC APIs |
| Smash lanterns | Smash all eight Jack-O-Lanterns | Live scene objects with Smash action; track disappearance and persist each confirmed world tile; search Rimmington when objects not loaded |
| Report lanterns | Speak to Angel or Henja | Return to their house |
| Halloween costume | Obtain previous Halloween rewards from Diango in Draynor and wear at least four items | Travel to Diango, request rewards via dialogue, equip known carried costume pieces, wait for four equipped pieces; does not blindly click reward widgets |
| Scare children | Spook *child* trick-or-treaters, not adults | NPC detection requires a live Spook menu action, ignores recently targeted NPC indexes |
| Report children | Speak to Angel or Henja | Return and progress to Chemist |
| Chemist | Obtain sugar by solving COFFIN periodic-table riddle: C(6)+O(8)+F(9)+F(9)+I(53)+N(7) = **92** | Talk to Chemist, select explicit 92 dialogue option when offered; otherwise log unknown widget for review |
| Cook sugar | Cook sugar on the house range or Chemist fireplace | Resolve a live Range/Fireplace and use sugar on it; wait for Molten sugar inventory |
| Collect onions | Gather five **old onions** from crate behind house, not ordinary onions | Live crate interaction; confirm inventory has five old onions |
| Make candy | Combine molten sugar and old onions | Repeated inventory combination until five candy onions |
| Serve adults | Give candy onions to five adult trick-or-treaters | Detect trick-or-treaters **without** child-only Spook action, use candy onion on each NPC, confirm decreased inventory before counting |
| Finale | Cutscene then speak to Angel or Henja | Return for dialogue; only report completion when in-game message or Vampyre reward confirms it |

The wiki doesn't provide every precise live interaction, so certain steps
may require manual review rather than repeated speculative clicks. The
plugin logs stage transitions, visible dialogue options when unknown, NPC
IDs and world tiles of successfully targeted event objects. No guessed
NPC/object IDs are treated as authoritative.

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
