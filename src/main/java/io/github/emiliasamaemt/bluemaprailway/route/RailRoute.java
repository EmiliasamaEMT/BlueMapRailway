package io.github.emiliasamaemt.bluemaprailway.route;

import java.util.List;
import java.util.Set;

public record RailRoute(
        String id,
        String name,
        String color,
        int lineWidth,
        Set<String> componentIds,
        List<RailRouteAnchor> anchors,
        RailRouteBounds bounds,
        boolean autoMatch
) {

    public RailRoute withAutoMatchedComponent(String componentId, RailRouteAnchor anchor, RailRouteBounds updatedBounds) {
        Set<String> updatedComponents = new java.util.LinkedHashSet<>(componentIds);
        updatedComponents.add(componentId);

        List<RailRouteAnchor> updatedAnchors = new java.util.ArrayList<>(anchors);
        if (!updatedAnchors.contains(anchor)) {
            updatedAnchors.add(anchor);
        }

        return new RailRoute(
                id,
                name,
                color,
                lineWidth,
                Set.copyOf(updatedComponents),
                List.copyOf(updatedAnchors),
                updatedBounds,
                autoMatch
        );
    }
}
