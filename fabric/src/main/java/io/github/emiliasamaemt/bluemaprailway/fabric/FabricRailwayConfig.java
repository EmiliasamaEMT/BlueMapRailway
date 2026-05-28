package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.config.RailwayCoreConfig;
import io.github.emiliasamaemt.bluemaprailway.config.RouteAutoMatchConfig;
import io.github.emiliasamaemt.bluemaprailway.scan.RailLineFilter;

import java.util.LinkedHashMap;
import java.util.Map;

public record FabricRailwayConfig(
        Map<String, FabricWorldConfig> worlds,
        RailwayCoreConfig core,
        FabricScannerConfig scanner,
        FabricCacheConfig cache,
        String markerSetId,
        String markerSetLabel,
        boolean defaultHidden,
        int lineWidth,
        boolean depthTestEnabled,
        double yOffset,
        Map<String, String> colors,
        FabricExportConfig export,
        FabricBackupConfig backup,
        FabricStationsConfig stations,
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
                new FabricScannerConfig(
                        true,
                        200,
                        1
                ),
                new FabricCacheConfig(
                        true,
                        "cache/rail-cache.yml",
                        true,
                        200
                ),
                "railways",
                "Railways",
                false,
                3,
                false,
                0.35,
                Map.of(
                        "rail", "#9ca3af",
                        "powered-rail", "#22c55e",
                        "powered-rail-inactive", "#65a30d",
                        "detector-rail", "#f59e0b",
                        "activator-rail", "#ef4444"
                ),
                new FabricExportConfig(
                        new FabricSvgExportConfig(false)
                ),
                new FabricBackupConfig(
                        true,
                        24,
                        "backups",
                        true,
                        0
                ),
                new FabricStationsConfig(
                        24.0,
                        6,
                        "站点",
                        new FabricStationBoundsConfig(
                                true,
                                "#fb7185",
                                2,
                                false
                        ),
                        new FabricStationInternalTracksConfig(
                                "站内轨道",
                                false
                        )
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

    public record FabricScannerConfig(
            boolean chunkLoadRescan,
            int updateDebounceTicks,
            int blockUpdateNeighborRadius
    ) {
    }

    public record FabricCacheConfig(
            boolean enabled,
            String file,
            boolean scanNewlyLoadedChunks,
            int chunkLoadDebounceTicks
    ) {
    }

    public record FabricBackupConfig(
            boolean enabled,
            int intervalHours,
            String directory,
            boolean includeConfig,
            int maxFiles
    ) {
    }

    public record FabricExportConfig(
            FabricSvgExportConfig svg
    ) {
    }

    public record FabricSvgExportConfig(
            boolean enabled
    ) {
    }

    public record FabricStationsConfig(
            double defaultRadius,
            int defaultYRadius,
            String markerSetLabel,
            FabricStationBoundsConfig bounds,
            FabricStationInternalTracksConfig internalTracks
    ) {
    }

    public record FabricStationBoundsConfig(
            boolean enabled,
            String color,
            int lineWidth,
            boolean depthTestEnabled
    ) {
    }

    public record FabricStationInternalTracksConfig(
            String label,
            boolean defaultHidden
    ) {
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
