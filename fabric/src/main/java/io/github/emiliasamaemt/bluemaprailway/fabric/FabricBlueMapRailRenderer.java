package io.github.emiliasamaemt.bluemaprailway.fabric;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FabricBlueMapRailRenderer {

    private final FabricRailwayConfig config;

    public FabricBlueMapRailRenderer(FabricRailwayConfig config) {
        this.config = config;
    }

    public void render(BlueMapAPI api, RailScanResult result) {
        Map<String, List<RailLine>> byWorld = new HashMap<>();
        for (RailLine line : result.lines()) {
            byWorld.computeIfAbsent(line.worldName(), ignored -> new ArrayList<>()).add(line);
        }

        for (Map.Entry<String, List<RailLine>> entry : byWorld.entrySet()) {
            renderWorld(api, entry.getKey(), entry.getValue());
        }
    }

    private void renderWorld(BlueMapAPI api, String worldId, List<RailLine> lines) {
        api.getWorld(worldId).ifPresentOrElse(world -> {
            MarkerSet markerSet = MarkerSet.builder()
                    .label(config.markerSetLabel())
                    .defaultHidden(config.defaultHidden())
                    .build();

            int index = 0;
            for (RailLine line : lines) {
                LineMarker marker = LineMarker.builder()
                        .label(labelFor(line))
                        .line(new Line(line.points()))
                        .lineWidth(config.lineWidth())
                        .lineColor(new Color(colorFor(line)))
                        .depthTestEnabled(config.depthTestEnabled())
                        .build();
                markerSet.put("rail-" + index++, marker);
            }

            for (BlueMapMap map : world.getMaps()) {
                clearRailMarkerSets(map);
                map.getMarkerSets().put(config.markerSetId(), markerSet);
            }
        }, () -> {
        });
    }

    private void clearRailMarkerSets(BlueMapMap map) {
        map.getMarkerSets().entrySet().removeIf(entry -> entry.getKey().equals(config.markerSetId()));
    }

    private String labelFor(RailLine line) {
        return line.type().name() + " / " + line.componentId();
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
