package io.github.emiliasamaemt.bluemaprailway.station;

import com.flowpowered.math.vector.Vector3d;

public record RailStation(
        String id,
        String name,
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public Vector3d center() {
        return new Vector3d(
                (minX + maxX) / 2.0 + 0.5,
                (minY + maxY) / 2.0 + 0.5,
                (minZ + maxZ) / 2.0 + 0.5
        );
    }
}
