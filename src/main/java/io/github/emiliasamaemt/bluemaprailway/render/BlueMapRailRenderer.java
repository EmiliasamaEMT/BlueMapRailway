package io.github.emiliasamaemt.bluemaprailway.render;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import io.github.emiliasamaemt.bluemaprailway.station.RailStation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlueMapRailRenderer {

    private final Plugin plugin;

    public BlueMapRailRenderer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void render(BlueMapAPI api, RailScanResult result, List<RailStation> stations) {
        Map<String, List<RailLine>> linesByWorld = new HashMap<>();
        for (RailLine line : result.lines()) {
            linesByWorld.computeIfAbsent(line.worldName(), ignored -> new ArrayList<>()).add(line);
        }

        if (plugin.getConfig().getBoolean("debug.render-demo-line", false)) {
            addDemoLines(linesByWorld);
        }

        for (String worldName : renderWorldNames(linesByWorld, stations)) {
            renderWorld(api, worldName, linesByWorld.getOrDefault(worldName, List.of()), stations);
        }
    }

    private void renderWorld(BlueMapAPI api, String worldName, List<RailLine> lines, List<RailStation> stations) {
        String markerSetId = plugin.getConfig().getString("markers.set-id", "railways");
        String markerSetLabel = plugin.getConfig().getString("markers.label", "Railways");
        boolean defaultHidden = plugin.getConfig().getBoolean("markers.default-hidden", false);
        Map<String, MarkerSet> markerSets = buildRouteMarkerSets(markerSetLabel, defaultHidden, lines);
        MarkerSet stationMarkerSet = buildStationMarkerSet(markerSetLabel, defaultHidden, worldName, stations);

        api.getWorld(worldName).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                clearRailwayMarkerSets(map, markerSetId);

                for (Map.Entry<String, MarkerSet> entry : markerSets.entrySet()) {
                    map.getMarkerSets().put(entry.getKey(), entry.getValue());
                }

                if (stationMarkerSet != null) {
                    map.getMarkerSets().put(stationMarkerSetId(markerSetId), stationMarkerSet);
                }
            }
        });
    }

    private Map<String, MarkerSet> buildRouteMarkerSets(String markerSetLabel, boolean defaultHidden, List<RailLine> lines) {
        String markerSetId = plugin.getConfig().getString("markers.set-id", "railways");
        Map<String, MarkerSet> markerSets = new LinkedHashMap<>();
        Map<String, Integer> indexes = new HashMap<>();

        for (RailLine railLine : lines) {
            String id = routeMarkerSetId(markerSetId, railLine);
            MarkerSet markerSet = markerSets.computeIfAbsent(id, ignored -> MarkerSet.builder()
                    .label(routeMarkerSetLabel(markerSetLabel, railLine))
                    .defaultHidden(defaultHidden)
                    .build());

            int index = indexes.getOrDefault(id, 0);
            markerSet.put("rail-line-" + index, toMarker(railLine, index));
            indexes.put(id, index + 1);
        }

        return markerSets;
    }

    private MarkerSet buildStationMarkerSet(
            String markerSetLabel,
            boolean defaultHidden,
            String worldName,
            List<RailStation> stations
    ) {
        List<RailStation> worldStations = stations.stream()
                .filter(station -> station.worldName().equals(worldName))
                .toList();

        if (worldStations.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = MarkerSet.builder()
                .label(markerSetLabel + " - 站点")
                .defaultHidden(defaultHidden)
                .build();

        for (RailStation station : worldStations) {
            markerSet.put("station-" + escapeId(station.id()), POIMarker.builder()
                    .label(station.name())
                    .position(station.center())
                    .detail(stationDetail(station))
                    .defaultIcon()
                    .listed(true)
                    .build());
        }

        return markerSet;
    }

    private Set<String> renderWorldNames(Map<String, List<RailLine>> linesByWorld, List<RailStation> stations) {
        Set<String> worldNames = new HashSet<>(linesByWorld.keySet());
        for (RailStation station : stations) {
            worldNames.add(station.worldName());
        }

        var worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                if (worldsSection.getBoolean(worldName + ".enabled", false)) {
                    worldNames.add(worldName);
                }
            }
        }

        return worldNames;
    }

    private void clearRailwayMarkerSets(BlueMapMap map, String markerSetId) {
        map.getMarkerSets().keySet().removeIf(id ->
                id.equals(markerSetId) ||
                        id.equals(stationMarkerSetId(markerSetId)) ||
                        id.equals(unclassifiedMarkerSetId(markerSetId)) ||
                        id.startsWith(markerSetId + ".route.")
        );
    }

    private LineMarker toMarker(RailLine railLine, int index) {
        Line.Builder lineBuilder = Line.builder();
        for (Vector3d point : railLine.points()) {
            lineBuilder.addPoint(point);
        }

        return LineMarker.builder()
                .label(labelFor(railLine, index))
                .line(lineBuilder.build())
                .lineWidth(lineWidthFor(railLine))
                .lineColor(colorFor(railLine))
                .depthTestEnabled(plugin.getConfig().getBoolean("markers.depth-test-enabled", false))
                .listed(false)
                .build();
    }

    private String labelFor(RailLine railLine, int index) {
        String routeName = railLine.routeName();
        if (routeName != null && !routeName.isBlank()) {
            return routeName + " / " + shortComponentId(railLine.componentId()) + " / 铁路段 " + (index + 1);
        }

        return shortComponentId(railLine.componentId()) + " / 铁路段 " + (index + 1);
    }

    private int lineWidthFor(RailLine railLine) {
        if (railLine.routeLineWidth() > 0) {
            return railLine.routeLineWidth();
        }

        return plugin.getConfig().getInt("markers.line-width", 5);
    }

    private Color colorFor(RailType type, boolean powered) {
        String key = "markers.colors." + type.configKey();
        if (type == RailType.POWERED_RAIL && !powered) {
            key = "markers.colors.powered-rail-inactive";
        }

        String fallback = type == RailType.POWERED_RAIL && !powered ? "#a16207" : "#9ca3af";
        return new Color(plugin.getConfig().getString(key, fallback));
    }

    private Color colorFor(RailLine railLine) {
        String routeColor = railLine.routeColor();
        if (routeColor != null && !routeColor.isBlank()) {
            return new Color(routeColor);
        }

        return colorFor(railLine.type(), railLine.powered());
    }

    private String shortComponentId(String componentId) {
        int index = componentId.lastIndexOf(':');
        if (index < 0 || index == componentId.length() - 1) {
            return componentId;
        }

        return componentId.substring(index + 1);
    }

    private String routeMarkerSetId(String markerSetId, RailLine line) {
        if (line.routeId() == null || line.routeId().isBlank()) {
            return unclassifiedMarkerSetId(markerSetId);
        }

        return markerSetId + ".route." + escapeId(line.routeId());
    }

    private String routeMarkerSetLabel(String markerSetLabel, RailLine line) {
        if (line.routeName() == null || line.routeName().isBlank()) {
            return markerSetLabel + " - 未分类";
        }

        return markerSetLabel + " - " + line.routeName();
    }

    private String unclassifiedMarkerSetId(String markerSetId) {
        return markerSetId + ".unclassified";
    }

    private String stationMarkerSetId(String markerSetId) {
        return markerSetId + ".stations";
    }

    private String stationDetail(RailStation station) {
        return "ID: " + escapeHtml(station.id()) + "<br>World: " + escapeHtml(station.worldName()) + "<br>Area: [" +
                station.minX() + "," + station.minY() + "," + station.minZ() + "] -> [" +
                station.maxX() + "," + station.maxY() + "," + station.maxZ() + "]";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
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
                    .add(new RailLine(world.getName() + ":component:demo", world.getName(), RailType.RAIL, false, points));
        }
    }
}
