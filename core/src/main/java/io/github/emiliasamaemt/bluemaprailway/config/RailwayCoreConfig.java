package io.github.emiliasamaemt.bluemaprailway.config;

import io.github.emiliasamaemt.bluemaprailway.scan.RailLineFilter;

public record RailwayCoreConfig(
        RailLineFilter lineFilter,
        RouteAutoMatchConfig routeAutoMatch
) {
}
