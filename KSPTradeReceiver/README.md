# KSP Trade Receiver

Microbot plugin for a simple receiving/mule workflow:

1. Set **Trader Name** to the exact RuneScape display name you trust.
2. When that player sends `wishes to trade with you`, the plugin responds to the request.
3. The plugin verifies the configured name again inside the first trade interface.
4. By default it refuses to accept if **your own offer contains any item**.
5. It accepts the first trade screen.
6. On the confirmation screen it reads the trade opponent component and verifies the configured name again before accepting.
7. When the inventory reaches 28/28 occupied slots, it saves the trading tile, walks to the nearest bank, deposits the inventory, closes the bank and walks back to the saved tile.

## Files

- `KSPTradeReceiverPlugin.java`
- `KSPTradeReceiverConfig.java`
- `KSPTradeReceiverScript.java`
- `KSPTradeReceiverOverlay.java`

## Recommended configuration

- **Trader Name:** exact account name
- **Respond To Requests:** ON
- **Require Empty Own Offer:** ON
- **Accept First Screen:** ON
- **Accept Confirmation:** ON
- **Bank When Full:** ON
- **Return To Trade Tile:** ON

## Behaviour and safety

The plugin deliberately does not accept a first-stage trade merely because *some* trade interface is open. The configured player must be present in the trade interface widget tree. The second stage uses `InterfaceID.Tradeconfirm.TRADEOPPONENT` and requires an exact normalized name match.

`Require Empty Own Offer` inspects `InterfaceID.Trademain.YOUR_OFFER` and blocks acceptance if an item is present. This is enabled by default because the intended workflow is receiving items, not giving items away.

The incoming request expires after the configured timeout. Requests from every other name are ignored.

## Banking

Banking uses Microbot's current `Rs2Bank.walkToBankAndUseBank()`, then `Rs2Bank.depositAll()`. The return destination is the WorldPoint captured when the configured trade request/interface was seen. The walker first attempts the exact tile and falls back to arriving within one tile if the exact tile cannot be reached.

## Compatibility note

This source targets the current `chsami/Microbot` `main` API checked on 2026-08-30, including generated `InterfaceID.Trademain` / `InterfaceID.Tradeconfirm` component constants. If your local Microbot checkout is older, update it or adapt the generated interface constants before compiling.

Test the workflow with low-value items first. Trade UI behaviour is game/client-version sensitive, and a source-only package cannot prove runtime behaviour on an untested local client build.
