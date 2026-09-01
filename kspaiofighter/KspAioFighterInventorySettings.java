package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Singleton
final class KspAioFighterInventorySettings
{
    private static final String SETUP_PREFIX = "inventorySetup.";
    private static final String ENABLED_PREFIX = "inventorySetupEnabled.";

    private final ConfigManager configManager;

    @Inject
    KspAioFighterInventorySettings(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    List<KspAioFighterInventoryItem> get(KspAioFighterGearStyle style)
    {
        String raw = configManager.getConfiguration(KspAioFighterConfig.GROUP, setupKey(style));
        if (raw == null || raw.isBlank()) return List.of();

        List<KspAioFighterInventoryItem> result = new ArrayList<>();
        for (String entry : raw.split(";"))
        {
            if (entry == null || entry.isBlank()) continue;
            String[] fields = entry.split("\\|", 5);
            if (fields.length != 5) continue;
            try
            {
                int slot = Integer.parseInt(fields[0]);
                int id = Integer.parseInt(fields[1]);
                int quantity = Integer.parseInt(fields[2]);
                boolean noted = "1".equals(fields[3]);
                String name = new String(Base64.getUrlDecoder().decode(fields[4]), StandardCharsets.UTF_8);
                if (id <= 0 || quantity <= 0 || slot < 0 || slot >= 28) continue;
                result.add(new KspAioFighterInventoryItem(id, quantity, slot, name, noted));
            }
            catch (Exception ignored)
            {
                // Ignore one malformed saved entry rather than discarding the whole setup.
            }
        }
        result.sort(Comparator.comparingInt(KspAioFighterInventoryItem::getSlot));
        return result;
    }

    List<KspAioFighterInventoryItem> saveCurrent(KspAioFighterGearStyle style)
    {
        List<Rs2ItemModel> current = Rs2Inventory.getList(item -> item != null);
        List<KspAioFighterInventoryItem> saved = current.stream()
            .filter(item -> item.getId() > 0)
            .sorted(Comparator.comparingInt(Rs2ItemModel::getSlot))
            .map(item -> new KspAioFighterInventoryItem(
                item.getId(),
                Math.max(1, item.getQuantity()),
                item.getSlot(),
                item.getName(),
                item.isNoted()))
            .collect(Collectors.toList());
        set(style, saved);
        setEnabled(style, !saved.isEmpty());
        return saved;
    }

    void set(KspAioFighterGearStyle style, List<KspAioFighterInventoryItem> items)
    {
        if (items == null || items.isEmpty())
        {
            configManager.unsetConfiguration(KspAioFighterConfig.GROUP, setupKey(style));
            return;
        }

        String encoded = items.stream()
            .filter(item -> item != null && item.getId() > 0 && item.getQuantity() > 0)
            .sorted(Comparator.comparingInt(KspAioFighterInventoryItem::getSlot))
            .map(this::encode)
            .collect(Collectors.joining(";"));
        if (encoded.isBlank()) configManager.unsetConfiguration(KspAioFighterConfig.GROUP, setupKey(style));
        else configManager.setConfiguration(KspAioFighterConfig.GROUP, setupKey(style), encoded);
    }

    void clear(KspAioFighterGearStyle style)
    {
        configManager.unsetConfiguration(KspAioFighterConfig.GROUP, setupKey(style));
        configManager.setConfiguration(KspAioFighterConfig.GROUP, enabledKey(style), false);
    }

    boolean isEnabled(KspAioFighterGearStyle style)
    {
        if (get(style).isEmpty()) return false;
        return Boolean.TRUE.equals(configManager.getConfiguration(
            KspAioFighterConfig.GROUP,
            enabledKey(style),
            Boolean.class));
    }

    void setEnabled(KspAioFighterGearStyle style, boolean enabled)
    {
        configManager.setConfiguration(
            KspAioFighterConfig.GROUP,
            enabledKey(style),
            enabled && !get(style).isEmpty());
    }

    int usedSlots(KspAioFighterGearStyle style)
    {
        return (int) get(style).stream()
            .map(KspAioFighterInventoryItem::getSlot)
            .distinct()
            .count();
    }

    private String encode(KspAioFighterInventoryItem item)
    {
        String encodedName = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(item.getName().getBytes(StandardCharsets.UTF_8));
        return item.getSlot() + "|" + item.getId() + "|" + item.getQuantity() + "|"
            + (item.isNoted() ? "1" : "0") + "|" + encodedName;
    }

    private static String setupKey(KspAioFighterGearStyle style)
    {
        return SETUP_PREFIX + style.name().toLowerCase(Locale.ROOT);
    }

    private static String enabledKey(KspAioFighterGearStyle style)
    {
        return ENABLED_PREFIX + style.name().toLowerCase(Locale.ROOT);
    }
}
