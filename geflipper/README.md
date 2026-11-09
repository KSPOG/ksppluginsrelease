# KSP GE Flipper (Microbot)

This folder contains the Microbot-native GE Flipper source from the provided `geflipper.zip` (originally v1.0.5), integrated on the `dev` branch. The KSP fork declares **v1.2.82** to distinguish its plugin release from the separate Microbot-Hub GE Flipper. This is a version-label change only; it does **not** import the upstream v1.2.x implementation.

The plugin follows Flipping Copilot's live RuneLite overlay highlights, resolves GE widgets, and uses Microbot APIs for clicks, banking, input, and actions. Flipping Copilot must be installed and enabled separately.

## Price and quantity input

The supplied Microbot implementation reads the numeric price or quantity from Copilot's visible chatbox prompt and calls `Rs2GrandExchange.setChatboxValue(...)`, then `Rs2Keyboard.enter()`. This is **not** the KLite GE Flipper, and its direct-value path does not use a hardcoded `E` shortcut. The existing path has been preserved rather than replaced without verification.

## Diagnostic logging

`FlipperScript` reports step transitions and bounded 2-second heartbeats; action dispatch and outcome; chatbox price/quantity values; Enter submission; and whether the numeric prompt closed. For price-entry issues, share a client log containing `GE Flipper step` and `GE Flipper price/quantity` messages.

## Installation and verification

Source loader: select `dev`. For a normal Microbot source checkout, place these Java files in `runelite-client/src/main/java/net/runelite/client/plugins/microbot/geflipper/` and compile with `./gradlew clean build -PpluginList=FlipperPlugin` according to the supplied plugin README.

Compilation and live operation are not yet verified. The initial source does not have a public Flipping Copilot automation API; its overlay reader uses a private overlay-field adapter and may need compatibility work for newer Copilot builds.
