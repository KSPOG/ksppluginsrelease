# KSP F2P Gathering Profit v0.0.3

Repository-compatible source for `KSPOG/ksppluginsrelease`.

## Features
- Automatic F2P Mining/Woodcutting/Fishing method selection by GP/XP objective
- Progressive method re-evaluation as skill levels increase
- Competition/depletion world hopping using the repository-compatible `Login.getRandomWorld(false)` flow
- Respawn-aware mining grace period
- Measured GP/h and XP/h overlay
- Wilderness Runite retreat/hop state machine
- Mining/Woodcutting tool selection respects the relevant gathering skill level
- Repository-standard verified banking through `KspVerifiedBank`
- Karamja Lobster and Tuna/Swordfish transport + Port Sarim deposit-box banking

## Supported targets
Mining: Iron, Coal, Mithril, Adamantite, Runite

Woodcutting: Oak, Willow, Yew

Fishing: Trout/Salmon, Lobster, Tuna/Swordfish

## Repository layout
These files intentionally live directly under:

`kspf2pgatheringprofit/`

The Java package remains:

`net.runelite.client.plugins.microbot.kspf2pgatheringprofit`

This matches the raw source layout used by the KSP release/source-loader repository.

## Local Microbot-Hub build
```powershell
.\gradlew.bat build -PpluginList=KspF2pGatheringProfitPlugin
```

The release repository itself does not contain the complete Microbot Gradle project, so a full Java compile still needs the matching local Microbot-Hub checkout.
