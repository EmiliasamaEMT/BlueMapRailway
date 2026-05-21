package io.github.emiliasamaemt.bluemaprailway.config;

import io.github.emiliasamaemt.bluemaprailway.scan.RailLineFilter;
import org.bukkit.configuration.file.FileConfiguration;

public final class PaperRailwayCoreConfig {

    private PaperRailwayCoreConfig() {
    }

    public static RailwayCoreConfig from(FileConfiguration config) {
        return new RailwayCoreConfig(
                lineFilter(config),
                routeAutoMatch(config)
        );
    }

    public static RailLineFilter lineFilter(FileConfiguration config) {
        return new RailLineFilter(
                config.getBoolean("filters.hide-short-lines", true),
                config.getInt("filters.short-line-max-points", 3),
                config.getDouble("filters.short-line-max-length", 6.0),
                config.getBoolean("filters.hide-fragmented-plain-rail-below-min-y", true),
                config.getInt("filters.min-y", 50),
                config.getInt("filters.fragmented-line-max-points", 8),
                config.getDouble("filters.fragmented-line-max-length", 32.0)
        );
    }

    public static RouteAutoMatchConfig routeAutoMatch(FileConfiguration config) {
        return new RouteAutoMatchConfig(
                config.getBoolean("routes.auto-match.enabled", true),
                Math.max(0.0, config.getDouble("routes.auto-match.anchor-radius", 16.0)),
                Math.max(0.0, config.getDouble("routes.auto-match.min-bounds-overlap-ratio", 0.35))
        );
    }
}
