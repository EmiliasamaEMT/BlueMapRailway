package io.github.emiliasamaemt.bluemaprailway.route;

import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;

public record RailRouteAnchor(String worldName, int x, int y, int z) {

    public static RailRouteAnchor of(RailPosition position) {
        return new RailRouteAnchor(position.worldName(), position.x(), position.y(), position.z());
    }

    public double distanceSquared(RailPosition position) {
        if (!worldName.equals(position.worldName())) {
            return Double.POSITIVE_INFINITY;
        }

        double dx = x - position.x();
        double dy = y - position.y();
        double dz = z - position.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
