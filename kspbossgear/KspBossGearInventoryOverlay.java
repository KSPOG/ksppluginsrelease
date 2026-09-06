package net.runelite.client.plugins.microbot.kspbossgear;

import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.OverlayManager;

final class KspBossGearInventoryOverlay extends BossGearItemOverlay
{
    @Inject
    KspBossGearInventoryOverlay(KspBossGearConfig config, BossGearService gearService, OverlayManager overlayManager)
    {
        super(config, gearService, overlayManager);
        showOnInventory();
    }

    @Override
    protected boolean acceptsParent(int parentId)
    {
        return parentId == InterfaceID.Inventory.ITEMS || parentId == InterfaceID.Bankside.ITEMS;
    }

    @Override
    protected boolean enabled()
    {
        return config.highlightInventory();
    }
}
