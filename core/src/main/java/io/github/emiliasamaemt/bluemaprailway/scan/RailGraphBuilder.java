package io.github.emiliasamaemt.bluemaprailway.scan;

import io.github.emiliasamaemt.bluemaprailway.model.RailConnection;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailDirection;
import io.github.emiliasamaemt.bluemaprailway.model.RailGraphResult;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RailGraphBuilder {

    public RailGraphResult build(Map<RailPosition, RailNode> nodes, double yOffset, RailLineFilter filter) {
        Map<RailPosition, Set<RailPosition>> adjacency = buildAdjacency(nodes);
        ComponentIndex componentIndex = indexComponents(nodes, adjacency);
        Set<RailConnection> visited = new HashSet<>();
        List<RailLine> lines = new ArrayList<>();
        int hiddenLineCount = 0;

        List<RailPosition> starts = adjacency.keySet().stream()
                .filter(position -> adjacency.getOrDefault(position, Set.of()).size() != 2)
                .sorted(Comparator.comparing(RailPosition::worldName)
                        .thenComparingInt(RailPosition::x)
                        .thenComparingInt(RailPosition::y)
                        .thenComparingInt(RailPosition::z))
                .toList();

        for (RailPosition start : starts) {
            for (RailPosition next : adjacency.getOrDefault(start, Set.of())) {
                LineBuildResult line = walkLine(start, next, nodes, adjacency, componentIndex.byPosition(), visited, yOffset, filter);
                if (line != null) {
                    if (line.hidden()) {
                        hiddenLineCount++;
                    } else {
                        lines.add(line.line());
                    }
                }
            }
        }

        for (RailPosition start : adjacency.keySet()) {
            for (RailPosition next : adjacency.getOrDefault(start, Set.of())) {
                LineBuildResult line = walkLine(start, next, nodes, adjacency, componentIndex.byPosition(), visited, yOffset, filter);
                if (line != null) {
                    if (line.hidden()) {
                        hiddenLineCount++;
                    } else {
                        lines.add(line.line());
                    }
                }
            }
        }

        return new RailGraphResult(lines, componentIndex.components(), hiddenLineCount);
    }

    private Map<RailPosition, Set<RailPosition>> buildAdjacency(Map<RailPosition, RailNode> nodes) {
        Map<RailPosition, Set<RailPosition>> adjacency = new HashMap<>();

        for (RailNode node : nodes.values()) {
            for (var direction : node.outgoingDirections()) {
                RailPosition target = direction.apply(node.position());
                if (!nodes.containsKey(target)) {
                    continue;
                }

                RailNode targetNode = nodes.get(target);
                if (!connectsBack(node, targetNode)) {
                    continue;
                }

                adjacency.computeIfAbsent(node.position(), ignored -> new HashSet<>()).add(target);
                adjacency.computeIfAbsent(target, ignored -> new HashSet<>()).add(node.position());
            }
        }

        return adjacency;
    }

    private boolean connectsBack(RailNode source, RailNode target) {
        int dx = source.position().x() - target.position().x();
        int dy = source.position().y() - target.position().y();
        int dz = source.position().z() - target.position().z();

        for (RailDirection direction : target.outgoingDirections()) {
            if (direction.apply(target.position()).equals(source.position())) {
                return true;
            }

            if (direction.dx() == dx && direction.dz() == dz && direction.dy() == 0 && dy == -1) {
                return true;
            }
        }

        return false;
    }

    private ComponentIndex indexComponents(
            Map<RailPosition, RailNode> nodes,
            Map<RailPosition, Set<RailPosition>> adjacency
    ) {
        Map<RailPosition, ComponentInfo> result = new HashMap<>();
        List<RailComponent> components = new ArrayList<>();
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

            RailComponent railComponent = createComponent(component, plainOnly, length);
            components.add(railComponent);
            ComponentInfo info = new ComponentInfo(railComponent);
            for (RailPosition position : component) {
                result.put(position, info);
            }
        }

        components.sort(Comparator.comparing(RailComponent::worldName).thenComparing(RailComponent::id));
        return new ComponentIndex(result, components);
    }

    private RailComponent createComponent(List<RailPosition> positions, boolean plainOnly, double length) {
        List<RailPosition> sorted = positions.stream()
                .sorted(this::compare)
                .toList();

        int minX = sorted.stream().mapToInt(RailPosition::x).min().orElse(0);
        int minY = sorted.stream().mapToInt(RailPosition::y).min().orElse(0);
        int minZ = sorted.stream().mapToInt(RailPosition::z).min().orElse(0);
        int maxX = sorted.stream().mapToInt(RailPosition::x).max().orElse(0);
        int maxY = sorted.stream().mapToInt(RailPosition::y).max().orElse(0);
        int maxZ = sorted.stream().mapToInt(RailPosition::z).max().orElse(0);

        String id = componentId(sorted, length, minX, minY, minZ, maxX, maxY, maxZ);
        String worldName = sorted.isEmpty() ? "unknown" : sorted.getFirst().worldName();
        return new RailComponent(id, worldName, sorted, plainOnly, length, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private String componentId(
            List<RailPosition> positions,
            double length,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        String worldName = positions.isEmpty() ? "unknown" : positions.getFirst().worldName();
        MessageDigest digest = sha1();
        update(digest, worldName);
        update(digest, Integer.toString(positions.size()));
        update(digest, Long.toString(Math.round(length * 100)));
        update(digest, minX + "," + minY + "," + minZ + ":" + maxX + "," + maxY + "," + maxZ);

        int sampleCount = Math.min(24, positions.size());
        for (int i = 0; i < sampleCount; i++) {
            int index = sampleCount == 1 ? 0 : (int) Math.round((positions.size() - 1) * (i / (double) (sampleCount - 1)));
            RailPosition position = positions.get(index);
            update(digest, position.x() + "," + position.y() + "," + position.z());
        }

        String hash = HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        return worldName + ":component:" + hash;
    }

    private MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 digest is not available", exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private LineBuildResult walkLine(
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
            return new LineBuildResult(null, true);
        }

        return new LineBuildResult(new RailLine(
                components.getOrDefault(firstNode.position(), ComponentInfo.EMPTY).component().id(),
                firstNode.position().worldName(),
                firstNode.type(),
                firstNode.powered(),
                positions.stream()
                        .map(position -> position.toBlueMapPoint(yOffset))
                        .toList()
        ), false);
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

        if (!component.component().plainRailOnly()) {
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

        return component.component().pointCount() <= filter.shortLineMaxPoints() ||
                component.component().length() <= filter.shortLineMaxLength();
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

    private record ComponentIndex(Map<RailPosition, ComponentInfo> byPosition, List<RailComponent> components) {
    }

    private record ComponentInfo(RailComponent component) {
        private static final ComponentInfo EMPTY = new ComponentInfo(
                new RailComponent("unknown:component:none", "unknown", List.of(), false, 0, 0, 0, 0, 0, 0, 0)
        );
    }

    private record LineBuildResult(RailLine line, boolean hidden) {
    }
}
