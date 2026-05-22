package io.github.emiliasamaemt.bluemaprailway.fabric;

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
import com.flowpowered.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FabricBlueMapRailRenderer {

    private final FabricRailwayConfig config;

    public FabricBlueMapRailRenderer(FabricRailwayConfig config) {
        this.config = config;
    }

    public void render(BlueMapAPI api, RailScanResult result, List<RailStation> stations) {
        Map<String, List<RailLine>> byWorld = new HashMap<>();
        for (RailLine line : result.lines()) {
            byWorld.computeIfAbsent(line.worldName(), ignored -> new ArrayList<>()).add(line);
        }

        for (String worldId : renderWorldIds(byWorld, stations)) {
            renderWorld(api, worldId, byWorld.getOrDefault(worldId, List.of()), worldStations(worldId, stations));
        }
    }

    private void renderWorld(BlueMapAPI api, String worldId, List<RailLine> lines, List<RailStation> stations) {
        api.getWorld(worldId).ifPresentOrElse(world -> {
            Map<String, MarkerSet> markerSets = buildRouteMarkerSets(lines);
            MarkerSet stationMarkerSet = buildStationMarkerSet(stations);

            for (BlueMapMap map : world.getMaps()) {
                clearRailMarkerSets(map);
                for (Map.Entry<String, MarkerSet> entry : markerSets.entrySet()) {
                    map.getMarkerSets().put(entry.getKey(), entry.getValue());
                }
                if (stationMarkerSet != null) {
                    map.getMarkerSets().put(stationMarkerSetId(), stationMarkerSet);
                }
            }
        }, () -> {
        });
    }

    private Map<String, MarkerSet> buildRouteMarkerSets(List<RailLine> lines) {
        Map<String, MarkerSet> markerSets = new LinkedHashMap<>();
        Map<String, Integer> indexes = new HashMap<>();

        for (RailLine line : lines) {
            String markerSetId = routeMarkerSetId(line);
            MarkerSet markerSet = markerSets.computeIfAbsent(markerSetId, ignored -> MarkerSet.builder()
                    .label(routeMarkerSetLabel(line))
                    .defaultHidden(config.defaultHidden())
                    .build());

            int index = indexes.getOrDefault(markerSetId, 0);
            LineMarker marker = LineMarker.builder()
                    .label(labelFor(line))
                    .line(new Line(line.points()))
                    .lineWidth(lineWidthFor(line))
                    .lineColor(new Color(colorFor(line)))
                    .depthTestEnabled(config.depthTestEnabled())
                    .build();
            markerSet.put("rail-" + index, marker);
            indexes.put(markerSetId, index + 1);
        }

        return markerSets;
    }

    private void clearRailMarkerSets(BlueMapMap map) {
        map.getMarkerSets().entrySet().removeIf(entry ->
                entry.getKey().equals(unclassifiedMarkerSetId()) ||
                        entry.getKey().equals(stationMarkerSetId()) ||
                        entry.getKey().startsWith(config.markerSetId() + ".route."));
    }

    private String labelFor(RailLine line) {
        if (line.routeName() != null && !line.routeName().isBlank()) {
            return line.routeName() + " / " + shortComponentId(line.componentId());
        }

        return line.type().name() + " / " + shortComponentId(line.componentId());
    }

    private int lineWidthFor(RailLine line) {
        return line.routeLineWidth() > 0 ? line.routeLineWidth() : config.lineWidth();
    }

    private String routeMarkerSetId(RailLine line) {
        if (!line.hasRoute()) {
            return unclassifiedMarkerSetId();
        }

        return config.markerSetId() + ".route." + escapeId(line.routeId());
    }

    private String routeMarkerSetLabel(RailLine line) {
        if (!line.hasRoute() || line.routeName() == null || line.routeName().isBlank()) {
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

    private String shortComponentId(String componentId) {
        int index = componentId.lastIndexOf(':');
        if (index < 0 || index == componentId.length() - 1) {
            return componentId;
        }

        return componentId.substring(index + 1);
    }

    private String escapeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private Set<String> renderWorldIds(Map<String, List<RailLine>> linesByWorld, List<RailStation> stations) {
        Set<String> worldIds = new HashSet<>(linesByWorld.keySet());
        for (RailStation station : stations) {
            worldIds.add(station.worldName());
        }
        return worldIds;
    }

    private List<RailStation> worldStations(String worldId, List<RailStation> stations) {
        return stations.stream()
                .filter(station -> station.worldName().equals(worldId))
                .toList();
    }

    private MarkerSet buildStationMarkerSet(List<RailStation> stations) {
        if (stations.isEmpty()) {
            return null;
        }

        MarkerSet markerSet = MarkerSet.builder()
                .label("站点")
                .defaultHidden(config.defaultHidden())
                .build();

        for (RailStation station : stations) {
            markerSet.put("station-" + escapeId(station.id()), POIMarker.builder()
                    .label(station.name())
                    .position(station.center())
                    .detail(stationDetail(station))
                    .defaultIcon()
                    .listed(true)
                    .build());
            addStationBounds(markerSet, station);
        }

        return markerSet;
    }

    private String stationDetail(RailStation station) {
        return "ID: " + escapeHtml(station.id()) +
                "<br>World: " + escapeHtml(station.worldName()) +
                "<br>Area: [" + station.minX() + "," + station.minY() + "," + station.minZ() + "] -> [" +
                station.maxX() + "," + station.maxY() + "," + station.maxZ() + "]";
    }

    private void addStationBounds(MarkerSet markerSet, RailStation station) {
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
        return LineMarker.builder()
                .label(station.name() + " / 站点范围")
                .line(new Line(points))
                .lineWidth(2)
                .lineColor(new Color("#fb7185"))
                .depthTestEnabled(false)
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

    private String colorFor(RailLine line) {
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
}
