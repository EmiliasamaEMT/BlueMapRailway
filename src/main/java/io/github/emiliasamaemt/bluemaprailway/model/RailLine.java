package io.github.emiliasamaemt.bluemaprailway.model;

import com.flowpowered.math.vector.Vector3d;

import java.util.List;

public record RailLine(
        String componentId,
        String worldName,
        RailType type,
        boolean powered,
        List<Vector3d> points,
        String routeId,
        String routeName,
        String routeColor,
        int routeLineWidth
) {

    public RailLine(String componentId, String worldName, RailType type, boolean powered, List<Vector3d> points) {
        this(componentId, worldName, type, powered, points, null, null, null, -1);
    }

    public boolean hasRoute() {
        return routeId != null;
    }

    public RailLine withRoute(String routeId, String routeName, String routeColor, int routeLineWidth) {
        return new RailLine(componentId, worldName, type, powered, points, routeId, routeName, routeColor, routeLineWidth);
    }

    public RailLine withPoints(List<Vector3d> points) {
        return new RailLine(componentId, worldName, type, powered, points, routeId, routeName, routeColor, routeLineWidth);
    }
}
