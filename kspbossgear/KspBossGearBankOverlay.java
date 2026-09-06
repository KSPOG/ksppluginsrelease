package net.runelite.client.plugins.microbot.kspbossgear;

import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.OverlayManager;

final class KspBossGearBankOverlay extends BossGearItemOverlay
{
    @Inject
    KspBossGearBankOverlay(KspBossGearConfig config, BossGearService gearService, OverlayManager overlayManager)
    {
        super(config, gearService, overlayManager);
        showOnBank();
    }

    @Override
    protected boolean acceptsParent(int parentId)
    {
        return parentId == InterfaceID.Bankmain.ITEMS || parentId == InterfaceID.SharedBank.ITEMS;
    }

    @Override
    protected boolean enabled()
    {
        return config.highlightBank();
    }
}
