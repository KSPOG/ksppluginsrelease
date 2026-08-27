# Smart Superheat 0.1.0 rewrite

This release replaces the accumulated GE patch stack with a compact state machine.

- `KspSmartSuperheatScript`: 375 added lines replacing 1,389 old lines.
- Removed `SmartGeTrader`: 512 lines.
- Removed `SmartSuperheatBuyQueue`: 630 lines.
- GE placement follows the Jewellery Crafter pattern: exact `GrandExchangeRequest`, real GE slot assignment, live `GrandExchangeOffer[]` reconciliation, and return-to-overview before retries.
- Nature runes remain a persistent inventory stack and all banked Nature runes are withdrawn.
- Exact material ratios are verified before casting (Steel = 1 Iron + 2 Coal per bar).
- Session-produced bars are withdrawn in noted form for sales without intentionally liquidating older banked bars.
- Bank item/note mode is changed only through the NOTE widget; quantity selector widgets are not used as mode controls.

Validation: the rewritten package compiled successfully against current `chsami/Microbot` with `./gradlew :client:compileJava` using only a test-time shim for this repository's custom `PluginConstants` class.
