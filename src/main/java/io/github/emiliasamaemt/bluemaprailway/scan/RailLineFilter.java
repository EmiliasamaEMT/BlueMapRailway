package io.github.emiliasamaemt.bluemaprailway.scan;

import org.bukkit.configuration.file.FileConfiguration;

public record RailLineFilter(
        boolean hideShortLines,
        int shortLineMaxPoints,
        double shortLineMaxLength,
        boolean hideFragmentedPlainRailBelowMinY,
        int minY,
        int fragmentedLineMaxPoints,
        double fragmentedLineMaxLength
) {

    public static RailLineFilter fromConfig(FileConfiguration config) {
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
}
