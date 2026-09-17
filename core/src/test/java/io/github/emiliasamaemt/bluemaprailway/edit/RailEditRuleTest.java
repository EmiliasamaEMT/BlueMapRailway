package io.github.emiliasamaemt.bluemaprailway.edit;

import com.flowpowered.math.vector.Vector3d;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailEditRuleTest {

    @Test
    void maskNormalizesBoundsAndChecksWorld() {
        RailEditMask mask = new RailEditMask("m", "mask", "world", true, 10, 20, 30, 0, 0, 0);

        assertTrue(mask.contains(new Vector3d(5, 10, 15)));
        assertFalse(mask.contains(new Vector3d(12, 10, 15)));
        assertFalse(mask.appliesTo("other"));
    }

    @Test
    void hiddenRuleMatchesRouteOnlyWhenEnabled() {
        RailLine line = new RailLine(
                "component", "world", RailType.RAIL, false,
                List.of(new Vector3d(0, 64, 0), new Vector3d(1, 64, 0)),
                "main", "Main", "#ffffff", 3
        );

        assertTrue(new RailEditHideRule("h", "hide", true, Set.of("main"), Set.of()).hides(line));
        assertFalse(new RailEditHideRule("h", "hide", false, Set.of("main"), Set.of()).hides(line));
        assertTrue(new RailEditHideRule("h", "hide", true, Set.of(), Set.of("component")).hides(line));
    }

    @Test
    void processorClipsLinesPreservesMetadataAndFiltersComponents() {
        RailLine line = new RailLine(
                "component", "world", RailType.RAIL, false,
                List.of(
                        new Vector3d(-2, 64, 0),
                        new Vector3d(0, 64, 0),
                        new Vector3d(2, 64, 0)
                ),
                "main", "Main", "#ffffff", 3
        );
        RailComponent component = new RailComponent(
                "component", "world", List.of(), false, 4,
                -2, 64, 0, 2, 64, 0
        );
        RailScanResult input = new RailScanResult(
                Map.of(), List.of(component), List.of(line), 7, 5, 3, 2
        );
        RailEditMask mask = new RailEditMask("m", "mask", "world", true, -1, 0, -1, 0, 100, 1);

        RailScanResult output = RailEditProcessor.apply(input, List.of(mask), List.of());

        assertSame(input.nodes(), output.nodes());
        assertEquals(2, output.lines().size());
        assertEquals("main", output.lines().get(0).routeId());
        assertEquals(3, output.lines().get(0).routeLineWidth());
        assertEquals(List.of(component), output.components());
        assertEquals(2, output.hiddenLineCount());
        assertEquals(new Vector3d(-2, 64, 0), output.lines().get(0).points().get(0));
        assertEquals(new Vector3d(2, 64, 0), output.lines().get(1).points().get(1));
    }

    @Test
    void processorHidesMatchingLinesAndReturnsInputForEmptyRules() {
        RailLine line = new RailLine(
                "component", "world", RailType.RAIL, false,
                List.of(new Vector3d(0, 64, 0), new Vector3d(1, 64, 0)),
                "main", "Main", "#ffffff", 3
        );
        RailComponent component = new RailComponent(
                "component", "world", List.of(), false, 1,
                0, 64, 0, 1, 64, 0
        );
        RailScanResult input = new RailScanResult(
                Map.of(), List.of(component), List.of(line), 1, 1, 2, 0
        );

        RailScanResult hidden = RailEditProcessor.apply(
                input,
                List.of(),
                List.of(new RailEditHideRule("h", "hide", true, Set.of("main"), Set.of()))
        );

        assertTrue(hidden.lines().isEmpty());
        assertTrue(hidden.components().isEmpty());
        assertSame(input, RailEditProcessor.apply(input, List.of(), List.of()));
    }
}
