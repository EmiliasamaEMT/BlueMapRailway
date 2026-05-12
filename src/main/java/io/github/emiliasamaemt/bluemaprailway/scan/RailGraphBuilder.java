package io.github.emiliasamaemt.bluemaprailway.scan;

import io.github.emiliasamaemt.bluemaprailway.model.RailConnection;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;

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
        Map<RailPosition, ComponentInfo> components = indexComponents(nodes, adjacency);
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
                RailLine line = walkLine(start, next, nodes, adjacency, components, visited, yOffset, filter);
                if (line != null) {
                    lines.add(line);
                }
            }
        }

        for (RailPosition start : adjacency.keySet()) {
            for (RailPosition next : adjacency.getOrDefault(start, Set.of())) {
                RailLine line = walkLine(start, next, nodes, adjacency, components, visited, yOffset, filter);
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

    private Map<RailPosition, ComponentInfo> indexComponents(
            Map<RailPosition, RailNode> nodes,
            Map<RailPosition, Set<RailPosition>> adjacency
    ) {
        Map<RailPosition, ComponentInfo> result = new HashMap<>();
        Set<RailPosition> visited = new HashSet<>();

        for (RailPosition start : nodes.keySet()) {
            if (!visited.add(start)) {
                continue;
            }

            List<RailPosition> component = new ArrayList<>();
            ArrayDeque<RailPosition> queue = new ArrayDeque<>();
            boolean plainOnly = true;
            double length = 0;
            Set<RailConnection> componentEdges = new HashSet<>();

            queue.add(start);
            while (!queue.isEmpty()) {
                RailPosition current = queue.removeFirst();
                component.add(current);

                RailNode node = nodes.get(current);
                if (node == null || node.type() != RailType.RAIL) {
                    plainOnly = false;
                }

                for (RailPosition neighbor : adjacency.getOrDefault(current, Set.of())) {
                    RailConnection edge = canonical(current, neighbor);
                    if (componentEdges.add(edge)) {
                        length += distance(current, neighbor);
                    }

                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            ComponentInfo info = new ComponentInfo(plainOnly, component.size(), length);
            for (RailPosition position : component) {
                result.put(position, info);
            }
        }

        return result;
    }

    private RailLine walkLine(
            RailPosition start,
            RailPosition next,
            Map<RailPosition, RailNode> nodes,
            Map<RailPosition, Set<RailPosition>> adjacency,
            Map<RailPosition, ComponentInfo> components,
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

        if (shouldHideLine(firstNode, positions, components, filter)) {
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
            Map<RailPosition, ComponentInfo> components,
            RailLineFilter filter
    ) {
        ComponentInfo component = components.getOrDefault(firstNode.position(), ComponentInfo.EMPTY);
        double length = estimateLength(positions);
        if (isShortComponent(component, filter)) {
            return true;
        }

        if (!filter.hideFragmentedPlainRailBelowMinY()) {
            return false;
        }

        if (firstNode.type() != RailType.RAIL) {
            return false;
        }

        if (!component.plainRailOnly()) {
            return false;
        }

        boolean belowMinY = positions.stream().allMatch(position -> position.y() < filter.minY());
        if (!belowMinY) {
            return false;
        }

        return positions.size() <= filter.fragmentedLineMaxPoints() ||
                length <= filter.fragmentedLineMaxLength();
    }

    private boolean isShortComponent(ComponentInfo component, RailLineFilter filter) {
        if (!filter.hideShortLines()) {
            return false;
        }

        return component.pointCount() <= filter.shortLineMaxPoints() ||
                component.length() <= filter.shortLineMaxLength();
    }

    private double estimateLength(List<RailPosition> positions) {
        double length = 0;

        for (int i = 1; i < positions.size(); i++) {
            length += distance(positions.get(i - 1), positions.get(i));
        }

        return length;
    }

    private double distance(RailPosition a, RailPosition b) {
        int dx = b.x() - a.x();
        int dy = b.y() - a.y();
        int dz = b.z() - a.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    private record ComponentInfo(boolean plainRailOnly, int pointCount, double length) {
        private static final ComponentInfo EMPTY = new ComponentInfo(false, 0, 0);
    }
}
