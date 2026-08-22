KSP Karamja Fishing v0.1.1

Modes:
- Tuna & Swordfish (Harpoon)
- Lobster (Cage)

Route:
- Port Sarim -> Musa Point using Seaman Lorris / Seaman Thresnor / Captain Tobias.
- Fishing target: 2924, 3178, 0.
- Full inventory -> Customs officer -> Port Sarim.
- Uses Microbot's Port Sarim deposit box at 3045, 3234, 0.
- Deposits everything except Harpoon, Lobster pot, and Coins.

Travel interaction tries:
- Outbound: Musa Point, Travel, Pay-fare, Pay-Fare
- Return: Port Sarim, Travel, Pay-fare, Pay-Fare

The plugin requires at least 60 coins before leaving Port Sarim so it cannot intentionally start a trip
without enough fare for the 30-coin return journey.

The destination-name options are included because Microbot's RuneLite shortest-path ship data currently identifies
Captain Tobias with "Musa Point" and Customs officer with "Port Sarim".

0.1.1: Removed unsupported Lombok @Getter/@RequiredArgsConstructor for KSP runtime source-loader compatibility.
