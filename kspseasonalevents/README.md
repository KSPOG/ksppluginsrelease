# KSP Seasonal Events 0.1.0

A **standalone Microbot plugin**, independent of Chopper, with its own
config, RuneLite navigation panel, Start / Pause / Resume / Stop controls,
runtime script and hidden-until-Start overlay.

## Halloween 2026 (entry beta)

**Verified:** Jagex's Old School RuneScape Halloween event on 28 October
2026 begins by speaking with **Angel or Henja in Rimmington**. The rewards
are Vampyre costume top/bottom/cape, monocle and fangs. The event ends
18 November 2026. Reference: Jagex's *Halloween Event 2026* update.

**Implemented:** WebWalker approaches Rimmington only if the player is
outside the town and travel is enabled. It waits for a live named
Angel/Henja NPC, talks, advances known continue prompts and accepts a
small set of explicit affirmative options. Its result is an **entry beta**:
if the next event step or dialogue branch is not verified, it stops further
automatic input and shows `AWAIT_EVENT_STEP`. The script never falsely
reports completion just because it clicked the starter NPC. Pause/Resume
preserves state, Stop removes overlay and halts the script.

**Not yet implemented:** The supplied YouTube video
https://www.youtube.com/watch?v=VlaowZ3nv5w could not be retrieved for
transcription or frame-by-frame verification through the available browsing
tools. Consequently the later Halloween 2026 objectives, instance
transitions, item IDs, correct branch choices and completion varbits are
**not verified**. This version is NOT a full unattended event solver.
Completing the event in-game or providing its step-by-step transcript
is necessary to implement and validate the remaining stages safely.

Avoid cross-contaminating this with the 2026 **RuneScape 3** Halloween
event (Harvest Hollow, Horsemen and All Souls' Eve); they are different
games and unrelated mechanics.

Future seasonal events should be added as separate event modules
without modifying the Chopper or Account Builder plugins.

## Testing

`.github/workflows/seasonal-events-build.yml` compiles the independent
plugin using Microbot Hub Java 11 and validates lifecycle and safety
invariants, with a downloadable JAR in Actions artifacts. CI does not
verify live game dialogue or post-introduction quest completion.
