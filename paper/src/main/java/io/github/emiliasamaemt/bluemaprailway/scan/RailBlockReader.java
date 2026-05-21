package io.github.emiliasamaemt.bluemaprailway.scan;

import io.github.emiliasamaemt.bluemaprailway.model.RailDirection;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Rail;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class RailBlockReader {

    public Optional<RailNode> read(World world, int x, int y, int z, Material material, BlockData blockData) {
        Optional<RailType> railType = railTypeFor(material);
        if (railType.isEmpty() || !(blockData instanceof Rail rail)) {
            return Optional.empty();
        }

        boolean powered = blockData instanceof Powerable powerable && powerable.isPowered();
        Rail.Shape shape = rail.getShape();
        Set<RailDirection> directions = directionsFor(shape);

        return Optional.of(new RailNode(
                new RailPosition(world.getName(), x, y, z),
                railType.get(),
                shape.name(),
                powered,
                directions
        ));
    }

    private Optional<RailType> railTypeFor(Material material) {
        return switch (material) {
            case RAIL -> Optional.of(RailType.RAIL);
            case POWERED_RAIL -> Optional.of(RailType.POWERED_RAIL);
            case DETECTOR_RAIL -> Optional.of(RailType.DETECTOR_RAIL);
            case ACTIVATOR_RAIL -> Optional.of(RailType.ACTIVATOR_RAIL);
            default -> Optional.empty();
        };
    }

    public static Set<RailDirection> directionsFor(Rail.Shape shape) {
        EnumSet<RailDirection> directions = EnumSet.noneOf(RailDirection.class);

        switch (shape) {
            case NORTH_SOUTH -> {
                directions.add(RailDirection.NORTH);
                directions.add(RailDirection.SOUTH);
            }
            case EAST_WEST -> {
                directions.add(RailDirection.EAST);
                directions.add(RailDirection.WEST);
            }
            case ASCENDING_EAST -> {
                directions.add(RailDirection.ASCENDING_EAST);
                directions.add(RailDirection.WEST);
            }
            case ASCENDING_WEST -> {
                directions.add(RailDirection.EAST);
                directions.add(RailDirection.ASCENDING_WEST);
            }
            case ASCENDING_NORTH -> {
                directions.add(RailDirection.ASCENDING_NORTH);
                directions.add(RailDirection.SOUTH);
            }
            case ASCENDING_SOUTH -> {
                directions.add(RailDirection.NORTH);
                directions.add(RailDirection.ASCENDING_SOUTH);
            }
            case SOUTH_EAST -> {
                directions.add(RailDirection.SOUTH);
                directions.add(RailDirection.EAST);
            }
            case SOUTH_WEST -> {
                directions.add(RailDirection.SOUTH);
                directions.add(RailDirection.WEST);
            }
            case NORTH_WEST -> {
                directions.add(RailDirection.NORTH);
                directions.add(RailDirection.WEST);
            }
            case NORTH_EAST -> {
                directions.add(RailDirection.NORTH);
                directions.add(RailDirection.EAST);
            }
        }

        return Set.copyOf(directions);
    }
}
