# Account Builder quest regression checks

`QuestRegressionTest.java` exercises the actual compiled quest scripts using mocked client state. It lives outside the plugin source folders so the source loader does not compile Mockito test code.

Coverage includes partial Sheep Shearer delivery, inventory/bank/active-offer purchase budgets, duplicate-offer prevention, collection to bank, unnoted withdrawal, all four X Marks the Spot dig tiles and exact-tile gating, Imp Catcher tower floors, Restless Ghost amulet/skull recovery, full inventory preparation, cutscene guarding, and game-state completion.

## Validation environment

- Account Builder + `kspmule`, `kspsupport`, and `kspbank` sources compiled with `javac --release 11` against the released `microbot-2.6.29.jar`.
- Lombok supplied on the compile classpath and processor path.
- A build-only `PluginConstants.KSP` prefix was supplied for the Hub/Source Loader generated class absent from the standalone client JAR.
- Tests use Mockito Core 5.14.2, Byte Buddy and Byte Buddy Agent 1.15.4, and Objenesis 3.3.
- Launch with `-javaagent:<byte-buddy-agent.jar>` so static mocking does not depend on dynamic JVM attachment.

With compiled plugin classes and dependencies available, compile this test with the plugin classes, client JAR, and Mockito Core on the classpath. Run `QuestRegressionTest` with those plus Byte Buddy, its agent, and Objenesis on the classpath. The expected result is `PASS: 38 quest regression checks`.

These are mocked regression checks, not a claim of live quest completion. In-game validation should run each quest from both an unstarted and interrupted state, with some required items banked and some missing, and verify both Run Single Quest and normal task selection.

## Quest-stage references

Stage mappings, item identities, dialogue choices, and route coordinates were cross-checked against the Quest Helper source at commit `75b623a6bc14237831fddc10b44765c0910a4eb0`:

- [Sheep Shearer](https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/sheepshearer/SheepShearer.java)
- [X Marks the Spot](https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/xmarksthespot/XMarksTheSpot.java)
- [The Restless Ghost](https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/therestlessghost/TheRestlessGhost.java)
- [Imp Catcher](https://github.com/Zoinkwiz/quest-helper/blob/75b623a6bc14237831fddc10b44765c0910a4eb0/src/main/java/com/questhelper/helpers/quests/impcatcher/ImpCatcher.java)

The production scripts are independent implementations using Microbot's existing bank, GE, dialogue, NPC, and walker APIs. Quest Helper is not a runtime dependency.
