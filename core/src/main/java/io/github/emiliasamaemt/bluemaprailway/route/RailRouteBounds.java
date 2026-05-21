package io.github.emiliasamaemt.bluemaprailway.route;

import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;

public record RailRouteBounds(
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public static RailRouteBounds of(RailComponent component) {
        return new RailRouteBounds(
                component.worldName(),
                component.minX(),
                component.minY(),
                component.minZ(),
                component.maxX(),
                component.maxY(),
                component.maxZ()
        );
    }

    public double overlapRatio(RailComponent component) {
        if (!worldName.equals(component.worldName())) {
            return 0.0;
        }

        long oldVolume = volume(minX, minY, minZ, maxX, maxY, maxZ);
        if (oldVolume <= 0) {
            return 0.0;
        }

        int overlapMinX = Math.max(minX, component.minX());
        int overlapMinY = Math.max(minY, component.minY());
        int overlapMinZ = Math.max(minZ, component.minZ());
        int overlapMaxX = Math.min(maxX, component.maxX());
        int overlapMaxY = Math.min(maxY, component.maxY());
        int overlapMaxZ = Math.min(maxZ, component.maxZ());

        long overlapVolume = volume(overlapMinX, overlapMinY, overlapMinZ, overlapMaxX, overlapMaxY, overlapMaxZ);
        return overlapVolume / (double) oldVolume;
    }

    private static long volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long width = Math.max(0, maxX - minX + 1L);
        long height = Math.max(0, maxY - minY + 1L);
        long depth = Math.max(0, maxZ - minZ + 1L);
        return width * height * depth;
    }
}
