package io.github.emiliasamaemt.bluemaprailway.edit;

import com.flowpowered.math.vector.Vector3d;

public record RailEditMask(
        String id,
        String name,
        String worldName,
        boolean enabled,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public RailEditMask {
        int originalMinX = minX;
        int originalMinY = minY;
        int originalMinZ = minZ;
        minX = Math.min(originalMinX, maxX);
        minY = Math.min(originalMinY, maxY);
        minZ = Math.min(originalMinZ, maxZ);
        maxX = Math.max(originalMinX, maxX);
        maxY = Math.max(originalMinY, maxY);
        maxZ = Math.max(originalMinZ, maxZ);
    }

    public boolean appliesTo(String lineWorldName) {
        return enabled && worldName.equals(lineWorldName);
    }

    public boolean contains(Vector3d point) {
        return point.getX() >= minX && point.getX() <= maxX + 1.0 &&
                point.getY() >= minY && point.getY() <= maxY + 1.0 &&
                point.getZ() >= minZ && point.getZ() <= maxZ + 1.0;
    }
}
