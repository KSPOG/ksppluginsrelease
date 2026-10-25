# KSP Account Builder - Development and Live Acceptance Milestones

Updated: 2026-10-25
Repository: Microbot-Hub
Plugin: KspAccountBuilderPlugin

## Progress: 4 of 7 previously verified

- Milestones 1-4: VERIFIED in earlier agent checkpoints. Original acceptance evidence must be reconciled before the final PR; do not infer that live integration passed.
- Milestone 5 - Progression and GE economy: IN PROGRESS.
- Milestone 6 - Controlled live UI/log integration acceptance: PENDING.
- Milestone 7 - Focused pull request, reviews, CI and merge: PENDING.

## Milestone 5 - Required acceptance

- Crafting selects an executable recipe based on skill level and actual resources.
- Banking withdraws a chisel and appropriate uncut gems with inventory acknowledgement and no repeated clicks.
- Missing ingredients trigger affordable GE resupply instead of unrelated task selection.
- Chisel and affordable unlocked gems take priority over unrelated purchases.
- GE offers are treated as pending only after confirmed submission.
- Completed offers are collected and verified by a fresh bank audit before pending requirements clear.
- Partial budgets, pending orders, invalid prices and zero GP do not produce purchase loops.
- GE selling, limited funds and task fallback permit useful account progression.
- Melee and Rune Essence handoffs do not bank for runes only to teleport back into the same route.
- Interactions with NPCs and objects require acknowledgement or bounded cooldowns.
- Mining avoids continuous unavailable-rock scans and log spam, resumes after respawn, and continues gaining Mining XP without unnecessary repeat clicks.

## Milestone 6 - Required live evidence

- Observe actual RuneLite/Microbot UI alongside timestamped client logs.
- Confirm the running implementation matches the candidate JAR before claiming live acceptance.
- Observe successful Crafting withdrawal, production and XP progress.
- Observe GE purchase, offer commit, collection, bank receipt and subsequent task resumption.
- Observe a sell/collect cycle and an economically appropriate task switch.
- Confirm Melee and Rune Essence routes without repeated teleport-bank cycles.
- Verify NPC/object interaction rates and recovery from failed clicks.
- Verify live Mining respawn-backoff effectiveness: lower unavailable-rock log frequency, subsequent successful interactions, and continued XP progress.
- Check pause/resume and task handoff without resetting progress.
- Resolve and validate the hot-reload state-preservation blocker before deploying the candidate: DevLoader unloads and recreates active plugins, Account Builder startUp() explicitly resets runAccountBuilder to false, and prepareHotUnload() shuts down the existing runtime. The cooperative pause gate preserves child tasks only within the existing script instance; it does not establish persistence across classloader replacement. Establish a controlled safe-boundary handoff or implement and regression-test explicit state restoration before accepting live deployment.
- Record start/end timestamps, status, failures and reproducible evidence for each scenario.

## Verified development evidence

- Step 34 (2026-10-25): selective Java 11 KSP Account Builder build succeeded against Microbot 2.6.41. Candidate JAR SHA256 07AE1E719FF755951D3638112774C3D775468A4F41D9CC947F11D27D6B8BAEB9 contains both EssenceBankCompletionPolicy.class and MiningRockRetryPolicy.class and all ten PNG artwork assets. All 68 regressions passed across nine JUnit suites, with zero failures, errors, or skips.
- Step 36 (2026-10-25): live Microbot PID 28228 remained active on MINING. DevLoader reads the GitHub dev branch every five minutes and can refresh active plugin units. The validated local candidate has not been deployed or independently verified as the running implementation. Controlled live acceptance remains pending.

- Step 199: 44 of 44 JUnit tests pass across six suites; zero failures, errors or skips.
- Step 197: selective Java 11 Account Builder build successful.
- Step 200: candidate JAR SHA-256 BF52C4DF92645BDB871DEAD7BF15C502143AA739721FA16A029C4B79AF86875B.
- Step 200: ten PNG artwork assets packaged; GE and Crafting policies included.
- Step 200: no matching Account Builder or Source Loader JAR found in the conventional sideload folder.
- Existing live client PID 28228 started at 16:15:21; the candidate build has NOT been verified as active.

## Open live observations

- 21:03:05 to 21:06:20 CEST: Crafting selected Tiara at level 41 while bank remained open at Lumbridge; parent reported stationary and non-animating. Investigate resource availability, worker activity and whether stall recovery fires. Do not claim the new code has failed without confirming deployment.
- 20:52:08 CEST: Rs2NpcModel logged failed Dismiss interaction on an NPC with null name and no actions. Determine owner and frequency before attributing spam to Account Builder.
- The user previously observed missing chisel/uncut-gem withdrawals, redundant Melee/Rune Essence teleport-bank routing and repeated NPC/object clicks. These remain live-acceptance scenarios even though local safeguards exist.

## Release restrictions

- Maintain the live client without interruption until a controlled handoff is established.
- Do not overwrite, reset, clean or delete unrelated files or untracked Account Builder sources.
- Do not claim completion solely from compilation or unit tests.
- Create a focused feature branch and PR only after milestone 5 and 6 acceptance; verify CI, reviews and merge readiness before merging.

- Step 208: watchdog test-runtime classpath refreshed from the compiled source; 46/46 Account Builder JUnit regressions pass, including delayed scheduler sampling and pause exclusion. Live integration remains unverified.

