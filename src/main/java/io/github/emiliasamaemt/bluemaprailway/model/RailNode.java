package io.github.emiliasamaemt.bluemaprailway.model;

import org.bukkit.block.data.Rail;

import java.util.Set;

public record RailNode(
        RailPosition position,
        RailType type,
        Rail.Shape shape,
        boolean powered,
        Set<RailDirection> outgoingDirections
) {
}
