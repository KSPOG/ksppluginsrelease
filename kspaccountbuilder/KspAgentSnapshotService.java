package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import net.runelite.api.coords.WorldPoint;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Singleton
public class KspAgentSnapshotService
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private WorldPoint lastPlayerLocation;

    private final Client client;

    private final Path snapshotPath = Paths.get(
            System.getProperty("user.home"),
            ".runelite",
            "ksp_agent_snapshot.json"
    );

    @Inject
    public KspAgentSnapshotService(Client client)
    {
        this.client = client;
    }

    public void writeSnapshot()
    {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("timestamp", System.currentTimeMillis());
        root.put("loggedIn", client.getGameState() == GameState.LOGGED_IN);
        root.put("gameState", client.getGameState().name());
        root.put("player", readPlayer());
        root.put("widgets", readWidgets());

        writeJson(root);
    }

    private Map<String, Object> readPlayer()
    {
        Map<String, Object> map = new LinkedHashMap<>();
        Player player = client.getLocalPlayer();

        if (player == null)
        {
            map.put("available", false);
            return map;
        }

        WorldPoint currentLocation = player.getWorldLocation();

        boolean moving = lastPlayerLocation != null
                && currentLocation != null
                && !currentLocation.equals(lastPlayerLocation);

        map.put("available", true);
        map.put("name", player.getName());
        map.put("combatLevel", player.getCombatLevel());
        map.put("animation", player.getAnimation());
        map.put("moving", moving);
        map.put("interacting", player.getInteracting() != null);

        if (currentLocation != null)
        {
            Map<String, Object> pos = new LinkedHashMap<>();
            pos.put("x", currentLocation.getX());
            pos.put("y", currentLocation.getY());
            pos.put("plane", currentLocation.getPlane());
            map.put("position", pos);

            lastPlayerLocation = currentLocation;
        }

        return map;
    }

    private List<Map<String, Object>> readWidgets()
    {
        List<Map<String, Object>> result = new ArrayList<>();

        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return result;
        }

        for (Widget root : roots)
        {
            collectWidget(root, result, 0);
        }

        return result;
    }

    private void collectWidget(Widget widget, List<Map<String, Object>> output, int depth)
    {
        if (widget == null)
        {
            return;
        }

        Map<String, Object> map = new LinkedHashMap<>();

        map.put("id", widget.getId());
        map.put("groupId", widget.getId() >>> 16);
        map.put("childId", widget.getId() & 0xFFFF);
        map.put("type", widget.getType());
        map.put("hidden", widget.isHidden());
        map.put("selfHidden", widget.isSelfHidden());
        map.put("text", safe(widget.getText()));
        map.put("name", safe(widget.getName()));
        map.put("itemId", widget.getItemId());
        map.put("itemQuantity", widget.getItemQuantity());
        map.put("spriteId", widget.getSpriteId());
        map.put("modelId", widget.getModelId());
        map.put("depth", depth);

        Rectangle bounds = widget.getBounds();
        if (bounds != null)
        {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("x", bounds.x);
            b.put("y", bounds.y);
            b.put("width", bounds.width);
            b.put("height", bounds.height);
            map.put("bounds", b);
        }

        output.add(map);

        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                collectWidget(child, output, depth + 1);
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                collectWidget(child, output, depth + 1);
            }
        }

        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget child : staticChildren)
            {
                collectWidget(child, output, depth + 1);
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                collectWidget(child, output, depth + 1);
            }
        }
    }

    private String safe(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.replace("<br>", " ").replaceAll("<[^>]*>", "").trim();
    }

    private void writeJson(Map<String, Object> root)
    {
        try
        {
            Files.createDirectories(snapshotPath.getParent());

            Path temp = snapshotPath.resolveSibling(snapshotPath.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            Files.move(temp, snapshotPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException ignored)
        {
            // Keep plugin safe; do not crash the client because snapshot writing failed.
        }
    }
}