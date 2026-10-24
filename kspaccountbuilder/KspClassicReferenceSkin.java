package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * High-resolution reference captured directly from the approved design.
 * Self-contained and offline; all chunks are Java sources for DevLoader.
 */
final class KspClassicReferenceSkin
{
    private static volatile BufferedImage skin;
    private static volatile boolean unavailable;

    private KspClassicReferenceSkin() {}

    static BufferedImage get()
    {
        if (unavailable) return null;
        BufferedImage current = skin;
        if (current != null) return current;
        synchronized (KspClassicReferenceSkin.class)
        {
            if (skin != null || unavailable) return skin;
            try
            {
                String encoded = KspClassicReferenceData0.text()
                        + KspClassicReferenceData1.text()
                        + KspClassicReferenceData2.text()
                        + KspClassicReferenceData3.text();
                byte[] bytes = Base64.getDecoder().decode(encoded);
                BufferedImage full = ImageIO.read(new ByteArrayInputStream(bytes));
                if (full != null && full.getWidth() >= 1402 && full.getHeight() >= 702)
                    skin = full.getSubimage(7, 220, 1395, 482);
                else unavailable = true;
            }
            catch (RuntimeException | java.io.IOException ex)
            {
                unavailable = true;
            }
            return skin;
        }
    }
}
