# KSP F2P Clue Solver

Version: 0.1.4 (experimental)

Main class: net.runelite.client.plugins.microbot.kspf2pcluesolver.KspF2pCluePlugin

This plugin is installed through the **KSP Source Loader**, with source branch set to **dev**. It does not require a manually side-loaded JAR.

## Scope
- Only Old School RuneScape **beginner** (F2P) clues
- Detects beginner clue text and five map-widget clues
- Supports known NPC/anagram, emote and dig steps
- Supports Charlie item requests where the task is identified
- Walks to a bank, withdraws requirements and buys missing tradeable items at the Grand Exchange
- Configurable buy premium, per-item maximum and session buy commitment cap
- Waits for existing GE offers; does not place duplicate offers or abort manually placed offers
- Optional beginner casket opening
- Experimental hot/cold; stops when device feedback is inconclusive rather than guessing indefinitely

## Installation
1. Enable KSP Source Loader.
2. Select **dev** from the Source Loader branch setting.
3. Refresh sources or restart Microbot.
4. Find **KSP F2P Clue Solver** in the plugin list.
5. Remove any old standalone clue solver JAR if it is still present, to avoid duplicate plugin conflicts.

## Implementation
Java source is in kspf2pcluesolver/. The plugin entrypoint is KspF2pCluePlugin.java; the client compiles the package through Source Loader. No other KSP plugin packages are required.

## Validation and limitations
The Java source was reviewed against Microbot 2.6.43 (chsami/Microbot revision fd12241). **A live in-client compile and gameplay test have not yet been performed.** Charlie dialogue variants, emote timing, NPC interaction and hot/cold solution selection may require adjustments.

Auto-buy uses an approved item whitelist and does not intentionally buy members-only clue items.

## Changes in v0.1.3
- Automatically checks inventory for a beginner clue or beginner scroll box.
- If absent, opens the bank, withdraws a beginner clue or beginner scroll box when available, and proceeds.
- Rechecks inventory/equipment and bank before any Grand Exchange purchase of clue requirements. Does not buy items already found in the bank.
- Three-attempt bank withdrawal and equipment retry cap with four-second cooldown.
- Reldo dialogue/state control: no repeated clue-scroll reading while obtaining the strange device, with eight-second NPC/option cooldown and five-attempt cap.
- Detailed '[KSP Clue]' client.log instrumentation for state transitions, new clue steps, bank scans/withdrawals, GE purchases, errors, and periodic 30-second diagnostic heartbeat (enabled by default).
- The hot/cold location algorithm remains experimental and can require manual review.

## Changes in v0.1.4 - Shared clue dialogue handling
- One context-aware dialogue controller for all supported beginner clue conversations: Reldo, Charlie, cryptic/anagram NPCs and Uri.
- Visible dialogue choices are read directly via RuneLite's current dialogue widgets. "Yes, I do." at Reldo is explicitly recognised.
- Select-menu choices take priority over Continue prompts, and conversation menus are retried only a limited number of times.
- Recognised clue-related affirmative options are selected; unsafe or unrelated options (payment, buying, travelling, quests, "No") are not auto-selected.
- Dialogue processing takes priority over item restocking or NPC re-interactions while a clue conversation is active.
- Detailed log messages include dialogue question, options, selected item, retry count and any unrecognised menu. All are prefixed with [KSP Clue].
- The policy only handles conversations associated with the current clue step; it does not operate as a general account-wide dialogue automation system.
- Source references: OSRS Wiki Beginner full guide; Transcript:Reldo; Transcript:Charlie the Tramp; Transcript:Shantay.
