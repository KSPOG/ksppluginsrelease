# KSP F2P Gathering Profit v0.0.3

Repository-compatible version of the uploaded v0.0.2 source.

## Compatibility changes
- Uses the repository's `PluginDescriptor.Mocrosoft` naming prefix.
- Uses shared `KspVerifiedBank` banking, including the booth-first banking behavior.
- Fixes current Microbot `Rs2Equipment.all()` stream usage.
- Uses current world-view player access for competition/threat checks.
- Makes pickaxe/axe selection respect Mining/Woodcutting requirements instead of blindly withdrawing the highest tier in the bank.
- Stores the plugin as flat Java sources under `kspf2pgatheringprofit/`, matching `ksppluginsrelease` layout.

## Supported targets
- Mining: Iron, Coal, Mithril, Adamantite, Runite
- Woodcutting: Oak, Willow, Yew
- Fishing: Trout/Salmon, Lobster, Tuna/Swordfish

## Build

```powershell
.\gradlew.bat build -PpluginList=KspF2pGatheringProfitPlugin
```

A live in-game test is still required for route quality and resource-specific timing.
