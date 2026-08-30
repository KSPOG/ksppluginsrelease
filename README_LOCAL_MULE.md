# KSP Local Mule

This test implementation adds localhost-only mule coordination between KSP worker plugins and `KSPTradeReceiver`.

## Current scope

- Communication is bound to `127.0.0.1` only.
- Multiple worker jobs are queued by the receiver.
- Worker account names are detected automatically from each worker client's local RuneScape player.
- The mule account name is detected automatically from the Trade Receiver client's local RuneScape player; no mule display-name setting is required.
- A queued job remains `QUEUED` until the receiver has positively discovered its own mule display name, preventing an `ACTIVE` job with an unknown trade target.
- The receiver preserves its last successfully discovered mule name across transient null-player snapshots during login/world hopping.
- The mule can remain logged out while idle.
- A worker `READY` request causes the receiver to log in through Microbot's active profile.
- The receiver stays online while any worker is queued or active.
- It logs out only after the final transfer is complete and the configured quiet period expires.
- A new worker arriving during the quiet period cancels the pending logout.

## Automatic account identity

No RuneScape display names need to be entered for the localhost mule handshake.

```text
Worker client                           Trade Receiver client
-------------                           ---------------------
getLocalPlayer().getName()              getLocalPlayer().getName()
          |                                      |
          | READY(worker name)                   | auto-detect mule name
          +---------------------> localhost <----+
                                                 |
                                      ACTIVE(mule name, world, tile)
          <--------------------------------------+
          |
worker trades exactly the returned mule name
```

If the receiver is logged out when a worker becomes ready, the job remains queued while the receiver logs in. Once RuneLite exposes the receiver's local player name, the job becomes active and that exact display name is returned to the worker.

The receiver still uses Microbot's **active login profile** to perform the automatic login. The saved login/profile name does not need to match the RuneScape display name; the actual display name is discovered after login from the game client.

## High Alch Trader settings

Enable **Local Mule** in `KSP High Alch Trader` and configure:

- **Start Transfer At**: total GP (inventory + bank cache) that triggers a transfer.
- **Keep In Bank**: protected bank reserve. The reserve guard keeps this stack in the bank and prevents the existing trader's bank-capital fallback from consuming it.
- **Keep Trading Capital**: spendable GP restored to the worker after the transfer. The normal trader **Coin reserve** remains a minimum floor.
- **Receiver Port**: default `17841`.
- **Mule Timeout**: maximum duration of a queued/active mule request.

Transfer amount is:

```text
Total coins - Keep In Bank - max(Keep Trading Capital, Coin reserve)
```

Example:

```text
Total GP             2,500,000
Keep In Bank           300,000
Keep Trading Capital   500,000
Transfer             1,700,000
```

## Trade Receiver settings

Enable **Local Mule** in `KSP Trade Receiver` and configure:

- **Local Port**: must match the worker port; default `17841`.
- **Logout Quiet Time**: how long an empty queue remains online before logout.
- **Worker Timeout**: stale-job timeout.
- **Login For Jobs**: automatically login when the first worker queues.
- **Logout When Done**: automatically logout only after all queued/active workers finish.
- **Bank After Transfer**: deposit received inventory before moving to the next worker.

The receiver plugin remains loaded while the game account is at the RuneLite login screen, so its localhost listener can receive a worker request and invoke Microbot's login manager.

## Queue flow

```text
Worker A READY ----\
Worker B READY -----+--> KSP Trade Receiver queue
Worker C READY ----/            |
                               login mule
                                  |
                          detect mule name
                                  |
                           process Worker A
                                  |
                                bank
                                  |
                           process Worker B
                                  |
                                bank
                                  |
                           process Worker C
                                  |
                                bank
                                  |
                           quiet countdown
                                  |
                                logout
```

## Protocol

The current test protocol is dependency-free UTF-8 TCP over loopback:

```text
READY   requestId account coins world
STATUS  requestId
CANCEL  requestId
PING
```

`READY` carries the automatically detected worker display name. `ACTIVE` responses carry the automatically detected mule display name, world, tile and exact expected coin amount. Account names and failure messages are URL-safe Base64 encoded in protocol fields.

## Notes

This is intentionally a localhost first version. The worker/server protocol is isolated from the RuneScape interaction code so it can later be moved to a LAN or hosted coordinator without rewriting the actual mule/trade state machines.
