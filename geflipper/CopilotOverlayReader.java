package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility boundary for Flipping Copilot.
 *
 * <p>All RuneLite interaction is performed through the public OverlayManager API. Reflection
 * is isolated here because Copilot's overlay payload fields are private and Copilot does not
 * expose a public service interface. No private Microbot or RuneLite fields are accessed.</p>
 */
final class CopilotOverlayReader
{
    private static final Logger LOG = LoggerFactory.getLogger(CopilotOverlayReader.class);

    private static final String WIDGET_OVERLAY = "com.flippingcopilot.ui.WidgetHighlightOverlay";
    private static final String NPC_OVERLAY = "com.flippingcopilot.ui.NpcHighlightOverlay";

    private final OverlayManager overlayManager;
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

    @Inject
    CopilotOverlayReader(OverlayManager overlayManager)
    {
        this.overlayManager = overlayManager;
    }

    List<CopilotHighlight> snapshot()
    {
        List<Overlay> overlays;
        synchronized (overlayManager)
        {
            overlays = new ArrayList<>(overlayManager.getOverlays());
        }

        if (overlays.isEmpty())
        {
            return Collections.emptyList();
        }

        List<CopilotHighlight> highlights = new ArrayList<>();
        for (Overlay overlay : overlays)
        {
            if (overlay == null)
            {
                continue;
            }

            String className = overlay.getClass().getName();
            if (WIDGET_OVERLAY.equals(className))
            {
                Widget widget = readField(overlay, "widget", Widget.class);
                Rectangle relativeBounds = readField(overlay, "relativeBounds", Rectangle.class);
                Color color = readOverlayColor(overlay);
                if (widget != null)
                {
                    highlights.add(CopilotHighlight.widget(widget, relativeBounds, color, className));
                }
            }
            else if (NPC_OVERLAY.equals(className))
            {
                NPC npc = readField(overlay, "npc", NPC.class);
                if (npc != null)
                {
                    highlights.add(CopilotHighlight.npc(npc, className));
                }
            }
        }
        return highlights;
    }

    private Color readOverlayColor(Object overlay)
    {
        Object value = readField(overlay, "colorSupplier", Object.class);
        if (!(value instanceof Supplier<?>))
        {
            return null;
        }

        try
        {
            Object supplied = ((Supplier<?>) value).get();
            return supplied instanceof Color ? (Color) supplied : null;
        }
        catch (RuntimeException ex)
        {
            LOG.debug("Unable to evaluate Copilot highlight color: {}", ex.getMessage());
            return null;
        }
    }

    private <T> T readField(Object owner, String name, Class<T> expectedType)
    {
        String key = owner.getClass().getName() + '#' + name;
        try
        {
            Field field = fieldCache.computeIfAbsent(key, ignored -> locateField(owner.getClass(), name));
            if (field == null)
            {
                return null;
            }

            Object value = field.get(owner);
            return expectedType.isInstance(value) ? expectedType.cast(value) : null;
        }
        catch (ReflectiveOperationException | RuntimeException ex)
        {
            LOG.debug("Unable to read Copilot overlay field {}: {}", key, ex.getMessage());
            fieldCache.remove(key);
            return null;
        }
    }

    private Field locateField(Class<?> type, String name)
    {
        Class<?> current = type;
        while (current != null)
        {
            try
            {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                current = current.getSuperclass();
            }
            catch (RuntimeException ex)
            {
                LOG.debug("Unable to make Copilot field accessible: {}", ex.getMessage());
                return null;
            }
        }
        return null;
    }
}
