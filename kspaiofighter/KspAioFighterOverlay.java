package net.runelite.client.plugins.microbot.kspaiofighter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class KspAioFighterOverlay extends Overlay
{
	private final KspAioFighterConfig config;
	private final KspAioFighterScript script;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	public KspAioFighterOverlay(KspAioFighterConfig config, KspAioFighterScript script)
	{
		this.config = config;
		this.script = script;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPaint())
		{
			return null;
		}

		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(230, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
				.text("KSP AIO Fighter")
				.color(Color.WHITE)
				.build());

		String error = formatError();
		addLine("Status", !"-".equals(error) ? error : stripPrefix(Microbot.status), !"-".equals(error) ? Color.ORANGE : Color.WHITE);
		addLine("Time Running", formatDuration(script.getOverlayRunningTimeMs()));

		return panelComponent.render(graphics);
	}

	private void addLine(String left, String right)
	{
		addLine(left, right, Color.WHITE);
	}

	private void addLine(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
				.left(left)
				.right(right == null || right.isEmpty() ? "-" : right)
				.rightColor(rightColor)
				.build());
	}

	private String formatTarget()
	{
		String name = script.getOverlayTargetName();
		WorldPoint location = script.getOverlayTargetLocation();
		long ageMs = script.getOverlayLastTargetAgeMs();
		String age = ageMs < 0 ? "" : " / " + formatAge(ageMs);
		int maxHit = script.getOverlayTargetMaxHit();
		return name + " @ " + formatPoint(location) + " / max " + maxHit + age;
	}

	private String formatAttackArea()
	{
		if (!config.useAttackArea())
		{
			return "off";
		}
		return formatPoint(script.getOverlayAttackAreaTile1()) + " -> " + formatPoint(script.getOverlayAttackAreaTile2());
	}

	private String formatFood()
	{
		if (!config.useHealing())
		{
			return "off";
		}
		String foodName = config.foodName() == null ? "" : config.foodName().trim();
		if (foodName.isEmpty())
		{
			return "<none set>";
		}
		return foodName + " x" + Rs2Inventory.count(foodName, true) + " / keep " + config.foodAmount();
	}

	private String formatLoot()
	{
		if (!config.lootItems() && !config.buryBones())
		{
			return "off";
		}
		StringBuilder builder = new StringBuilder();
		if (config.lootItems())
		{
			builder.append("items");
		}
		if (config.buryBones())
		{
			if (builder.length() > 0)
			{
				builder.append(" + ");
			}
			builder.append("bones");
		}
		return builder.toString();
	}

	private String formatError()
	{
		String error = script.getOverlayLastError();
		long ageMs = script.getOverlayLastErrorAgeMs();
		if (error == null || error.equals("-") || ageMs > 30_000L)
		{
			return "-";
		}
		return trim(error, 44) + " / " + formatAge(ageMs);
	}

	private String formatPoint(WorldPoint point)
	{
		return point == null ? "-" : point.getX() + "," + point.getY() + "," + point.getPlane();
	}

	private String stripPrefix(String status)
	{
		if (status == null || status.isEmpty())
		{
			return "-";
		}
		String prefix = "KSP AIO Fighter: ";
		return status.startsWith(prefix) ? status.substring(prefix.length()) : status;
	}

	private String empty(String value, String fallback)
	{
		return value == null || value.trim().isEmpty() ? fallback : trim(value.trim(), 32);
	}

	private String trim(String value, int maxLength)
	{
		if (value == null || value.length() <= maxLength)
		{
			return value;
		}
		return value.substring(0, Math.max(0, maxLength - 3)) + "...";
	}

	private String formatDuration(long durationMs)
	{
		long totalSeconds = Math.max(0L, durationMs / 1_000L);
		long hours = totalSeconds / 3_600L;
		long minutes = (totalSeconds % 3_600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0L)
		{
			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format("%02d:%02d", minutes, seconds);
	}

	private String formatAge(long ageMs)
	{
		long seconds = Math.max(0L, ageMs / 1_000L);
		return seconds + "s ago";
	}
}
