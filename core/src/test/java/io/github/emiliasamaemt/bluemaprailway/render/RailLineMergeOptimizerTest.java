package io.github.emiliasamaemt.bluemaprailway.render;

import com.flowpowered.math.vector.Vector3d;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RailLineMergeOptimizerTest {

    @Test
    void mergesAdjacentLinesWithTheSameStyle() {
        List<RailLine> lines = List.of(
                line(List.of(point(0), point(1))),
                line(List.of(point(1), point(2)))
        );

        List<RailLine> merged = RailLineMergeOptimizer.merge(lines, ignored -> "rail");

        assertEquals(1, merged.size());
        assertEquals(3, merged.getFirst().points().size());
        assertEquals(2.0, merged.getFirst().points().getLast().getX());
    }

    @Test
    void doesNotMergeAcrossAThreeWayEndpoint() {
        List<RailLine> lines = List.of(
                line(List.of(point(0), point(1))),
                line(List.of(point(1), point(2))),
                line(List.of(point(1), point(1, 1)))
        );

        List<RailLine> merged = RailLineMergeOptimizer.merge(lines, ignored -> "rail");

        assertEquals(3, merged.size());
    }

    private static RailLine line(List<Vector3d> points) {
        return new RailLine("world:component:test", "world", RailType.RAIL, false, points);
    }

    private static Vector3d point(double x) {
        return new Vector3d(x, 64, 0);
    }

    private static Vector3d point(double x, double z) {
        return new Vector3d(x, 64, z);
    }
}
