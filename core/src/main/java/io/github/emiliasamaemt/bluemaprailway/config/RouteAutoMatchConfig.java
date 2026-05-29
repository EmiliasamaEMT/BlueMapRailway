package io.github.emiliasamaemt.bluemaprailway.config;

public record RouteAutoMatchConfig(
        boolean enabled,
        double anchorRadius,
        double minBoundsOverlapRatio
) {
}
