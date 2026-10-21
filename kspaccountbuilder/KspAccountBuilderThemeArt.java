package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Production HUD decoration, drawn in RuneLite at runtime.
 * No screenshot crops, placeholder statistics or concept/mockup images.
 * Every theme owns a distinct graphic composition and decorative silhouette.
 */
final class KspAccountBuilderThemeArt
{
    private KspAccountBuilderThemeArt() {}

    static void paint(Graphics2D graphics, KspAccountBuilderOverlayStyle style,
                      int width, int height, Color accent, long frameTime)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            switch (style)
            {
                case NEXUS: nexus(g, width, height, frameTime); break;
                case PRESTIGE: prestige(g, width, height); break;
                case COMPACT: compact(g, width, height, accent); break;
                case HORIZON: horizon(g, width, height, accent); break;
                case VERTICAL_PRO: vertical(g, width, height, frameTime); break;
                case ROADMAP: roadmap(g, width, height); break;
                case ELEMENTAL: elemental(g, width, height, accent, frameTime); break;
                case CLASSIC_PLUS: classic(g, width, height); break;
                default: break;
            }
        }
        finally { g.dispose(); }
    }

    private static void nexus(Graphics2D g, int w, int h, long millis)
    {
        Color violet = new Color(168, 63, 255), shine = new Color(232, 176, 255);
        // Luminous jagged crystal perimeter: never cover the live text area.
        for (int y = 27; y < h - 26; y += 28)
        {
            int height = 17 + (y % 3) * 2;
            gem(g, 2, y, 13, height, violet, 0.82f);
            gem(g, w - 15, y + 8, 12, height, violet, 0.82f);
        }
        for (int x = 31; x < w - 20; x += 28)
        {
            gem(g, x, h - 16, 19, 15, violet, 0.8f);
        }
        gem(g, 3, 3, 28, 36, violet, 1f);
        gem(g, w - 34, 4, 26, 35, violet, 1f);
        double beat = .5 + .5 * Math.sin(millis / 650d);
        g.setStroke(new BasicStroke(2.4f));
        g.setColor(new Color(207, 138, 255, (int)(105 + 115 * beat)));
        g.drawRoundRect(7, 7, w - 15, h - 15, 18, 18);
        g.setColor(shine);
        for (int y = 29; y < h; y += 61)
        {
            g.fillOval(8, y, 3, 3);
            g.fillOval(w - 12, y + 9, 3, 3);
        }
    }

    private static void prestige(Graphics2D g, int w, int h)
    {
        Color bronze = new Color(146, 92, 37), gold = new Color(242, 196, 93);
        // Carved gilded crest and vine-wrapped perimeter.
        shield(g, 10, 3, 38, 49, bronze, gold);
        for (int y = 62; y < h - 15; y += 36)
        {
            leaves(g, 9, y, true);
            leaves(g, w - 9, y, false);
        }
        for (int x = 49; x < w - 30; x += 48)
        {
            leaf(g, x, h - 8, -Math.PI / 2, 15, new Color(73, 118, 53));
            leaf(g, x + 13, h - 8, Math.PI / 2, 13, new Color(119, 145, 61));
        }
        g.setColor(gold);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(37, 8, w - 24, 8);
        g.drawLine(22, h - 10, w - 22, h - 10);
        rivet(g, w - 22, 18, gold);
        rivet(g, 22, h - 16, gold);
        rivet(g, w - 22, h - 16, gold);
    }

    private static void compact(Graphics2D g, int w, int h, Color accent)
    {
        g.setColor(new Color(12, 206, 232, 200));
        g.setStroke(new BasicStroke(2));
        g.drawLine(5, 5, 30, 5); g.drawLine(5, 5, 5, 28);
        g.drawLine(w - 30, 5, w - 6, 5); g.drawLine(w - 6, 5, w - 6, 28);
        g.drawLine(5, h - 28, 5, h - 6); g.drawLine(5, h - 6, 30, h - 6);
        g.drawLine(w - 30, h - 6, w - 6, h - 6); g.drawLine(w - 6, h - 28, w - 6, h - 6);
        g.setColor(accent);
        g.fillRoundRect(w - 18, 13, 5, 8, 3, 3);
    }

    private static void horizon(Graphics2D g, int w, int h, Color accent)
    {
        // Slim double-rim instrument HUD; inset rails and diagonal endcaps.
        g.setColor(new Color(54, 203, 243, 190));
        g.setStroke(new BasicStroke(2));
        g.drawLine(38, 2, w - 38, 2);
        g.drawLine(38, h - 3, w - 38, h - 3);
        for (int x : new int[]{11, w - 20})
        {
            g.setColor(accent);
            g.fillRect(x, 43, 8, 33);
            g.setColor(new Color(12, 50, 68));
            g.fillRect(x + 2, 47, 4, 25);
        }
        for (int x = 56; x < w - 20; x += 70)
        {
            g.setColor(new Color(68, 178, 219, 70));
            g.drawLine(x, h - 6, x + 17, h - 6);
        }
    }

    private static void vertical(Graphics2D g, int w, int h, long millis)
    {
        // A genuine vertical app-rail, not another surrounding rectangle.
        g.setPaint(new GradientPaint(0, 40, new Color(28, 13, 67),
                38, 340, new Color(11, 44, 89)));
        g.fillRoundRect(2, 45, 37, h - 57, 8, 8);
        g.setColor(new Color(135, 76, 246));
        g.setStroke(new BasicStroke(2.4f));
        g.drawLine(43, 55, 43, h - 13);
        for (int i = 0; i < 5; i++)
        {
            int y = 87 + i * 50;
            g.setColor(new Color(30, 22, 67));
            g.fillOval(8, y - 14, 26, 26);
            g.setColor(i == 0 ? new Color(189, 98, 255) : new Color(64, 171, 234));
            g.setStroke(new BasicStroke(i == 0 ? 3 : 1.5f));
            g.drawOval(8, y - 14, 26, 26);
            g.drawLine(16, y - 2, 26, y - 2);
            g.drawLine(21, y - 8, 21, y + 4);
        }
        gem(g, 7, 7, 29, 36, new Color(166, 74, 252), 1f);
        g.setColor(new Color(154, 98, 254, 120));
        g.fillOval(15, (int)(h - 36 - 3 * Math.sin(millis / 900d)), 12, 12);
    }

    private static void roadmap(Graphics2D g, int w, int h)
    {
        // Medieval guild ledger: layered title crest + six gilded fixings.
        shield(g, 6, 3, 39, 49, new Color(114, 75, 37), new Color(245, 204, 102));
        Color gold = new Color(217, 159, 66);
        g.setColor(gold);
        g.setStroke(new BasicStroke(3));
        g.drawLine(53, 8, w - 15, 8);
        for (int x : new int[]{14, w / 2, w - 14})
            for (int y : new int[]{15, h - 13})
                rivet(g, x, y, gold);
        for (int i = 0; i < 6; i++)
        {
            int x = 48 + i * 52;
            g.setColor(new Color(224, 166, 79, 70));
            g.fillRoundRect(x, h - 8, 34, 3, 3, 3);
        }
    }

    private static void elemental(Graphics2D g, int w, int h, Color accent, long millis)
    {
        // Oversized faceted ice/crystal frame, including animated highlights.
        for (int y = 18; y < h - 14; y += 36)
        {
            int shift = y % 3;
            gem(g, 0, y, 20 + shift, 39, accent, .9f);
            gem(g, w - 23, y + 11, 22, 38, accent, .9f);
        }
        for (int x = 18; x < w - 10; x += 30)
        {
            gem(g, x, 1, 28, 22, accent, .9f);
            gem(g, x, h - 22, 30, 22, accent, .9f);
        }
        gem(g, 9, 2, 47, 57, accent, 1f);
        gem(g, w - 57, h - 63, 47, 60, accent, 1f);
        for (int i = 0; i < 13; i++)
        {
            int px = 20 + (int)((millis / 22 + i * 97L) % (w - 40));
            int py = 40 + (i * 57) % (h - 71);
            g.setColor(new Color(189, 245, 255, 55 + (i % 5) * 20));
            g.fillOval(px, py, 2 + i % 3, 2 + i % 3);
        }
    }

    private static void classic(Graphics2D g, int w, int h)
    {
        Color silver = new Color(139, 151, 163), bronze = new Color(172, 129, 71);
        shield(g, 7, 1, 47, 56, new Color(48, 57, 68), silver);
        g.setColor(silver);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(53, 7, w - 22, 7);
        g.drawLine(17, h - 9, w - 17, h - 9);
        for (int x : new int[]{11, w - 11})
            for (int y : new int[]{18, h - 18})
                rivet(g, x, y, bronze);
        // Riveted dark-steel faceplate corners.
        g.setColor(new Color(106, 115, 125, 170));
        g.fillRect(w - 37, 8, 22, 5);
        g.fillRect(15, h - 13, 27, 5);
    }

    private static void gem(Graphics2D g, int x, int y, int w, int h, Color color, float opacity)
    {
        Graphics2D d = (Graphics2D)g.create();
        try
        {
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            Path2D diamond = new Path2D.Double();
            diamond.moveTo(x + w * .5, y);
            diamond.lineTo(x + w, y + h * .32);
            diamond.lineTo(x + w * .72, y + h);
            diamond.lineTo(x + w * .2, y + h * .77);
            diamond.lineTo(x, y + h * .27);
            diamond.closePath();
            d.setPaint(new GradientPaint(x, y, color.brighter(), x + w, y + h, color.darker().darker()));
            d.fill(diamond);
            d.setColor(new Color(231, 252, 255, 175));
            d.setStroke(new BasicStroke(1.1f));
            d.draw(diamond);
            d.drawLine(x + w / 2, y, x + w / 2, y + h * 3 / 4);
            d.drawLine(x + w / 2, y + h * 3 / 4, x + w, y + h / 3);
        }
        finally { d.dispose(); }
    }

    private static void shield(Graphics2D g, int x, int y, int w, int h, Color body, Color trim)
    {
        Path2D path = new Path2D.Double();
        path.moveTo(x + w * .5, y);
        path.lineTo(x + w, y + h * .15);
        path.lineTo(x + w * .89, y + h * .69);
        path.lineTo(x + w * .5, y + h);
        path.lineTo(x + w * .11, y + h * .69);
        path.lineTo(x, y + h * .15);
        path.closePath();
        g.setPaint(new GradientPaint(x, y, body.brighter(), x + w, y + h, body.darker()));
        g.fill(path);
        g.setColor(trim);
        g.setStroke(new BasicStroke(3));
        g.draw(path);
        g.drawLine(x + w / 2, y + 7, x + w / 2, y + h - 13);
        g.drawLine(x + 8, y + 17, x + w - 8, y + 17);
    }

    private static void leaves(Graphics2D g, int x, int y, boolean left)
    {
        int dir = left ? 1 : -1;
        leaf(g, x, y, left ? -.7 : Math.PI + .7, 21, new Color(70, 124, 46));
        leaf(g, x + dir * 5, y + 12, left ? .8 : Math.PI - .8, 18, new Color(103, 148, 63));
        leaf(g, x + dir * 1, y + 23, left ? -.4 : Math.PI + .4, 17, new Color(66, 100, 42));
    }

    private static void leaf(Graphics2D g, int x, int y, double angle, int size, Color c)
    {
        Graphics2D d = (Graphics2D)g.create();
        try
        {
            d.translate(x, y);
            d.rotate(angle);
            d.setPaint(new GradientPaint(0, 0, c.brighter(), size, 0, c.darker()));
            d.fillOval(0, -size / 4, size, Math.max(3, size / 2));
            d.setStroke(new BasicStroke(1));
            d.setColor(new Color(191, 169, 85, 150));
            d.drawLine(0, 0, size - 3, 0);
        }
        finally { d.dispose(); }
    }

    private static void rivet(Graphics2D g, int x, int y, Color metal)
    {
        g.setColor(new Color(31, 30, 26));
        g.fillOval(x - 5, y - 5, 10, 10);
        g.setColor(metal);
        g.fillOval(x - 4, y - 4, 8, 8);
        g.setColor(Color.WHITE);
        g.fillOval(x - 2, y - 3, 2, 2);
    }
}
