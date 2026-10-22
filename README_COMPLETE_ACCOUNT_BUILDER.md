# Complete KSP Account Builder source archive

Source: https://github.com/KSPOG/ksppluginsrelease
Fixed source commit: 036f04eaece43076f47f7003cb71047f8f1b8911
Historical cutoff from the request: 2026-10-21 21:10 Europe/Brussels (2026-10-21T19:10:00Z)

This archive contains the ENTIRE kspaccountbuilder directory at the fixed source commit, not just files modified after the cutoff.
Total source files in kspaccountbuilder: 138 (137 Java files + 1 PNG).
Includes .github/workflows/account-builder-build.yml for CI reference.

These Java sources depend on classes and libraries from RuneLite/Microbot. This ZIP is a full KSP Account Builder source directory, not an independently buildable RuneLite client or precompiled JAR.

To inspect pre-cutoff Account Builder version (different from this current snapshot), see:
https://github.com/KSPOG/ksppluginsrelease/tree/634424cf776339539d49daa84f3bbeb20093de1d/kspaccountbuilder

Complete included directory contents:
kspaccountbuilder/KSPAccountBuilderOverlay.java
kspaccountbuilder/KspAccountBuilderConfig.java
kspaccountbuilder/KspAccountBuilderMode.java
kspaccountbuilder/KspAccountBuilderPanel.java
kspaccountbuilder/KspAccountBuilderPlugin.java
kspaccountbuilder/KspAccountBuilderScript.java
kspaccountbuilder/KspAccountBuilderSkillingAntiban.java
kspaccountbuilder/KspAccountBuilderStartSkill.java
kspaccountbuilder/KspAccountPlayTimeCache.java
kspaccountbuilder/KspAccountTaskCache.java
kspaccountbuilder/KspAgentSnapshotService.java
kspaccountbuilder/KspArcaneCodexRenderer.java
kspaccountbuilder/KspBankMode.java
kspaccountbuilder/KspClassicCinematicHud.java
kspaccountbuilder/KspClassicHudInput.java
kspaccountbuilder/KspDeveloperMiningTarget.java
kspaccountbuilder/KspDeveloperSmeltingTarget.java
kspaccountbuilder/KspDeveloperTask.java
kspaccountbuilder/KspDeveloperWoodcuttingTarget.java
kspaccountbuilder/KspManagedScript.java
kspaccountbuilder/KspQuestTask.java
kspaccountbuilder/KspSingleSkillConfigTarget.java
kspaccountbuilder/KspSingleSkillTarget.java
kspaccountbuilder/KspTaskDebug.java
kspaccountbuilder/KspTrainSingleSkillTask.java
kspaccountbuilder/KspWalkerGuard.java
kspaccountbuilder/KspWorldMapGuard.java
kspaccountbuilder/TradeUnlock.java
kspaccountbuilder/arcane_codex_background.png
kspaccountbuilder/ksputil/KspBankWidgetHelper.java
kspaccountbuilder/ksputil/KspLiveScene.java
kspaccountbuilder/ksputil/experiencelamps/KspExperienceLampScript.java
kspaccountbuilder/ksputil/randomevents/KspRandomEventSolver.java
kspaccountbuilder/task/taskwalker/TaskWalker.java
kspaccountbuilder/tasks/questing/cooksassistant/cookscript/CooksScript.java
kspaccountbuilder/tasks/questing/cooksassistant/cookscript/CooksState.java
kspaccountbuilder/tasks/questing/cooksassistant/reqs/Items.java
kspaccountbuilder/tasks/questing/goblindip/dialogue/GobDialogue.java
kspaccountbuilder/tasks/questing/goblindip/goblindipscript/GobScript.java
kspaccountbuilder/tasks/questing/goblindip/goblindipscript/GobState.java
kspaccountbuilder/tasks/questing/goblindip/reqs/GobReqs.java
kspaccountbuilder/tasks/questing/impcatcher/impscript/ImpScript.java
kspaccountbuilder/tasks/questing/impcatcher/reqs/ImpRequirements.java
kspaccountbuilder/tasks/questing/romeoandjuliet/romeinv/Inv.java
kspaccountbuilder/tasks/questing/romeoandjuliet/romeoareas/Area.java
kspaccountbuilder/tasks/questing/romeoandjuliet/romeoareas/Areas.java
kspaccountbuilder/tasks/questing/romeoandjuliet/romeoscript/RomeoScript.java
kspaccountbuilder/tasks/questing/romeoandjuliet/romeoscript/RomeoState.java
kspaccountbuilder/tasks/questing/runemyst/RuneMystScript.java
kspaccountbuilder/tasks/questing/runemyst/RuneMystState.java
kspaccountbuilder/tasks/questing/shared/QuestRequirement.java
kspaccountbuilder/tasks/questing/shared/QuestTaskState.java
kspaccountbuilder/tasks/questing/shared/SimpleQuestScript.java
kspaccountbuilder/tasks/questing/sheepshearer/reqs/SheepRequirements.java
kspaccountbuilder/tasks/questing/sheepshearer/sheepscript/SheepScript.java
kspaccountbuilder/tasks/questing/therestlessghost/ghostscript/GhostScript.java
kspaccountbuilder/tasks/questing/xmarksthespot/reqs/XMarksRequirements.java
kspaccountbuilder/tasks/questing/xmarksthespot/xmarksscript/XMarksScript.java
kspaccountbuilder/tasks/skilling/combat/melee/areas/CombatAreas.java
kspaccountbuilder/tasks/skilling/combat/melee/areas/CowArea.java
kspaccountbuilder/tasks/skilling/combat/melee/equipment/amulet/Amulet.java
kspaccountbuilder/tasks/skilling/combat/melee/equipment/armour/Armour.java
kspaccountbuilder/tasks/skilling/combat/melee/equipment/cape/Capes.java
kspaccountbuilder/tasks/skilling/combat/melee/equipment/weapon/Weapons.java
kspaccountbuilder/tasks/skilling/combat/melee/food/Food.java
kspaccountbuilder/tasks/skilling/combat/melee/loot/alkharidwarriotloot/WarriorLoot.java
kspaccountbuilder/tasks/skilling/combat/melee/loot/cowloot/CowLoot.java
kspaccountbuilder/tasks/skilling/combat/melee/loot/hillgiantloot/HillGiantLoot.java
kspaccountbuilder/tasks/skilling/combat/melee/loot/mossgiantloot/MossGiantLoot.java
kspaccountbuilder/tasks/skilling/combat/melee/meleescript/CombatState.java
kspaccountbuilder/tasks/skilling/combat/melee/meleescript/MeleeLootingHelper.java
kspaccountbuilder/tasks/skilling/combat/melee/meleescript/MeleeScript.java
kspaccountbuilder/tasks/skilling/combat/melee/npc/NPC.java
kspaccountbuilder/tasks/skilling/cooking/areas/Area.java
kspaccountbuilder/tasks/skilling/cooking/areas/Areas.java
kspaccountbuilder/tasks/skilling/cooking/cookingscript/CookingScript.java
kspaccountbuilder/tasks/skilling/cooking/cookingscript/CookingState.java
kspaccountbuilder/tasks/skilling/cooking/levels/CookLevels.java
kspaccountbuilder/tasks/skilling/crafting/areas/Area.java
kspaccountbuilder/tasks/skilling/crafting/areas/Areas.java
kspaccountbuilder/tasks/skilling/crafting/clevels/CraftingLevels.java
kspaccountbuilder/tasks/skilling/crafting/craftingscript/CraftingScript.java
kspaccountbuilder/tasks/skilling/crafting/inventory/CraftInventory.java
kspaccountbuilder/tasks/skilling/firemaking/firemakingscript/FireMakingScript.java
kspaccountbuilder/tasks/skilling/firemaking/firemakingscript/FireMakingState.java
kspaccountbuilder/tasks/skilling/firemaking/fmarea/FireArea.java
kspaccountbuilder/tasks/skilling/firemaking/loglevels/LogsLvl.java
kspaccountbuilder/tasks/skilling/fishing/areas/Areas.java
kspaccountbuilder/tasks/skilling/fishing/fishingscript/FishingScript.java
kspaccountbuilder/tasks/skilling/fishing/fishingscript/FishingState.java
kspaccountbuilder/tasks/skilling/fishing/helper/KaramjaTravelHelper.java
kspaccountbuilder/tasks/skilling/fishing/levelreqfishing/LevelReqs.java
kspaccountbuilder/tasks/skilling/fishing/needed/Inventory.java
kspaccountbuilder/tasks/skilling/mining/areas/Areas.java
kspaccountbuilder/tasks/skilling/mining/equiplevels/PickaxeEquip.java
kspaccountbuilder/tasks/skilling/mining/levelreqmining/MiningReq.java
kspaccountbuilder/tasks/skilling/mining/miningscript/MiningScript.java
kspaccountbuilder/tasks/skilling/mining/miningscript/MiningState.java
kspaccountbuilder/tasks/skilling/mining/rocklevel/RockLevel.java
kspaccountbuilder/tasks/skilling/runeessence/inv/Equip.java
kspaccountbuilder/tasks/skilling/runeessence/reqs/EssLevel.java
kspaccountbuilder/tasks/skilling/runeessence/runeessscript/EssenceMining.java
kspaccountbuilder/tasks/skilling/runeessence/runeessscript/EssenceState.java
kspaccountbuilder/tasks/skilling/selling/GEArea.java
kspaccountbuilder/tasks/skilling/selling/buyscript/Buy.java
kspaccountbuilder/tasks/skilling/selling/buyscript/BuyScript.java
kspaccountbuilder/tasks/skilling/selling/buyscript/BuyState.java
kspaccountbuilder/tasks/skilling/selling/gearea/GEArea.java
kspaccountbuilder/tasks/skilling/selling/sell/SellList.java
kspaccountbuilder/tasks/skilling/selling/sellscript/SellScript.java
kspaccountbuilder/tasks/skilling/selling/sellscript/SellState.java
kspaccountbuilder/tasks/skilling/smelting/barlevel/BarLevels.java
kspaccountbuilder/tasks/skilling/smelting/oresreq/ReqOres.java
kspaccountbuilder/tasks/skilling/smelting/smeltarea/SmeltArea.java
kspaccountbuilder/tasks/skilling/smelting/smeltscript/SmeltScript.java
kspaccountbuilder/tasks/skilling/smelting/smeltscript/SmeltState.java
kspaccountbuilder/tasks/skilling/smithing/recipes/SmithRecipe.java
kspaccountbuilder/tasks/skilling/smithing/smitharea/SmithArea.java
kspaccountbuilder/tasks/skilling/smithing/smithlevels/SmithLevels.java
kspaccountbuilder/tasks/skilling/smithing/smithscript/SmithScript.java
kspaccountbuilder/tasks/skilling/smithing/smithscript/SmithState.java
kspaccountbuilder/tasks/skilling/smithing/tool/SmithTool.java
kspaccountbuilder/tasks/skilling/woodcutting/equiplevels/AxeEquip.java
kspaccountbuilder/tasks/skilling/woodcutting/levelreqwc/WoodCuttingReq.java
kspaccountbuilder/tasks/skilling/woodcutting/treeareas/TreeAreas.java
kspaccountbuilder/tasks/skilling/woodcutting/treelevel/TreeLevel.java
kspaccountbuilder/tasks/skilling/woodcutting/woodcuttingscript/AccountBuilderForestryHandler.java
kspaccountbuilder/tasks/skilling/woodcutting/woodcuttingscript/WoodCuttingScript.java
kspaccountbuilder/tasks/skilling/woodcutting/woodcuttingscript/WoodcuttingState.java
kspaccountbuilder/tasks/stronghold/areas/Area.java
kspaccountbuilder/tasks/stronghold/areas/Areas.java
kspaccountbuilder/tasks/stronghold/inventory/InvFood.java
kspaccountbuilder/tasks/stronghold/script/SoCScript.java
kspaccountbuilder/tasks/tutorialisland/TutState.java
kspaccountbuilder/tasks/tutorialisland/TutorialIslandScript.java
kspaccountbuilder/tasks/tutorialisland/areas/TutAreas.java
kspaccountbuilder/util/autologin/AutoLoginScript.java
kspaccountbuilder/util/autologin/LoginState.java
