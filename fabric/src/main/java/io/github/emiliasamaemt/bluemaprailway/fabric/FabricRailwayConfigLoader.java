package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.config.RailwayCoreConfig;
import io.github.emiliasamaemt.bluemaprailway.config.RouteAutoMatchConfig;
import io.github.emiliasamaemt.bluemaprailway.scan.RailLineFilter;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FabricRailwayConfigLoader {

    private static final Yaml YAML = FabricYamlSupport.readerYaml();

    private FabricRailwayConfigLoader() {
    }

    public static Path dataDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("bluemaprailway");
    }

    public static Path configFile() {
        return dataDirectory().resolve("config.yml");
    }

    public static FabricRailwayConfig load(FabricRailwayLogger log) {
        ensureDefaultConfig(log);

        try (Reader reader = Files.newBufferedReader(configFile(), StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return FabricRailwayConfig.defaults();
            }
            return fromMap(castMap(map));
        } catch (IOException | RuntimeException exception) {
            log.warning("Failed to read Fabric config.yml, using defaults: " + FabricYamlSupport.errorMessage(exception));
            return FabricRailwayConfig.defaults();
        }
    }

    private static void ensureDefaultConfig(FabricRailwayLogger log) {
        try {
            Files.createDirectories(dataDirectory());
            if (Files.exists(configFile())) {
                return;
            }
            try (InputStream input = FabricRailwayConfigLoader.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (input == null) {
                    return;
                }
                Files.copy(input, configFile());
            }
        } catch (IOException exception) {
            log.warning("Failed to create default Fabric config.yml: " + exception.getMessage());
        }
    }

    private static FabricRailwayConfig fromMap(Map<String, Object> root) {
        FabricRailwayConfig defaults = FabricRailwayConfig.defaults();

        Map<String, FabricRailwayConfig.FabricWorldConfig> worlds = new LinkedHashMap<>();
        Map<String, Object> worldsSection = child(root, "worlds");
        if (worldsSection.isEmpty()) {
            worlds.putAll(defaults.worlds());
        } else {
            for (Map.Entry<String, Object> entry : worldsSection.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> worldMap)) {
                    continue;
                }
                Map<String, Object> typed = castMap(worldMap);
                worlds.put(entry.getKey(), new FabricRailwayConfig.FabricWorldConfig(
                        bool(typed, "enabled", true),
                        integer(typed, "scan-radius", 8)
                ));
            }
            if (worlds.isEmpty()) {
                worlds.putAll(defaults.worlds());
            }
        }

        Map<String, Object> scanner = child(root, "scanner");
        Map<String, Object> cache = child(root, "cache");
        Map<String, Object> filters = child(root, "filters");
        Map<String, Object> markers = child(root, "markers");
        Map<String, Object> colors = child(markers, "colors");
        Map<String, Object> export = child(root, "export");
        Map<String, Object> exportSvg = child(export, "svg");
        Map<String, Object> backup = child(root, "backup");
        Map<String, Object> routes = child(root, "routes");
        Map<String, Object> routeAutoMatch = child(routes, "auto-match");
        Map<String, Object> stations = child(root, "stations");
        Map<String, Object> stationBounds = child(stations, "bounds");
        Map<String, Object> stationInternalTracks = child(stations, "internal-tracks");
        Map<String, Object> adminWeb = child(root, "admin-web");
        Map<String, Object> background = child(adminWeb, "background");

        RailwayCoreConfig core = new RailwayCoreConfig(
                new RailLineFilter(
                        bool(filters, "hide-short-lines", defaults.core().lineFilter().hideShortLines()),
                        integer(filters, "short-line-max-points", defaults.core().lineFilter().shortLineMaxPoints()),
                        number(filters, "short-line-max-length", defaults.core().lineFilter().shortLineMaxLength()),
                        bool(filters, "hide-fragmented-plain-rail-below-min-y", defaults.core().lineFilter().hideFragmentedPlainRailBelowMinY()),
                        integer(filters, "min-y", defaults.core().lineFilter().minY()),
                        integer(filters, "fragmented-line-max-points", defaults.core().lineFilter().fragmentedLineMaxPoints()),
                        number(filters, "fragmented-line-max-length", defaults.core().lineFilter().fragmentedLineMaxLength())
                ),
                new RouteAutoMatchConfig(
                        bool(routeAutoMatch, "enabled", defaults.core().routeAutoMatch().enabled()),
                        number(routeAutoMatch, "anchor-radius", defaults.core().routeAutoMatch().anchorRadius()),
                        number(routeAutoMatch, "min-bounds-overlap-ratio", defaults.core().routeAutoMatch().minBoundsOverlapRatio())
                )
        );

        Map<String, String> mergedColors = new LinkedHashMap<>(defaults.colors());
        for (Map.Entry<String, Object> entry : colors.entrySet()) {
            if (entry.getValue() instanceof String value) {
                mergedColors.put(entry.getKey(), value);
            }
        }

        return new FabricRailwayConfig(
                worlds,
                core,
                new FabricRailwayConfig.FabricScannerConfig(
                        bool(scanner, "chunk-load-rescan", defaults.scanner().chunkLoadRescan()),
                        Math.max(0, integer(scanner, "update-debounce-ticks", defaults.scanner().updateDebounceTicks())),
                        Math.max(0, integer(scanner, "block-update-neighbor-radius", defaults.scanner().blockUpdateNeighborRadius()))
                ),
                new FabricRailwayConfig.FabricCacheConfig(
                        bool(cache, "enabled", defaults.cache().enabled()),
                        string(cache, "file", defaults.cache().file()),
                        bool(cache, "scan-newly-loaded-chunks", defaults.cache().scanNewlyLoadedChunks()),
                        Math.max(0, integer(cache, "chunk-load-debounce-ticks", defaults.cache().chunkLoadDebounceTicks()))
                ),
                string(markers, "set-id", defaults.markerSetId()),
                string(markers, "label", defaults.markerSetLabel()),
                bool(markers, "unclassified-ignore-rail-type", defaults.unclassifiedIgnoreRailType()),
                bool(markers, "route-ignore-rail-type", defaults.routeIgnoreRailType()),
                bool(markers, "default-hidden", defaults.defaultHidden()),
                integer(markers, "line-width", defaults.lineWidth()),
                bool(markers, "depth-test-enabled", defaults.depthTestEnabled()),
                number(markers, "y-offset", defaults.yOffset()),
                mergedColors,
                new FabricRailwayConfig.FabricExportConfig(
                        new FabricRailwayConfig.FabricSvgExportConfig(
                                bool(exportSvg, "enabled", defaults.export().svg().enabled())
                        )
                ),
                new FabricRailwayConfig.FabricBackupConfig(
                        bool(backup, "enabled", defaults.backup().enabled()),
                        Math.max(1, integer(backup, "interval-hours", defaults.backup().intervalHours())),
                        string(backup, "directory", defaults.backup().directory()),
                        bool(backup, "include-config", defaults.backup().includeConfig()),
                        integer(backup, "max-files", defaults.backup().maxFiles())
                ),
                new FabricRailwayConfig.FabricStationsConfig(
                        number(stations, "default-radius", defaults.stations().defaultRadius()),
                        integer(stations, "default-y-radius", defaults.stations().defaultYRadius()),
                        string(stations, "marker-set-label", defaults.stations().markerSetLabel()),
                        new FabricRailwayConfig.FabricStationBoundsConfig(
                                bool(stationBounds, "enabled", defaults.stations().bounds().enabled()),
                                string(stationBounds, "color", defaults.stations().bounds().color()),
                                integer(stationBounds, "line-width", defaults.stations().bounds().lineWidth()),
                                bool(stationBounds, "depth-test-enabled", defaults.stations().bounds().depthTestEnabled())
                        ),
                        new FabricRailwayConfig.FabricStationInternalTracksConfig(
                                string(stationInternalTracks, "label", defaults.stations().internalTracks().label()),
                                bool(stationInternalTracks, "default-hidden", defaults.stations().internalTracks().defaultHidden())
                        )
                ),
                new FabricRailwayConfig.FabricAdminWebConfig(
                        bool(adminWeb, "enabled", defaults.adminWeb().enabled()),
                        string(adminWeb, "host", defaults.adminWeb().host()),
                        integer(adminWeb, "port", defaults.adminWeb().port()),
                        string(adminWeb, "token", defaults.adminWeb().token()),
                        string(background, "image", defaults.adminWeb().backgroundImage()),
                        string(background, "world", defaults.adminWeb().backgroundWorld()),
                        number(background, "center-x", defaults.adminWeb().backgroundCenterX()),
                        number(background, "center-z", defaults.adminWeb().backgroundCenterZ()),
                        number(background, "pixels-per-block", defaults.adminWeb().backgroundPixelsPerBlock())
                )
        );
    }

    private static Map<String, Object> child(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String string ? string : fallback;
    }
}
