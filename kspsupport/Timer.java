package net.runelite.client.plugins.microbot.kspsupport;

import java.awt.event.ActionListener;

/**
 * Package-local Swing timer used by the KSP debug UI.
 *
 * KspDebugPanel imports both javax.swing.* and java.util.*, which makes the
 * simple name Timer ambiguous when sources are compiled independently by the
 * Source Loader. Keeping this adapter package-local makes the intended Swing
 * timer unambiguous without introducing any java.util.Timer semantics.
 */
final class Timer extends javax.swing.Timer
{
    Timer(int delay, ActionListener listener)
    {
        super(delay, listener);
    }
}
