package net.runelite.client.plugins.microbot.kspseasonalevents;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.JOptionPane;
import java.awt.Font;
import java.awt.Color;
import java.awt.GridLayout;

final class KspSeasonalEventsPanel extends PluginPanel {
    private final KspSeasonalEventsPlugin plugin;
    private final KspHalloween2026Script script;
    private final JButton start = new JButton("Start");
    private final JButton pause = new JButton("Pause");
    private final JButton stop = new JButton("Stop");
    private final JButton reset = new JButton("Reset progress");
    private final JButton confirmLanterns = new JButton("Confirm all 8 lanterns smashed");
    private final JLabel state = new JLabel("", SwingConstants.CENTER);
    private final Timer timer;

    KspSeasonalEventsPanel(KspSeasonalEventsPlugin plugin,
                            KspHalloween2026Script script) {
        this.plugin = plugin;
        this.script = script;
        JLabel title = new JLabel("KSP Seasonal Events", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(Color.WHITE);
        add(title);
        JComboBox<KspSeasonalEvent> events = new JComboBox<>(KspSeasonalEvent.values());
        events.setEnabled(false); // More event modules will be added separately.
        add(events);
        JPanel buttons = new JPanel(new GridLayout(1, 3, 5, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.add(start);
        buttons.add(pause);
        buttons.add(stop);
        add(buttons);
        add(reset);
        reset.addActionListener(e -> { script.resetProgress(); update(); });
        add(confirmLanterns);
        confirmLanterns.setToolTipText("Only use when the game has confirmed all eight pumpkins were smashed");
        confirmLanterns.addActionListener(e -> {
            if (!script.canConfirmLanternsManually()) return;
            int answer = JOptionPane.showConfirmDialog(this,
                    "Have you already smashed ALL eight pumpkins in-game?\n"
                    + "This will resume the next Halloween event objective without smashing more.",
                    "Confirm Halloween progress", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) script.confirmLanternsManually();
            update();
        });
        state.setForeground(Color.LIGHT_GRAY);
        add(state);
        JLabel warning = new JLabel("<html><center>Halloween 2026 wiki walkthrough.<br>"
                + "Unrecognised quest interfaces pause<br>for review instead of random clicks.</center></html>",
                SwingConstants.CENTER);
        warning.setForeground(new Color(245, 182, 103));
        add(warning);
        start.addActionListener(e -> { plugin.startEvent(); update(); });
        pause.addActionListener(e -> { plugin.togglePause(); update(); });
        stop.addActionListener(e -> { plugin.stopEvent(); update(); });
        timer = new Timer(700, e -> update());
        timer.start();
        update();
    }

    private void update() {
        boolean running = script.isActive();
        start.setEnabled(!running);
        stop.setEnabled(running);
        pause.setEnabled(running);
        reset.setEnabled(!running);
        confirmLanterns.setEnabled(script.canConfirmLanternsManually());
        pause.setText(script.isPaused() ? "Resume" : "Pause");
        state.setText("<html><center>" + script.getStage() + "<br>"
                + "Lanterns: " + script.getLanternCount() + "/8 | Adults: "
                + script.getAdultsGiven() + "/5<br>"
                + "Child Spook attempts: " + script.getChildSpookAttempts()
                + "/" + script.getChildSpookCheckpoint()
                + " | Objective: "
                + (script.isChildObjectiveConfirmed() ? "Confirmed" : "Not verified")
                + "<br>" + script.getStatus() + "</center></html>");
    }

    void dispose() { timer.stop(); }
}
