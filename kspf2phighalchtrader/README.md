# KSP High Alch Trader — Grand Exchange-only access (v0.3.8)

Banking and trading for the High Alch Trader are **restricted to the Grand Exchange**.

- When launched **outside the GE**, the trader walks to the canonical
  `BankLocation.GRAND_EXCHANGE` via Microbot's
  `Rs2GrandExchange.walkToGrandExchange()`. This is the only intended
  offsite walking destination for the trader itself.
- When **inside the GE**, bank access uses a local NPC named `Banker`
  with a genuine **Bank** menu action. Trading uses a local
  `Grand Exchange Clerk` with the **Exchange** action.
- No bank booth or generic `Rs2Bank.openBank()` /
  `KspVerifiedBank.openBank()` lookup is used. Those can select the Cooking
  Guild booth (object 10583 at 3148,3449) and launch Microbot's can't-reach
  recovery even when the player is at the GE.
- GE entry and exit guard rejects/cancels stale walker targets outside the
  trading area; if no local Banker/Clerk is available it retries in place.
- The bank-reserve guard and optional mule service obey the same Banker-only
  restriction and do not initiate offsite bank trips. Mule recipient coordinates
  outside the GE are rejected; valid local GE meetings still work.
- Bank-open confirmation uses `Rs2Bank.isOpen()` (RuneLite `BANKMAIN`
  interface). WorldPoint checks cover the GE paved square: x=3153..3178,
  y=3477..3502, plane=0.

The startup log must show **v0.3.8** after updating the installed
source/JAR. A different version indicates that an older loaded build is
still running. The GitHub Actions workflow builds the plugin and provides
its compiled JAR as an artifact. Runtime interaction with GE bankers and
mule transfers still requires an in-client test.
