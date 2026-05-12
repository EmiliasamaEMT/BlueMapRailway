package dev.kokomi.bluemaprailway.render;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import dev.kokomi.bluemaprailway.model.RailLine;
import dev.kokomi.bluemaprailway.model.RailScanResult;
import dev.kokomi.bluemaprailway.model.RailType;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlueMapRailRenderer {

    private final Plugin plugin;

    public BlueMapRailRenderer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void render(BlueMapAPI api, RailScanResult result) {
        Map<String, List<RailLine>> linesByWorld = new HashMap<>();
        for (RailLine line : result.lines()) {
            linesByWorld.computeIfAbsent(line.worldName(), ignored -> new ArrayList<>()).add(line);
        }

        if (plugin.getConfig().getBoolean("debug.render-demo-line", false)) {
            addDemoLines(linesByWorld);
        }

        for (Map.Entry<String, List<RailLine>> entry : linesByWorld.entrySet()) {
            renderWorld(api, entry.getKey(), entry.getValue());
        }
    }

    private void renderWorld(BlueMapAPI api, String worldName, List<RailLine> lines) {
        String markerSetId = plugin.getConfig().getString("markers.set-id", "railways");
        String markerSetLabel = plugin.getConfig().getString("markers.label", "Railways");
        boolean defaultHidden = plugin.getConfig().getBoolean("markers.default-hidden", false);

        MarkerSet markerSet = MarkerSet.builder()
                .label(markerSetLabel)
                .defaultHidden(defaultHidden)
                .build();

        int index = 0;
        for (RailLine railLine : lines) {
            markerSet.put("rail-line-" + index, toMarker(railLine, index));
            index++;
        }

        api.getWorld(worldName).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                map.getMarkerSets().put(markerSetId, markerSet);
            }
        });
    }

    private LineMarker toMarker(RailLine railLine, int index) {
        Line.Builder lineBuilder = Line.builder();
        for (Vector3d point : railLine.points()) {
            lineBuilder.addPoint(point);
        }

        return LineMarker.builder()
                .label("铁路段 " + (index + 1))
                .line(lineBuilder.build())
                .lineWidth(plugin.getConfig().getInt("markers.line-width", 5))
                .lineColor(colorFor(railLine.type(), railLine.powered()))
                .depthTestEnabled(plugin.getConfig().getBoolean("markers.depth-test-enabled", false))
                .listed(false)
                .build();
    }

    private Color colorFor(RailType type, boolean powered) {
        String key = "markers.colors." + type.configKey();
        if (type == RailType.POWERED_RAIL && !powered) {
            key = "markers.colors.powered-rail-inactive";
        }

        String fallback = type == RailType.POWERED_RAIL && !powered ? "#a16207" : "#9ca3af";
        return new Color(plugin.getConfig().getString(key, fallback));
    }

    private void addDemoLines(Map<String, List<RailLine>> linesByWorld) {
        double yOffset = plugin.getConfig().getDouble("markers.y-offset", 0.35);

        for (World world : Bukkit.getWorlds()) {
            if (!plugin.getConfig().getBoolean("worlds." + world.getName() + ".enabled", false)) {
                continue;
            }

            var spawn = world.getSpawnLocation();
            List<Vector3d> points = List.of(
                    new Vector3d(spawn.getBlockX() + 0.5, spawn.getBlockY() + yOffset, spawn.getBlockZ() + 0.5),
                    new Vector3d(spawn.getBlockX() + 16.5, spawn.getBlockY() + yOffset, spawn.getBlockZ() + 0.5),
                    new Vector3d(spawn.getBlockX() + 24.5, spawn.getBlockY() + yOffset, spawn.getBlockZ() + 8.5)
            );

            linesByWorld.computeIfAbsent(world.getName(), ignored -> new ArrayList<>())
                    .add(new RailLine(world.getName(), RailType.RAIL, false, points));
        }
    }
}
