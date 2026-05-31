package io.github.emiliasamaemt.bluemaprailway.fabric;

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
import io.github.emiliasamaemt.bluemaprailway.render.RailLineMergeOptimizer;
import io.github.emiliasamaemt.bluemaprailway.station.RailStation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class FabricBlueMapRailRenderer {

    private final FabricRailwayConfig config;

    public FabricBlueMapRailRenderer(FabricRailwayConfig config) {
        this.config = config;
    }

    public void render(BlueMapAPI api, RailScanResult result, List<RailStation> stations) {
        Map<String, List<RailLine>> linesByWorld = new HashMap<>();
        for (RailLine line : result.lines()) {
            linesByWorld.computeIfAbsent(line.worldName(), ignored -> new ArrayList<>()).add(line);
        }

        for (String worldName : renderWorldNames(linesByWorld, stations)) {
            renderWorld(api, worldName, linesByWorld.getOrDefault(worldName, List.of()), stations);
        }
    }

    private void renderWorld(BlueMapAPI api, String worldName, List<RailLine> lines, List<RailStation> stations) {
        List<RailStation> worldStations = worldStations(worldName, stations);
        SplitLines splitLines = splitStationLines(lines, worldStations);
        List<RailLine> mergedRouteLines = RailLineMergeOptimizer.merge(splitLines.routeLines(), this::mergeStyleKey);
        List<RailLine> mergedStationLines = RailLineMergeOptimizer.merge(splitLines.stationLines(), this::mergeStyleKey);
        Map<String, MarkerSet> markerSets = buildRouteMarkerSets(mergedRouteLines);
        MarkerSet stationInternalMarkerSet = buildStationInternalMarkerSet(mergedStationLines);
        MarkerSet stationMarkerSet = buildStationMarkerSet(worldStations, lines);

        api.getWorld(worldName).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                clearRailwayMarkerSets(map);

                for (Map.Entry<String, MarkerSet> entry : markerSets.entrySet()) {
                    map.getMarkerSets().put(entry.getKey(), entry.getValue());
                }

                if (stationInternalMarkerSet != null) {
                    map.getMarkerSets().put(stationInternalMarkerSetId(), stationInternalMarkerSet);
                }

                if (stationMarkerSet != null) {
                    map.getMarkerSets().put(stationMarkerSetId(), stationMarkerSet);
                }
            }
        });
    }

    private Map<String, MarkerSet> buildRouteMarkerSets(List<RailLine> lines) {
        Map<String, MarkerSet> markerSets = new LinkedHashMap<>();
        Map<String, Integer> indexes = new HashMap<>();

        for (RailLine line : lines) {
            String id = routeMarkerSetId(line);
            MarkerSet markerSet = markerSets.computeIfAbsent(id, ignored -> MarkerSet.builder()
                    .label(routeMarkerSetLabel(line))
                    .defaultHidden(config.defaultHidden())
                    .build());

            int index = indexes.getOrDefault(id, 0);
            markerSet.put("rail-line-" + index, toMarker(line, index));
            indexes.put(id, index + 1);
        }

        return markerSets;
    }

    private MarkerSet buildStationInternalMarkerSet(List<RailLine> lines) {
        if (lines.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = MarkerSet.builder()
                .label(config.stations().internalTracks().label())
                .defaultHidden(config.stations().internalTracks().defaultHidden())
                .build();

        int index = 0;
        for (RailLine line : lines) {
            markerSet.put("station-rail-line-" + index, toMarker(line, index));
            index++;
        }

        return markerSet;
    }

    private MarkerSet buildStationMarkerSet(List<RailStation> stations, List<RailLine> lines) {
        if (stations.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = MarkerSet.builder()
                .label(config.stations().markerSetLabel())
                .defaultHidden(config.defaultHidden())
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

        for (Map.Entry<String, FabricRailwayConfig.FabricWorldConfig> entry : config.worlds().entrySet()) {
            if (entry.getValue().enabled()) {
                worldNames.add(entry.getKey());
            }
        }

        return worldNames;
    }

    private void clearRailwayMarkerSets(BlueMapMap map) {
        map.getMarkerSets().keySet().removeIf(id ->
                id.equals(config.markerSetId()) ||
                        id.equals(stationMarkerSetId()) ||
                        id.equals(stationInternalMarkerSetId()) ||
                        id.equals(unclassifiedMarkerSetId()) ||
                        id.startsWith(config.markerSetId() + ".route.")
        );
    }

    private LineMarker toMarker(RailLine line, int index) {
        Line.Builder lineBuilder = Line.builder();
        for (Vector3d point : line.points()) {
            lineBuilder.addPoint(point);
        }

        return LineMarker.builder()
                .label(labelFor(line, index))
                .line(lineBuilder.build())
                .lineWidth(lineWidthFor(line))
                .lineColor(new Color(colorFor(line)))
                .depthTestEnabled(config.depthTestEnabled())
                .listed(false)
                .build();
    }

    private String labelFor(RailLine line, int index) {
        String routeName = line.routeName();
        if (routeName != null && !routeName.isBlank()) {
            return routeName + " / " + shortComponentId(line.componentId()) + " / 铁路段" + (index + 1);
        }

        return shortComponentId(line.componentId()) + " / 铁路段" + (index + 1);
    }

    private int lineWidthFor(RailLine line) {
        return line.routeLineWidth() > 0 ? line.routeLineWidth() : config.lineWidth();
    }

    private String routeMarkerSetId(RailLine line) {
        if (line.routeId() == null || line.routeId().isBlank()) {
            return unclassifiedMarkerSetId();
        }
        return config.markerSetId() + ".route." + escapeId(line.routeId());
    }

    private String routeMarkerSetLabel(RailLine line) {
        if (line.routeName() == null || line.routeName().isBlank()) {
            return config.markerSetLabel() + " - 未分类";
        }
        return line.routeName();
    }

    private String unclassifiedMarkerSetId() {
        return config.markerSetId() + ".unclassified";
    }

    private String stationMarkerSetId() {
        return config.markerSetId() + ".stations";
    }

    private String stationInternalMarkerSetId() {
        return config.markerSetId() + ".station-internal";
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
            if (!passesStation(line, station)) {
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
        for (int index = 1; index < line.points().size(); index++) {
            Vector3d previous = line.points().get(index - 1);
            Vector3d point = line.points().get(index);
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
        for (int index = 1; index < line.points().size(); index++) {
            if (segmentTouchesStation(line.points().get(index - 1), line.points().get(index), List.of(station))) {
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
        if (!config.stations().bounds().enabled()) {
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
                .lineWidth(config.stations().bounds().lineWidth())
                .lineColor(new Color(config.stations().bounds().color()))
                .depthTestEnabled(config.stations().bounds().depthTestEnabled())
                .listed(false)
                .build();
    }

    private String shortComponentId(String componentId) {
        int index = componentId.lastIndexOf(':');
        if (index < 0 || index == componentId.length() - 1) {
            return componentId;
        }
        return componentId.substring(index + 1);
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

    private String colorFor(RailLine line) {
        if (shouldIgnoreRailTypeForUnclassified(line)) {
            return config.colors().getOrDefault("rail", "#9ca3af");
        }

        String routeColor = line.routeColor();
        if (routeColor != null && !routeColor.isBlank()) {
            return routeColor;
        }

        if (line.type() == RailType.POWERED_RAIL && !line.powered()) {
            return config.colors().getOrDefault("powered-rail-inactive", "#65a30d");
        }

        return switch (line.type()) {
            case RAIL -> config.colors().getOrDefault("rail", "#9ca3af");
            case POWERED_RAIL -> config.colors().getOrDefault("powered-rail", "#22c55e");
            case DETECTOR_RAIL -> config.colors().getOrDefault("detector-rail", "#f59e0b");
            case ACTIVATOR_RAIL -> config.colors().getOrDefault("activator-rail", "#ef4444");
        };
    }

    private String mergeStyleKey(RailLine line) {
        if (shouldIgnoreRailTypeForUnclassified(line)) {
            return "unclassified|" + lineWidthFor(line);
        }
        return colorFor(line) + "|" + lineWidthFor(line);
    }

    private boolean shouldIgnoreRailTypeForUnclassified(RailLine line) {
        return config.unclassifiedIgnoreRailType() && (line.routeId() == null || line.routeId().isBlank());
    }

    private record SplitLines(List<RailLine> routeLines, List<RailLine> stationLines) {
    }
}
