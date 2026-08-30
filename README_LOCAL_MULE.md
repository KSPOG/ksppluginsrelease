# KSP Local Mule

This test implementation adds localhost-only mule coordination between KSP worker plugins and `KSPTradeReceiver`.

## Current scope

- Communication is bound to `127.0.0.1` only.
- Multiple worker jobs are queued by the receiver.
- The mule can remain logged out while idle.
- A worker `READY` request causes the receiver to log in through Microbot's active profile.
- The receiver stays online while any worker is queued or active.
- It logs out only after the final transfer is complete and the configured quiet period expires.
- A new worker arriving during the quiet period cancels the pending logout.

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

The receiver plugin is enabled on the RuneLite login screen so the localhost listener remains alive while the mule account is logged out.

## Queue flow

```text
Worker A READY ----\
Worker B READY -----+--> KSP Trade Receiver queue
Worker C READY ----/            |
                               login mule
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

Account names and failure messages are URL-safe Base64 encoded in protocol fields.

## Notes

This is intentionally a localhost first version. The worker/server protocol is isolated from the RuneScape interaction code so it can later be moved to a LAN or hosted coordinator without rewriting the actual mule/trade state machines.
