package io.github.emiliasamaemt.bluemaprailway.model;

import java.util.List;

public record RailComponent(
        String id,
        String worldName,
        List<RailPosition> positions,
        boolean plainRailOnly,
        double length,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public int pointCount() {
        return positions.size();
    }
}
