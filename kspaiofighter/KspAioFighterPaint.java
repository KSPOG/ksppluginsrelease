package net.runelite.client.plugins.microbot.kspaiofighter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.inject.Inject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class KspAioFighterPaint extends Overlay
{
	private static final int CHATBOX_WIDTH = 519;
	private static final int CHATBOX_HEIGHT = 164;
	private static final int CHATBOX_ANCHOR_HEIGHT = 142;
	private static final int TITLE_OVERHANG = 22;
	private static final int FRAME = 9;
	private static final int TITLE_HEIGHT = 34;
	private static final int ROW_HEIGHT = 22;
	private static final int ICON_SIZE = 18;

	private static final Color OUTER_SHADOW = new Color(0, 0, 0, 185);
	private static final Color STONE_TOP = new Color(73, 65, 52, 242);
	private static final Color STONE_BOTTOM = new Color(31, 27, 22, 244);
	private static final Color STONE_DARK = new Color(15, 12, 9, 248);
	private static final Color STONE_LIGHT = new Color(117, 103, 77, 235);
	private static final Color BRONZE = new Color(141, 94, 36, 245);
	private static final Color BRONZE_LIGHT = new Color(214, 159, 67, 245);
	private static final Color GOLD = new Color(247, 209, 101, 255);
	private static final Color GEM_GREEN = new Color(50, 178, 112, 230);
	private static final Color INNER_TOP = new Color(38, 34, 28, 236);
	private static final Color INNER_BOTTOM = new Color(22, 20, 17, 238);
	private static final Color ROW_TOP = new Color(49, 43, 35, 210);
	private static final Color ROW_BOTTOM = new Color(28, 25, 21, 218);
	private static final Color LABEL = new Color(235, 225, 204, 255);
	private static final Color VALUE = new Color(255, 232, 88, 255);
	private static final Color MUTED = new Color(190, 162, 105, 255);
	private static final Color GOOD = new Color(114, 235, 107, 255);
	private static final Color WARNING = new Color(255, 155, 63, 255);
	private static final Color TEXT_SHADOW = new Color(0, 0, 0, 220);

	private final KspAioFighterConfig config;
	private final KspAioFighterScript script;

	@Inject
	public KspAioFighterPaint(KspAioFighterConfig config, KspAioFighterScript script)
	{
		this.config = config;
		this.script = script;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPaint())
		{
			return null;
		}

		int canvasWidth = Microbot.getClient().getCanvasWidth();
		int canvasHeight = Microbot.getClient().getCanvasHeight();
		if (canvasWidth <= 0 || canvasHeight <= 0)
		{
			return null;
		}

		int width = Math.min(CHATBOX_WIDTH, canvasWidth);
		int height = Math.min(CHATBOX_HEIGHT, canvasHeight);
		int x = 0;
		int y = Math.max(0, canvasHeight - CHATBOX_ANCHOR_HEIGHT - TITLE_OVERHANG);

		drawPaint(graphics, x, y, width, height);
		return new Dimension(width, height);
	}

	private void drawPaint(Graphics2D graphics, int x, int y, int width, int height)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			drawFrame(g, x, y, width, height);
			drawTitle(g, x, y, width);
			drawRows(g, x, y, width, height);
		}
		finally
		{
			g.dispose();
		}
	}

	private void drawFrame(Graphics2D g, int x, int y, int width, int height)
	{
		g.setColor(OUTER_SHADOW);
		g.fillRoundRect(x + 4, y + 5, width - 8, height - 7, 12, 12);

		g.setPaint(new GradientPaint(x, y, STONE_TOP, x, y + height, STONE_BOTTOM));
		g.fillRoundRect(x + 3, y + 2, width - 7, height - 5, 12, 12);

		g.setColor(STONE_DARK);
		g.setStroke(new BasicStroke(3f));
		g.drawRoundRect(x + 3, y + 2, width - 8, height - 6, 12, 12);

		g.setColor(STONE_LIGHT);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x + 7, y + 6, width - 16, height - 14, 9, 9);

		g.setColor(BRONZE);
		g.setStroke(new BasicStroke(2f));
		g.drawRoundRect(x + 10, y + 9, width - 22, height - 20, 7, 7);

		g.setColor(BRONZE_LIGHT);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x + 13, y + 12, width - 28, height - 26, 5, 5);

		g.setPaint(new GradientPaint(x, y + TITLE_HEIGHT, INNER_TOP, x, y + height - FRAME, INNER_BOTTOM));
		g.fillRoundRect(x + 19, y + TITLE_HEIGHT + 4, width - 38, height - TITLE_HEIGHT - 21, 5, 5);
		g.setColor(new Color(10, 8, 6, 180));
		g.drawRoundRect(x + 19, y + TITLE_HEIGHT + 4, width - 39, height - TITLE_HEIGHT - 22, 5, 5);

		drawCorner(g, x + 13, y + 13, true, true);
		drawCorner(g, x + width - 31, y + 13, false, true);
		drawCorner(g, x + 13, y + height - 31, true, false);
		drawCorner(g, x + width - 31, y + height - 31, false, false);
		drawTopGem(g, x + width / 2, y + 10);
		drawBottomCompass(g, x + width / 2, y + height - 15);
		drawMinimizeButton(g, x + width - 50, y + 18);
	}

	private void drawTitle(Graphics2D g, int x, int y, int width)
	{
		g.setPaint(new GradientPaint(x, y + 12, new Color(32, 28, 22, 245), x, y + TITLE_HEIGHT + 5, new Color(61, 48, 30, 236)));
		g.fillRoundRect(x + 74, y + 3, width - 148, TITLE_HEIGHT + 2, 8, 8);
		g.setColor(BRONZE_LIGHT);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x + 75, y + 4, width - 150, TITLE_HEIGHT, 8, 8);

		Font titleFont = new Font(Font.SERIF, Font.BOLD, 21);
		FontMetrics fm = g.getFontMetrics(titleFont);
		String title = "KSP AIO Fighter";
		int titleX = x + (width - fm.stringWidth(title)) / 2;
		int titleY = y + 29;
		drawSwordIcon(g, titleX - 34, y + 12, 18);
		drawSwordIcon(g, titleX + fm.stringWidth(title) + 17, y + 12, 18);
		drawText(g, title, titleFont, titleX, titleY, GOLD);
	}

	private void drawRows(Graphics2D g, int x, int y, int width, int height)
	{
		Font labelFont = new Font(Font.SERIF, Font.BOLD, 13);
		Font valueFont = new Font(Font.SERIF, Font.BOLD, 13);
		FontMetrics valueMetrics = g.getFontMetrics(valueFont);

		int rowTop = y + TITLE_HEIGHT + 24;
		int leftX = x + 30;
		int rightX = x + width / 2 + 9;
		int colWidth = (width - 69) / 2;

		String status = currentStatus();
		String error = currentError();
		Color statusColor = GOOD;
		if (!"-".equals(error))
		{
			status = error;
			statusColor = WARNING;
		}

		drawInfoRow(g, valueMetrics, leftX, rowTop, colWidth, 0, "Status", status, statusColor, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, leftX, rowTop + ROW_HEIGHT, colWidth, 1, "Time Running", formatDuration(script.getOverlayRunningTimeMs()), VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, leftX, rowTop + ROW_HEIGHT * 2, colWidth, 2, "Action", script.getOverlayAction(), VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, leftX, rowTop + ROW_HEIGHT * 3, colWidth, 3, "Skill", script.getOverlayTrainingSkill(), VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, leftX, rowTop + ROW_HEIGHT * 4, colWidth, 4, "Target", formatTarget(), VALUE, labelFont, valueFont);

		drawInfoRow(g, valueMetrics, rightX, rowTop, colWidth, 5, "Loot", formatLoot(), VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, rightX, rowTop + ROW_HEIGHT, colWidth, 6, "Food", formatFood(), VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, rightX, rowTop + ROW_HEIGHT * 2, colWidth, 7, "Inventory", formatInventory(), Rs2Inventory.isFull() ? WARNING : VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, rightX, rowTop + ROW_HEIGHT * 3, colWidth, 8, "Area", formatAttackArea(), VALUE, labelFont, valueFont);
		drawInfoRow(g, valueMetrics, rightX, rowTop + ROW_HEIGHT * 4, colWidth, 9, "Goal", config.walkToBankAndLogoutWhenGoalsReached() ? "Bank + logout" : "Stop plugin", VALUE, labelFont, valueFont);
	}

	private void drawInfoRow(Graphics2D g, FontMetrics valueMetrics, int x, int y, int width, int iconType,
						 String label, String value, Color valueColor, Font labelFont, Font valueFont)
	{
		g.setPaint(new GradientPaint(x, y - 13, ROW_TOP, x, y + 4, ROW_BOTTOM));
		g.fillRoundRect(x, y - 15, width, ROW_HEIGHT - 2, 6, 6);
		g.setColor(new Color(5, 4, 3, 190));
		g.drawRoundRect(x, y - 15, width, ROW_HEIGHT - 3, 6, 6);
		g.setColor(new Color(115, 86, 42, 205));
		g.drawLine(x + ICON_SIZE + 10, y + 2, x + width - 7, y + 2);
		drawSmallDiamond(g, x - 4, y - 7);
		drawSmallDiamond(g, x + width - 4, y - 7);

		drawIcon(g, x + 5, y - 12, iconType);

		String safeValue = value == null || value.trim().isEmpty() ? "-" : value.trim();
		int labelX = x + ICON_SIZE + 13;
		int valueX = labelX + Math.max(68, g.getFontMetrics(labelFont).stringWidth(label + ":") + 9);
		String trimmedValue = trim(safeValue, valueMetrics, Math.max(25, x + width - valueX - 6));

		drawText(g, label + ":", labelFont, labelX, y - 1, LABEL);
		drawText(g, trimmedValue, valueFont, valueX, y - 1, valueColor);
	}

	private void drawCorner(Graphics2D g, int x, int y, boolean left, boolean top)
	{
		g.setPaint(new GradientPaint(x, y, BRONZE_LIGHT, x + 16, y + 16, BRONZE));
		Path2D path = new Path2D.Double();
		int sx = left ? 1 : -1;
		int sy = top ? 1 : -1;
		path.moveTo(x + (left ? 0 : 18), y + (top ? 5 : 13));
		path.lineTo(x + (left ? 5 : 13), y + (top ? 0 : 18));
		path.lineTo(x + (left ? 18 : 0), y + (top ? 7 : 11));
		path.lineTo(x + (left ? 11 : 7), y + (top ? 18 : 0));
		path.closePath();
		g.fill(path);
		g.setColor(STONE_DARK);
		g.draw(path);
		g.setColor(GEM_GREEN);
		g.fillOval(x + 7 + (sx < 0 ? -1 : 0), y + 7 + (sy < 0 ? -1 : 0), 5, 5);
	}

	private void drawTopGem(Graphics2D g, int cx, int y)
	{
		drawSmallDiamond(g, cx - 12, y + 1);
		drawSmallDiamond(g, cx + 4, y + 1);
		Path2D gem = new Path2D.Double();
		gem.moveTo(cx, y);
		gem.lineTo(cx + 7, y + 6);
		gem.lineTo(cx, y + 12);
		gem.lineTo(cx - 7, y + 6);
		gem.closePath();
		g.setColor(new Color(20, 110, 70, 245));
		g.fill(gem);
		g.setColor(BRONZE_LIGHT);
		g.draw(gem);
	}

	private void drawBottomCompass(Graphics2D g, int cx, int cy)
	{
		g.setColor(new Color(15, 12, 8, 220));
		g.fillOval(cx - 14, cy - 14, 28, 28);
		g.setColor(BRONZE_LIGHT);
		g.setStroke(new BasicStroke(2f));
		g.drawOval(cx - 13, cy - 13, 26, 26);
		g.setStroke(new BasicStroke(1f));
		g.drawLine(cx, cy - 18, cx, cy + 18);
		g.drawLine(cx - 18, cy, cx + 18, cy);
		g.setColor(GOLD);
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(cx, cy - 12);
		arrow.lineTo(cx + 4, cy + 3);
		arrow.lineTo(cx, cy);
		arrow.lineTo(cx - 4, cy + 3);
		arrow.closePath();
		g.fill(arrow);
	}

	private void drawMinimizeButton(Graphics2D g, int x, int y)
	{
		g.setPaint(new GradientPaint(x, y, new Color(92, 78, 61, 245), x, y + 19, new Color(24, 21, 17, 245)));
		g.fillRoundRect(x, y, 22, 19, 5, 5);
		g.setColor(BRONZE_LIGHT);
		g.drawRoundRect(x, y, 21, 18, 5, 5);
		g.setColor(GOLD);
		g.setStroke(new BasicStroke(2f));
		g.drawLine(x + 6, y + 10, x + 16, y + 10);
	}

	private void drawSmallDiamond(Graphics2D g, int x, int y)
	{
		Path2D diamond = new Path2D.Double();
		diamond.moveTo(x + 4, y);
		diamond.lineTo(x + 8, y + 4);
		diamond.lineTo(x + 4, y + 8);
		diamond.lineTo(x, y + 4);
		diamond.closePath();
		g.setColor(new Color(18, 14, 9, 230));
		g.fill(diamond);
		g.setColor(BRONZE_LIGHT);
		g.draw(diamond);
	}

	private void drawIcon(Graphics2D g, int x, int y, int type)
	{
		g.setColor(new Color(10, 8, 6, 210));
		g.fillOval(x - 1, y - 1, ICON_SIZE + 2, ICON_SIZE + 2);
		g.setColor(new Color(98, 83, 61, 230));
		g.fillOval(x, y, ICON_SIZE, ICON_SIZE);
		g.setColor(BRONZE_LIGHT);
		g.drawOval(x, y, ICON_SIZE, ICON_SIZE);

		switch (type)
		{
			case 0:
			case 2:
				drawSwordIcon(g, x + 3, y + 3, 11);
				break;
			case 1:
				drawClockIcon(g, x + 3, y + 3, 11);
				break;
			case 3:
				drawStrengthIcon(g, x + 3, y + 4);
				break;
			case 4:
				drawTargetIcon(g, x + 3, y + 3, 11);
				break;
			case 5:
				drawBagIcon(g, x + 4, y + 3);
				break;
			case 6:
				drawFoodIcon(g, x + 3, y + 5);
				break;
			case 7:
				drawInventoryIcon(g, x + 4, y + 4);
				break;
			case 8:
				drawFlagIcon(g, x + 5, y + 3);
				break;
			default:
				drawFlagIcon(g, x + 5, y + 3);
				break;
		}
	}

	private void drawSwordIcon(Graphics2D g, int x, int y, int size)
	{
		g.setColor(new Color(218, 210, 190, 255));
		g.setStroke(new BasicStroke(2f));
		g.drawLine(x, y + size, x + size, y);
		g.setColor(BRONZE_LIGHT);
		g.drawLine(x + 3, y + size - 2, x + size - 2, y + 3);
		g.setColor(new Color(85, 46, 22, 255));
		g.drawLine(x + 1, y + size - 5, x + 5, y + size - 1);
	}

	private void drawClockIcon(Graphics2D g, int x, int y, int size)
	{
		g.setColor(new Color(230, 224, 199, 255));
		g.drawOval(x, y, size, size);
		g.drawLine(x + size / 2, y + size / 2, x + size / 2, y + 2);
		g.drawLine(x + size / 2, y + size / 2, x + size - 2, y + size / 2);
	}

	private void drawStrengthIcon(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(235, 190, 138, 255));
		g.fillOval(x + 1, y + 5, 7, 7);
		g.fillRoundRect(x + 5, y + 2, 8, 5, 5, 5);
		g.setColor(new Color(120, 65, 32, 255));
		g.drawArc(x + 2, y, 10, 12, 200, 140);
	}

	private void drawTargetIcon(Graphics2D g, int x, int y, int size)
	{
		g.setColor(new Color(225, 222, 210, 255));
		g.drawOval(x, y, size, size);
		g.drawOval(x + 3, y + 3, size - 6, size - 6);
		g.drawLine(x + size / 2, y - 1, x + size / 2, y + size + 1);
		g.drawLine(x - 1, y + size / 2, x + size + 1, y + size / 2);
	}

	private void drawBagIcon(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(205, 136, 48, 255));
		g.fillRoundRect(x + 1, y + 5, 10, 8, 5, 5);
		g.setColor(new Color(115, 70, 25, 255));
		g.drawArc(x + 2, y, 8, 8, 0, 180);
		g.setColor(GOLD);
		g.drawLine(x + 4, y + 7, x + 9, y + 7);
	}

	private void drawFoodIcon(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(224, 128, 72, 255));
		g.fillOval(x + 1, y + 2, 12, 7);
		g.setColor(new Color(255, 205, 135, 255));
		g.drawArc(x, y, 12, 10, 20, 130);
	}

	private void drawInventoryIcon(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(207, 167, 80, 255));
		g.drawRect(x, y, 10, 10);
		g.drawLine(x + 5, y, x + 5, y + 10);
		g.drawLine(x, y + 5, x + 10, y + 5);
	}

	private void drawFlagIcon(Graphics2D g, int x, int y)
	{
		g.setColor(new Color(209, 158, 54, 255));
		g.drawLine(x, y, x, y + 12);
		g.fillRect(x + 1, y + 1, 8, 5);
		g.setColor(new Color(54, 116, 172, 255));
		g.fillRect(x + 3, y + 2, 5, 3);
	}

	private void drawText(Graphics2D g, String text, Font font, int x, int y, Color color)
	{
		g.setFont(font);
		g.setColor(TEXT_SHADOW);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private String currentStatus()
	{
		String status = Microbot.status;
		if (status == null || status.isEmpty())
		{
			return "-";
		}
		String prefix = "KSP AIO Fighter: ";
		return status.startsWith(prefix) ? status.substring(prefix.length()) : status;
	}

	private String currentError()
	{
		String error = script.getOverlayLastError();
		long ageMs = script.getOverlayLastErrorAgeMs();
		if (error == null || error.equals("-") || ageMs < 0L || ageMs > 30_000L)
		{
			return "-";
		}
		return error + " / " + formatAge(ageMs);
	}

	private String formatTarget()
	{
		String name = script.getOverlayTargetName();
		WorldPoint location = script.getOverlayTargetLocation();
		if (name == null || name.equals("-"))
		{
			return "-";
		}
		return name + " @ " + formatPoint(location);
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
			return "none";
		}
		return foodName + " " + Rs2Inventory.count(foodName, true) + "/" + config.foodAmount();
	}

	private String formatLoot()
	{
		if (!config.lootItems() && !config.buryBones())
		{
			return "off";
		}
		String custom = config.itemsToLoot() == null ? "" : config.itemsToLoot().trim();
		if (!custom.isEmpty())
		{
			return custom;
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

	private String formatInventory()
	{
		return getInventoryUsedSlots() + "/28" + (Rs2Inventory.isFull() ? " full" : "");
	}

	private int getInventoryUsedSlots()
	{
		ItemContainer inventory = Microbot.getClient().getItemContainer(InventoryID.INVENTORY);
		if (inventory == null || inventory.getItems() == null)
		{
			return 0;
		}
		int used = 0;
		for (Item item : inventory.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				used++;
			}
		}
		return used;
	}

	private String formatPoint(WorldPoint point)
	{
		return point == null ? "-" : point.getX() + "," + point.getY() + "," + point.getPlane();
	}

	private String trim(String value, FontMetrics metrics, int maxWidth)
	{
		if (value == null || metrics.stringWidth(value) <= maxWidth)
		{
			return value;
		}
		String ellipsis = "...";
		int end = value.length();
		while (end > 0 && metrics.stringWidth(value.substring(0, end) + ellipsis) > maxWidth)
		{
			end--;
		}
		return value.substring(0, Math.max(0, end)) + ellipsis;
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
		return Math.max(0L, ageMs / 1_000L) + "s";
	}
}
