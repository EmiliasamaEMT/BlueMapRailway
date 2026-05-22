package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.config.RailwayCoreConfig;
import io.github.emiliasamaemt.bluemaprailway.config.RouteAutoMatchConfig;
import io.github.emiliasamaemt.bluemaprailway.scan.RailLineFilter;

import java.util.LinkedHashMap;
import java.util.Map;

public record FabricRailwayConfig(
        Map<String, FabricWorldConfig> worlds,
        RailwayCoreConfig core,
        boolean chunkLoadRescan,
        String markerSetId,
        String markerSetLabel,
        boolean defaultHidden,
        int lineWidth,
        boolean depthTestEnabled,
        double yOffset,
        Map<String, String> colors,
        FabricAdminWebConfig adminWeb
) {

    public FabricRailwayConfig {
        worlds = Map.copyOf(worlds);
        colors = Map.copyOf(colors);
    }

    public static FabricRailwayConfig defaults() {
        Map<String, FabricWorldConfig> worlds = new LinkedHashMap<>();
        worlds.put("minecraft:overworld", new FabricWorldConfig(true, 8));

        return new FabricRailwayConfig(
                worlds,
                new RailwayCoreConfig(
                        new RailLineFilter(true, 3, 6.0, true, 50, 8, 32.0),
                        new RouteAutoMatchConfig(true, 16.0, 0.35)
                ),
                true,
                "railways",
                "Railways",
                false,
                5,
                false,
                0.35,
                Map.of(
                        "rail", "#9ca3af",
                        "powered-rail", "#22c55e",
                        "powered-rail-inactive", "#65a30d",
                        "detector-rail", "#f59e0b",
                        "activator-rail", "#ef4444"
                ),
                new FabricAdminWebConfig(
                        false,
                        "127.0.0.1",
                        8766,
                        "change-me",
                        "admin-web/background.png",
                        "minecraft:overworld",
                        0.0,
                        0.0,
                        1.0
                )
        );
    }

    public record FabricWorldConfig(boolean enabled, int scanRadius) {
    }

    public record FabricAdminWebConfig(
            boolean enabled,
            String host,
            int port,
            String token,
            String backgroundImage,
            String backgroundWorld,
            double backgroundCenterX,
            double backgroundCenterZ,
            double backgroundPixelsPerBlock
    ) {
    }
}
