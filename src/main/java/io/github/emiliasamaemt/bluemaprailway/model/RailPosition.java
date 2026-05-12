package io.github.emiliasamaemt.bluemaprailway.model;

import com.flowpowered.math.vector.Vector3d;
import org.bukkit.World;

public record RailPosition(String worldName, int x, int y, int z) {

    public static RailPosition of(World world, int x, int y, int z) {
        return new RailPosition(world.getName(), x, y, z);
    }

    public RailPosition relative(int dx, int dy, int dz) {
        return new RailPosition(worldName, x + dx, y + dy, z + dz);
    }

    public Vector3d toBlueMapPoint(double yOffset) {
        return new Vector3d(x + 0.5, y + yOffset, z + 0.5);
    }
}
