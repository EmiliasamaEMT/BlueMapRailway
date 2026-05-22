package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.model.RailDirection;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class FabricRailBlockReader {

    public Optional<RailNode> read(String worldId, int x, int y, int z, BlockState state) {
        Optional<RailType> railType = railTypeFor(state.getBlock());
        if (railType.isEmpty()) {
            return Optional.empty();
        }

        RailShape shape = railShape(state);
        if (shape == null) {
            return Optional.empty();
        }

        boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
        return Optional.of(new RailNode(
                new RailPosition(worldId, x, y, z),
                railType.get(),
                shape.name(),
                powered,
                directionsFor(shape)
        ));
    }

    private Optional<RailType> railTypeFor(Block block) {
        if (block == Blocks.RAIL) {
            return Optional.of(RailType.RAIL);
        }
        if (block == Blocks.POWERED_RAIL) {
            return Optional.of(RailType.POWERED_RAIL);
        }
        if (block == Blocks.DETECTOR_RAIL) {
            return Optional.of(RailType.DETECTOR_RAIL);
        }
        if (block == Blocks.ACTIVATOR_RAIL) {
            return Optional.of(RailType.ACTIVATOR_RAIL);
        }
        return Optional.empty();
    }

    private RailShape railShape(BlockState state) {
        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE)) {
            return state.getValue(BlockStateProperties.RAIL_SHAPE);
        }
        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)) {
            return state.getValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT);
        }
        return null;
    }

    private Set<RailDirection> directionsFor(RailShape shape) {
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
