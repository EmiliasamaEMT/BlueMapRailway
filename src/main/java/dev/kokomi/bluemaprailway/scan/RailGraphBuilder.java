package dev.kokomi.bluemaprailway.scan;

import dev.kokomi.bluemaprailway.model.RailConnection;
import dev.kokomi.bluemaprailway.model.RailLine;
import dev.kokomi.bluemaprailway.model.RailNode;
import dev.kokomi.bluemaprailway.model.RailPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RailGraphBuilder {

    public List<RailLine> buildLines(Map<RailPosition, RailNode> nodes, double yOffset) {
        Map<RailPosition, Set<RailPosition>> adjacency = buildAdjacency(nodes);
        Set<RailConnection> visited = new HashSet<>();
        List<RailLine> lines = new ArrayList<>();

        List<RailPosition> starts = adjacency.keySet().stream()
                .filter(position -> adjacency.getOrDefault(position, Set.of()).size() != 2)
                .sorted(Comparator.comparing(RailPosition::worldName)
                        .thenComparingInt(RailPosition::x)
                        .thenComparingInt(RailPosition::y)
                        .thenComparingInt(RailPosition::z))
                .toList();

        for (RailPosition start : starts) {
            for (RailPosition next : adjacency.getOrDefault(start, Set.of())) {
                RailLine line = walkLine(start, next, nodes, adjacency, visited, yOffset);
                if (line != null) {
                    lines.add(line);
                }
            }
        }

        for (RailPosition start : adjacency.keySet()) {
            for (RailPosition next : adjacency.getOrDefault(start, Set.of())) {
                RailLine line = walkLine(start, next, nodes, adjacency, visited, yOffset);
                if (line != null) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    private Map<RailPosition, Set<RailPosition>> buildAdjacency(Map<RailPosition, RailNode> nodes) {
        Map<RailPosition, Set<RailPosition>> adjacency = new HashMap<>();

        for (RailNode node : nodes.values()) {
            for (var direction : node.outgoingDirections()) {
                RailPosition target = direction.apply(node.position());
                if (!nodes.containsKey(target)) {
                    continue;
                }

                adjacency.computeIfAbsent(node.position(), ignored -> new HashSet<>()).add(target);
                adjacency.computeIfAbsent(target, ignored -> new HashSet<>()).add(node.position());
            }
        }

        return adjacency;
    }

    private RailLine walkLine(
            RailPosition start,
            RailPosition next,
            Map<RailPosition, RailNode> nodes,
            Map<RailPosition, Set<RailPosition>> adjacency,
            Set<RailConnection> visited,
            double yOffset
    ) {
        RailConnection firstConnection = canonical(start, next);
        if (!visited.add(firstConnection)) {
            return null;
        }

        RailNode firstNode = nodes.get(start);
        if (firstNode == null) {
            return null;
        }

        List<RailPosition> positions = new ArrayList<>();
        positions.add(start);

        RailPosition previous = start;
        RailPosition current = next;

        while (true) {
            positions.add(current);

            RailNode currentNode = nodes.get(current);
            if (currentNode == null || !sameStyle(firstNode, currentNode)) {
                break;
            }

            Set<RailPosition> neighbors = adjacency.getOrDefault(current, Set.of());
            if (neighbors.size() != 2) {
                break;
            }

            RailPosition candidate = null;
            for (RailPosition neighbor : neighbors) {
                if (!neighbor.equals(previous)) {
                    candidate = neighbor;
                    break;
                }
            }

            if (candidate == null || !visited.add(canonical(current, candidate))) {
                break;
            }

            previous = current;
            current = candidate;
        }

        if (positions.size() < 2) {
            return null;
        }

        return new RailLine(
                firstNode.position().worldName(),
                firstNode.type(),
                firstNode.powered(),
                positions.stream()
                        .map(position -> position.toBlueMapPoint(yOffset))
                        .toList()
        );
    }

    private boolean sameStyle(RailNode first, RailNode second) {
        return first.type() == second.type() && first.powered() == second.powered();
    }

    private RailConnection canonical(RailPosition a, RailPosition b) {
        return compare(a, b) <= 0 ? new RailConnection(a, b) : new RailConnection(b, a);
    }

    private int compare(RailPosition a, RailPosition b) {
        return Comparator.comparing(RailPosition::worldName)
                .thenComparingInt(RailPosition::x)
                .thenComparingInt(RailPosition::y)
                .thenComparingInt(RailPosition::z)
                .compare(Objects.requireNonNull(a), Objects.requireNonNull(b));
    }
}
