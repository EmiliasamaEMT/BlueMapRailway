package io.github.emiliasamaemt.bluemaprailway.model;

import java.util.Set;

public record RailNode(
        RailPosition position,
        RailType type,
        String shapeName,
        boolean powered,
        Set<RailDirection> outgoingDirections
) {
}
