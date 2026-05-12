package dev.kokomi.bluemaprailway.scan;

import org.bukkit.configuration.file.FileConfiguration;

public record RailLineFilter(
        boolean hideFragmentedPlainRailBelowMinY,
        int minY,
        int fragmentedLineMaxPoints,
        double fragmentedLineMaxLength
) {

    public static RailLineFilter fromConfig(FileConfiguration config) {
        return new RailLineFilter(
                config.getBoolean("filters.hide-fragmented-plain-rail-below-min-y", true),
                config.getInt("filters.min-y", 50),
                config.getInt("filters.fragmented-line-max-points", 8),
                config.getDouble("filters.fragmented-line-max-length", 32.0)
        );
    }
}
