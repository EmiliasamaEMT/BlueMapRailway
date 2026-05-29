package io.github.emiliasamaemt.bluemaprailway.scan;

public record RailLineFilter(
        boolean hideShortLines,
        int shortLineMaxPoints,
        double shortLineMaxLength,
        boolean hideFragmentedPlainRailBelowMinY,
        int minY,
        int fragmentedLineMaxPoints,
        double fragmentedLineMaxLength
) {
}
