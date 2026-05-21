package io.github.emiliasamaemt.bluemaprailway.model;

import java.util.List;

public record RailGraphResult(
        List<RailLine> lines,
        List<RailComponent> components,
        int hiddenLineCount
) {
}
