package dev.kokomi.bluemaprailway.scan;

import dev.kokomi.bluemaprailway.model.RailConnection;
import dev.kokomi.bluemaprailway.model.RailLine;
import dev.kokomi.bluemaprailway.model.RailNode;
import dev.kokomi.bluemaprailway.model.RailPosition;
import dev.kokomi.bluemaprailway.model.RailType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RailGraphBuilder {

    public List<RailLine> buildLines(Map<RailPosition, RailNode> nodes, double yOffset, RailLineFilter filter) {
        Map<RailPosition, Set<RailPosition>> adjacency = buildAdjacency(nodes);
        Map<RailPosition, Boolean> plainRailOnlyComponents = indexPlainRailOnlyComponents(nodes, adjacency);
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
                RailLine line = walkLine(start, next, nodes, adjacency, plainRailOnlyComponents, visited, yOffset, filter);
                if (line != null) {
                    lines.add(line);
                }
            }
        }

        for (RailPosition start : adjacency.keySet()) {
            for (RailPosition next : adjacency.getOrDefault(start, Set.of())) {
                RailLine line = walkLine(start, next, nodes, adjacency, plainRailOnlyComponents, visited, yOffset, filter);
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

    private Map<RailPosition, Boolean> indexPlainRailOnlyComponents(
            Map<RailPosition, RailNode> nodes,
            Map<RailPosition, Set<RailPosition>> adjacency
    ) {
        Map<RailPosition, Boolean> result = new HashMap<>();
        Set<RailPosition> visited = new HashSet<>();

        for (RailPosition start : nodes.keySet()) {
            if (!visited.add(start)) {
                continue;
            }

            List<RailPosition> component = new ArrayList<>();
            ArrayDeque<RailPosition> queue = new ArrayDeque<>();
            boolean plainOnly = true;

            queue.add(start);
            while (!queue.isEmpty()) {
                RailPosition current = queue.removeFirst();
                component.add(current);

                RailNode node = nodes.get(current);
                if (node == null || node.type() != RailType.RAIL) {
                    plainOnly = false;
                }

                for (RailPosition neighbor : adjacency.getOrDefault(current, Set.of())) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            for (RailPosition position : component) {
                result.put(position, plainOnly);
            }
        }

        return result;
    }

    private RailLine walkLine(
            RailPosition start,
            RailPosition next,
            Map<RailPosition, RailNode> nodes,
            Map<RailPosition, Set<RailPosition>> adjacency,
            Map<RailPosition, Boolean> plainRailOnlyComponents,
            Set<RailConnection> visited,
            double yOffset,
            RailLineFilter filter
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

        if (shouldHideLine(firstNode, positions, plainRailOnlyComponents, filter)) {
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

    private boolean shouldHideLine(
            RailNode firstNode,
            List<RailPosition> positions,
            Map<RailPosition, Boolean> plainRailOnlyComponents,
            RailLineFilter filter
    ) {
        if (!filter.hideFragmentedPlainRailBelowMinY()) {
            return false;
        }

        if (firstNode.type() != RailType.RAIL) {
            return false;
        }

        if (!plainRailOnlyComponents.getOrDefault(firstNode.position(), false)) {
            return false;
        }

        boolean belowMinY = positions.stream().allMatch(position -> position.y() < filter.minY());
        if (!belowMinY) {
            return false;
        }

        return positions.size() <= filter.fragmentedLineMaxPoints() ||
                estimateLength(positions) <= filter.fragmentedLineMaxLength();
    }

    private double estimateLength(List<RailPosition> positions) {
        double length = 0;

        for (int i = 1; i < positions.size(); i++) {
            RailPosition previous = positions.get(i - 1);
            RailPosition current = positions.get(i);
            int dx = current.x() - previous.x();
            int dy = current.y() - previous.y();
            int dz = current.z() - previous.z();
            length += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        return length;
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
