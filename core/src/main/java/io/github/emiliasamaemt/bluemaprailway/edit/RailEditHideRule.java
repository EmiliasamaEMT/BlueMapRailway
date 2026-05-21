package io.github.emiliasamaemt.bluemaprailway.edit;

import io.github.emiliasamaemt.bluemaprailway.model.RailLine;

import java.util.Set;

public record RailEditHideRule(
        String id,
        String name,
        boolean enabled,
        Set<String> routeIds,
        Set<String> componentIds
) {

    public RailEditHideRule {
        routeIds = Set.copyOf(routeIds);
        componentIds = Set.copyOf(componentIds);
    }

    public boolean hides(RailLine line) {
        if (!enabled) {
            return false;
        }

        if (componentIds.contains(line.componentId())) {
            return true;
        }

        String routeId = line.routeId();
        return routeId != null && routeIds.contains(routeId);
    }
}
