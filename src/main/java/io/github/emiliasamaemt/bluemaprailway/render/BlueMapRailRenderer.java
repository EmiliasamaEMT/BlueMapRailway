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
import java.util.TreeSet;

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
        List<RailStation> worldStations = worldStations(worldName, stations);
        SplitLines splitLines = splitStationLines(lines, worldStations);
        Map<String, MarkerSet> markerSets = buildRouteMarkerSets(markerSetLabel, defaultHidden, splitLines.routeLines());
        MarkerSet stationInternalMarkerSet = buildStationInternalMarkerSet(defaultHidden, splitLines.stationLines());
        MarkerSet stationMarkerSet = buildStationMarkerSet(defaultHidden, worldStations, lines);

        api.getWorld(worldName).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                clearRailwayMarkerSets(map, markerSetId);

                for (Map.Entry<String, MarkerSet> entry : markerSets.entrySet()) {
                    map.getMarkerSets().put(entry.getKey(), entry.getValue());
                }

                if (stationInternalMarkerSet != null) {
                    map.getMarkerSets().put(stationInternalMarkerSetId(markerSetId), stationInternalMarkerSet);
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

    private MarkerSet buildStationInternalMarkerSet(boolean defaultHidden, List<RailLine> lines) {
        if (lines.isEmpty()) {
            return null;
        }

        boolean hidden = plugin.getConfig().getBoolean("stations.internal-tracks.default-hidden", defaultHidden);
        MarkerSet markerSet = MarkerSet.builder()
                .label(plugin.getConfig().getString("stations.internal-tracks.label", "站内轨道"))
                .defaultHidden(hidden)
                .build();

        int index = 0;
        for (RailLine line : lines) {
            markerSet.put("station-rail-line-" + index, toMarker(line, index));
            index++;
        }

        return markerSet;
    }

    private MarkerSet buildStationMarkerSet(
            boolean defaultHidden,
            List<RailStation> stations,
            List<RailLine> lines
    ) {
        if (stations.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = MarkerSet.builder()
                .label(plugin.getConfig().getString("stations.marker-set-label", "站点"))
                .defaultHidden(defaultHidden)
                .build();

        for (RailStation station : stations) {
            markerSet.put("station-" + escapeId(station.id()), POIMarker.builder()
                    .label(station.name())
                    .position(station.center())
                    .detail(stationDetail(station, lines))
                    .defaultIcon()
                    .listed(true)
                    .build());
            addStationBounds(markerSet, station);
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
                        id.equals(stationInternalMarkerSetId(markerSetId)) ||
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
            return plugin.getConfig().getString("markers.unclassified-label", markerSetLabel + " - 未分类");
        }

        return line.routeName();
    }

    private String unclassifiedMarkerSetId(String markerSetId) {
        return markerSetId + ".unclassified";
    }

    private String stationMarkerSetId(String markerSetId) {
        return markerSetId + ".stations";
    }

    private String stationInternalMarkerSetId(String markerSetId) {
        return markerSetId + ".station-internal";
    }

    private String stationDetail(RailStation station, List<RailLine> lines) {
        StringBuilder detail = new StringBuilder();
        detail.append("ID: ").append(escapeHtml(station.id()))
                .append("<br>World: ").append(escapeHtml(station.worldName()))
                .append("<br>Area: [")
                .append(station.minX()).append(",").append(station.minY()).append(",").append(station.minZ()).append("] -> [")
                .append(station.maxX()).append(",").append(station.maxY()).append(",").append(station.maxZ()).append("]");

        Set<String> routeNames = stationRouteNames(station, lines);
        if (!routeNames.isEmpty()) {
            detail.append("<br>经过线路: ").append(escapeHtml(String.join(", ", routeNames)));
        }

        return detail.toString();
    }

    private Set<String> stationRouteNames(RailStation station, List<RailLine> lines) {
        Set<String> routeNames = new TreeSet<>();
        for (RailLine line : lines) {
            boolean passesStation = passesStation(line, station);
            if (!passesStation) {
                continue;
            }

            if (line.routeName() != null && !line.routeName().isBlank()) {
                routeNames.add(line.routeName());
            } else {
                routeNames.add("未分类/" + shortComponentId(line.componentId()));
            }
        }

        return routeNames;
    }

    private List<RailStation> worldStations(String worldName, List<RailStation> stations) {
        return stations.stream()
                .filter(station -> station.worldName().equals(worldName))
                .toList();
    }

    private SplitLines splitStationLines(List<RailLine> lines, List<RailStation> stations) {
        if (stations.isEmpty()) {
            return new SplitLines(lines, List.of());
        }

        List<RailLine> routeLines = new ArrayList<>();
        List<RailLine> stationLines = new ArrayList<>();
        for (RailLine line : lines) {
            splitLine(line, stations, routeLines, stationLines);
        }

        return new SplitLines(List.copyOf(routeLines), List.copyOf(stationLines));
    }

    private void splitLine(
            RailLine line,
            List<RailStation> stations,
            List<RailLine> routeLines,
            List<RailLine> stationLines
    ) {
        if (line.points().size() < 2) {
            routeLines.add(line);
            return;
        }

        List<Vector3d> current = new ArrayList<>();
        Boolean currentStationInternal = null;
        for (int i = 1; i < line.points().size(); i++) {
            Vector3d previous = line.points().get(i - 1);
            Vector3d point = line.points().get(i);
            boolean stationInternal = segmentTouchesStation(previous, point, stations);

            if (currentStationInternal == null || currentStationInternal != stationInternal) {
                flushSplitLine(line, current, currentStationInternal, routeLines, stationLines);
                current = new ArrayList<>();
                current.add(previous);
                currentStationInternal = stationInternal;
            }

            current.add(point);
        }

        flushSplitLine(line, current, currentStationInternal, routeLines, stationLines);
    }

    private void flushSplitLine(
            RailLine source,
            List<Vector3d> points,
            Boolean stationInternal,
            List<RailLine> routeLines,
            List<RailLine> stationLines
    ) {
        if (stationInternal == null || points.size() < 2) {
            return;
        }

        RailLine split = source.withPoints(List.copyOf(points));
        if (stationInternal) {
            stationLines.add(split);
        } else {
            routeLines.add(split);
        }
    }

    private boolean passesStation(RailLine line, RailStation station) {
        for (int i = 1; i < line.points().size(); i++) {
            if (segmentTouchesStation(line.points().get(i - 1), line.points().get(i), List.of(station))) {
                return true;
            }
        }

        return line.points().stream().anyMatch(station::contains);
    }

    private boolean segmentTouchesStation(Vector3d a, Vector3d b, List<RailStation> stations) {
        Vector3d middle = new Vector3d(
                (a.getX() + b.getX()) / 2.0,
                (a.getY() + b.getY()) / 2.0,
                (a.getZ() + b.getZ()) / 2.0
        );
        for (RailStation station : stations) {
            if (station.contains(a) || station.contains(b) || station.contains(middle)) {
                return true;
            }
        }

        return false;
    }

    private void addStationBounds(MarkerSet markerSet, RailStation station) {
        if (!plugin.getConfig().getBoolean("stations.bounds.enabled", true)) {
            return;
        }

        String id = escapeId(station.id());
        double minX = station.minX();
        double minY = station.minY();
        double minZ = station.minZ();
        double maxX = station.maxX() + 1.0;
        double maxY = station.maxY() + 1.0;
        double maxZ = station.maxZ() + 1.0;

        Vector3d bottomNorthWest = new Vector3d(minX, minY, minZ);
        Vector3d bottomNorthEast = new Vector3d(maxX, minY, minZ);
        Vector3d bottomSouthEast = new Vector3d(maxX, minY, maxZ);
        Vector3d bottomSouthWest = new Vector3d(minX, minY, maxZ);
        Vector3d topNorthWest = new Vector3d(minX, maxY, minZ);
        Vector3d topNorthEast = new Vector3d(maxX, maxY, minZ);
        Vector3d topSouthEast = new Vector3d(maxX, maxY, maxZ);
        Vector3d topSouthWest = new Vector3d(minX, maxY, maxZ);

        markerSet.put("station-bounds-" + id + "-bottom", stationBoundsMarker(station, List.of(
                bottomNorthWest, bottomNorthEast, bottomSouthEast, bottomSouthWest, bottomNorthWest
        )));
        markerSet.put("station-bounds-" + id + "-top", stationBoundsMarker(station, List.of(
                topNorthWest, topNorthEast, topSouthEast, topSouthWest, topNorthWest
        )));
        markerSet.put("station-bounds-" + id + "-nw", stationBoundsMarker(station, List.of(bottomNorthWest, topNorthWest)));
        markerSet.put("station-bounds-" + id + "-ne", stationBoundsMarker(station, List.of(bottomNorthEast, topNorthEast)));
        markerSet.put("station-bounds-" + id + "-se", stationBoundsMarker(station, List.of(bottomSouthEast, topSouthEast)));
        markerSet.put("station-bounds-" + id + "-sw", stationBoundsMarker(station, List.of(bottomSouthWest, topSouthWest)));
    }

    private LineMarker stationBoundsMarker(RailStation station, List<Vector3d> points) {
        Line.Builder lineBuilder = Line.builder();
        for (Vector3d point : points) {
            lineBuilder.addPoint(point);
        }

        return LineMarker.builder()
                .label(station.name() + " / 站点范围")
                .line(lineBuilder.build())
                .lineWidth(plugin.getConfig().getInt("stations.bounds.line-width", 2))
                .lineColor(new Color(plugin.getConfig().getString("stations.bounds.color", "#fb7185")))
                .depthTestEnabled(plugin.getConfig().getBoolean("stations.bounds.depth-test-enabled", false))
                .listed(false)
                .build();
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

    private record SplitLines(List<RailLine> routeLines, List<RailLine> stationLines) {
    }
}
