package io.github.emiliasamaemt.bluemaprailway.scan;

import io.github.emiliasamaemt.bluemaprailway.model.RailDirection;
import io.github.emiliasamaemt.bluemaprailway.model.RailGraphResult;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RailGraphBuilderTest {

    private static final RailLineFilter NO_FILTER = new RailLineFilter(false, 0, 0, false, 0, 0, 0);

    @Test
    void straightRailsBecomeOneComponentAndOneLine() {
        Map<RailPosition, RailNode> nodes = new LinkedHashMap<>();
        put(nodes, 0, 0, RailDirection.EAST);
        put(nodes, 1, 0, RailDirection.EAST, RailDirection.WEST);
        put(nodes, 2, 0, RailDirection.WEST);

        RailGraphResult result = new RailGraphBuilder().build(nodes, 0.35, NO_FILTER);

        assertEquals(1, result.components().size());
        assertEquals(1, result.lines().size());
        assertEquals(3, result.lines().getFirst().points().size());
        assertEquals(0.5, result.lines().getFirst().points().getFirst().getX());
        assertEquals(2.5, result.lines().getFirst().points().getLast().getX());
    }

    @Test
    void branchCreatesSeparateLinesAtTheJunction() {
        Map<RailPosition, RailNode> nodes = new LinkedHashMap<>();
        put(nodes, 0, 0, RailDirection.EAST, RailDirection.WEST, RailDirection.NORTH);
        put(nodes, 1, 0, RailDirection.WEST);
        put(nodes, -1, 0, RailDirection.EAST);
        put(nodes, 0, -1, RailDirection.SOUTH);

        RailGraphResult result = new RailGraphBuilder().build(nodes, 0.35, NO_FILTER);

        assertEquals(1, result.components().size());
        assertEquals(3, result.lines().size());
    }

    private static void put(Map<RailPosition, RailNode> nodes, int x, int z, RailDirection... directions) {
        RailPosition position = new RailPosition("world", x, 64, z);
        nodes.put(position, new RailNode(position, RailType.RAIL, "flat", false,
                EnumSet.copyOf(java.util.List.of(directions))));
    }
}
