# KSP logout breaks

The automation plugins expose **Break Handler** in their own configuration panel, using Account Builder's settings and defaults:

| Setting | Default | Range |
| --- | --- | --- |
| Do Breaks | Enabled | On/off |
| Break After Min (min) | 45 | 5–300 |
| Break After Max (min) | 90 | 5–300 |
| Break Duration Min (min) | 5 | 1–180 |
| Break Duration Max (min) | 15 | 1–180 |

Settings are saved separately for each plugin. Reversed minimum/maximum values are normalized. Play time stops counting while logged out or paused by another tool.

The shared controller pauses action loops, clears the walker target, waits for combat to end plus an 11-second grace period, and logs out. Active trade screens finish before the controller claims the pause. Break duration starts once logout is observed. Failed logout attempts retry every three seconds.

During the break, standalone AutoLogin is stopped. Afterward, LoginManager uses the active profile and the previous world, with a ten-second login retry interval. The controller handles the welcome-screen Play button and releases its pause only after the game scene is ready. Without an active profile, manual login can complete recovery. Disabling Do Breaks during a break ends the remaining wait and completes recovery. Stopping or hot-unloading the plugin cancels its timer and releases only a pause it acquired.

Integrated plugins: GE Looter, Trade Receiver, Bone/Ash collector, F2P Processing Factory, AIO Fighter, Auto Run, Bones to Bananas, Bryophyta, Direct Fishing, F2P Gathering Profit, F2P High Alch Trader, Flesh Crawlers, Jewelry Crafter, Karamja Fishing, Kebab Buyer, Mad Cow, Mug Filler, Smart Smelter, Smart Superheat, Chopper, Auto Mining, and Bank Organizer. Bank Organizer schedules breaks only during a requested run. Account Builder retains its existing schedule; its login helper now respects another plugin's break.

Boss Gear, Bond Goal, Disable Render, Robes of Ruin's guide, and Support are passive utilities, so they do not schedule account logouts.

Regression tests are stored as `.java.txt` under `.github/tests/breaks` so the production Source Loader does not compile Mockito dependencies. Copy the test to a temporary `.java` file, compile against the production classes, Microbot 2.6.29, Mockito 5.18.0, Byte Buddy 1.17.5 and Objenesis 3.3, then run it with Byte Buddy's agent enabled and the 22 integrated config class names as arguments. The test uses RuneLite's actual ConfigManager descriptor builder to verify each panel's section and inherited settings, and mocks game APIs to exercise break transitions without waiting for real minutes.
