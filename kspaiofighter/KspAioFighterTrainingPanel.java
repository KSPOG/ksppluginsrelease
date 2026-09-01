package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.client.ui.ColorScheme;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class KspAioFighterTrainingPanel extends JPanel
{
    private final Supplier<Boolean> enabledSupplier;
    private final Consumer<Boolean> enabledSetter;
    private final JCheckBox useLevelTargets = new JCheckBox("Use level targets");
    private boolean refreshing;

    KspAioFighterTrainingPanel(Supplier<Boolean> enabledSupplier, Consumer<Boolean> enabledSetter)
    {
        this.enabledSupplier = enabledSupplier;
        this.enabledSetter = enabledSetter;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, 4, 0));
        buildUi();
        refresh();
    }

    private void buildUi()
    {
        JLabel title = new JLabel("Training Targets", SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(new EmptyBorder(10, 2, 2, 2));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        useLevelTargets.setOpaque(false);
        useLevelTargets.setForeground(Color.WHITE);
        useLevelTargets.setAlignmentX(LEFT_ALIGNMENT);
        useLevelTargets.setToolTipText("When disabled, enabled combat skills ignore their configured 'level till' targets during the run.");
        useLevelTargets.addActionListener(e -> {
            if (refreshing) return;
            enabledSetter.accept(useLevelTargets.isSelected());
            refresh();
        });
        add(useLevelTargets);

        JLabel help = new JLabel(
            "<html><center>Off = ignore the configured level-till values and train enabled skills up to the normal level cap.</center></html>",
            SwingConstants.CENTER);
        help.setForeground(Color.LIGHT_GRAY);
        help.setBorder(new EmptyBorder(0, 4, 4, 4));
        help.setAlignmentX(CENTER_ALIGNMENT);
        add(help);
    }

    void refresh()
    {
        refreshing = true;
        try
        {
            useLevelTargets.setSelected(Boolean.TRUE.equals(enabledSupplier.get()));
        }
        finally
        {
            refreshing = false;
        }
    }
}
