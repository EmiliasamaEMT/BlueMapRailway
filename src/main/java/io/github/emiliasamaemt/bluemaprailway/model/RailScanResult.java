package io.github.emiliasamaemt.bluemaprailway.model;

import java.util.List;
import java.util.Map;

public record RailScanResult(
        Map<RailPosition, RailNode> nodes,
        List<RailComponent> components,
        List<RailLine> lines,
        int scannedChunks,
        int hiddenLineCount
) {

    public int railCount() {
        return nodes.size();
    }

    public int lineCount() {
        return lines.size();
    }

    public int componentCount() {
        return components.size();
    }

    public int classifiedLineCount() {
        int count = 0;
        for (RailLine line : lines) {
            if (line.hasRoute()) {
                count++;
            }
        }

        return count;
    }
}