- Step 212: verified running JVM -jar path C:\Users\Proje\.microbot\microbot-2.6.40.jar; candidate 46/46 tests, 10 artwork PNGs. Embedded class comparison is diagnostic only; loaded-class identity and live acceptance require independent verification.

- Step 213: the live Microbot 2.6.40 core JAR contains none of the five inspected Account Builder classes; Account Builder must originate from a separate runtime source if loaded. Its exact loaded class identity and post-21:10 lifecycle remain unverified. The development candidate passes 46/46 tests but has not passed controlled live acceptance.

- Step 216-217: live JVM confirms Account Builder scheduler still runs; Melee worker was inside Rs2WalkerTransports.waitForPostHandleObjectLanding while the character remained near (3243,3235-3237,0), with repeated route-idle nudges and no progress toward (3096,3468,0). Treat as a new navigation acceptance blocker pending root-cause confirmation. Live DevLoader class cache predates the verified local fixes.

- Step 218 correction to navigation observation: the Melee route was delayed around Lumbridge until approximately 21:35, then resumed through a transport and progressed into the dungeon. It was not permanently deadlocked. Investigate a bounded no-progress recovery before considering this live-acceptance blocker resolved.

- Step 220-221 live Melee investigation: at 21:39:54 the worker reported no food bank required at 26/35 HP, with 25 empty inventory slots; by 21:39:57 it initiated a bank route from Hill Giants toward Lumbridge upper bank (3209,3220,2). Determine the actual triggering branch and whether resources were changed before returning; do not assume this was a confirmed redundant bank trip.

- Step 223 investigation: Melee unexpectedly started a Lumbridge bank route at 21:39:57 immediately after successful Hill Giant combat, despite a 21:39:54 food-bank rejection at 26/35 HP and 25 free inventory slots. The optional missing-weapon inspection may run after transient loss of combat detection. Confirm that the worker has already fought before permitting this optional bank trip; preserve emergency HP banking. No completed bank transaction is yet demonstrated.

- Step 225: implemented run-scoped Melee optional-gear inspection satisfaction after confirmed combat, preventing delayed weapon-inspection bank trips on combat disengagement. Initial legitimate gear inspection, food resupply and emergency HP banking remain unchanged. Six regression tests added; compilation and live acceptance pending.

- Step 227 verification: selective Account Builder JAR compiled successfully, all ten PNG artwork assets were retained, and all seven targeted JUnit suites passed (52 tests, zero failures/errors/skips). The full repository compileJava task remains blocked by 25 errors in unrelated plugins. Updated candidate has not yet been deployed or live-validated.

- Step 238: verified that the candidate JAR with 52 passing regressions and ten artwork PNGs differs from every inspected DevLoader cache generation in the main scheduler, walker guard, Melee, Rune Essence, and Crafting classes. Staged the hash-verified candidate under build only; NOT installed or live-loaded. Live validation requires a controlled DevLoader update when gameplay is at a safe handoff boundary.

- Step 250: Rune Essence banking stays active through partial deposits; closing requires an inventory containing only the retained pickaxe or an empty inventory when equipped, and the bank-close transition must finish before Aubury travel. Eight pure-policy regressions passed in the verified 68-test candidate. Live acceptance remains pending.

- Step 264 live navigation investigation: Mining logs at 2026-10-25 00:38-00:40 show bank-vs-direct route comparison, Varrock Teleport selection and repeated canoe handoffs. Determine whether the currently loaded DevLoader MiningScript or shared walker selects these detours; a corrected candidate build and 60 passing regressions do not yet establish a live routing fix.

- Live Mining finding (2026-10-25): 955 unavailable-Silver-rock messages in 1200 log lines, alongside 21 real rock interactions and Mining level progression from 38 to 39. Implemented a 750 ms unavailable-rock rescan interval, 10-second diagnostic throttle, respawn retries and eight MiningRockRetryPolicy tests. Selective build and all 68 regressions passed. Controlled live Mining acceptance remains pending.


### Live Rune Essence transport and banking investigation (
LIVE_ESSENCE_20261025_0126
)
Status: OPEN - observed on the running older DevLoader class generation; candidate fix not yet live-validated.
- 2026-10-25 01:26:49 CEST: Account Builder was in the Essence Mine at WorldPoint(2906,4836,0); by 01:27:03 it arrived near Lumbridge after a TELEPORTATION_SPELL handoff, then reached the Lumbridge upper-floor bank at approximately 01:27:33.
- Repeated `Failed to load exit portal config` messages appeared; walker logged a roughly 17-second raw-scene transport scan and a refused plane-change interaction for object 56230.
- The route comparator considered a Varrock Teleport bank leg, but the observed execution involved Lumbridge. Do not label the Varrock proposal an executed teleport. Whether the Lumbridge bank visit was necessary remains unverified.
- Acceptance: verify exit portal recognition and inventory/bank state, avoid unintended mine-to-bank or bank-to-task detours, prevent teleport resource purchases without a beneficial destination, bound transport retry latency, and verify correct eventual return to Aubury without repeated NPC/object clicks.
- Add focused regression tests for confirmed source-level defects; validate in the running client only after a controlled safe deployment boundary. Preserve active gameplay and all existing artwork.
