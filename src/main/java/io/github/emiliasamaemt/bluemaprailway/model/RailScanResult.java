package io.github.emiliasamaemt.bluemaprailway.model;

import java.util.List;
import java.util.Map;

public record RailScanResult(
        Map<RailPosition, RailNode> nodes,
        List<RailLine> lines,
        int scannedChunks
) {

    public int railCount() {
        return nodes.size();
    }

    public int lineCount() {
        return lines.size();
    }
}
