package net.runelite.client.plugins.microbot.kspaiofighter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum KspLootOwnership
{
	LOOT_OWN("Loot Own"),
	LOOT_ALL("Loot All");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
