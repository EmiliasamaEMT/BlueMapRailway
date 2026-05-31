package io.github.emiliasamaemt.bluemaprailway.render;

import com.flowpowered.math.vector.Vector3d;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class RailLineMergeOptimizer {

    private RailLineMergeOptimizer() {
    }

    public static List<RailLine> merge(List<RailLine> lines, Function<RailLine, String> styleKeyFactory) {
        if (lines.size() < 2) {
            return lines;
        }

        Map<EndpointKey, Integer> globalDegrees = globalDegrees(lines);
        Map<GroupKey, List<RailLine>> groups = new LinkedHashMap<>();
        for (RailLine line : lines) {
            groups.computeIfAbsent(GroupKey.from(line, styleKeyFactory.apply(line)), ignored -> new ArrayList<>())
                    .add(line);
        }

        List<RailLine> merged = new ArrayList<>(lines.size());
        for (List<RailLine> groupLines : groups.values()) {
            merged.addAll(mergeGroup(groupLines, globalDegrees));
        }
        return List.copyOf(merged);
    }

    private static Map<EndpointKey, Integer> globalDegrees(List<RailLine> lines) {
        Map<EndpointKey, Integer> degrees = new LinkedHashMap<>();
        for (RailLine line : lines) {
            if (line.points().size() < 2) {
                continue;
            }

            EndpointKey start = EndpointKey.of(line.points().getFirst());
            EndpointKey end = EndpointKey.of(line.points().getLast());
            degrees.merge(start, 1, Integer::sum);
            degrees.merge(end, 1, Integer::sum);
        }
        return degrees;
    }

    private static List<RailLine> mergeGroup(List<RailLine> lines, Map<EndpointKey, Integer> globalDegrees) {
        if (lines.size() < 2) {
            return lines;
        }

        List<IndexedLine> indexed = new ArrayList<>(lines.size());
        Map<EndpointKey, List<Integer>> incident = new LinkedHashMap<>();
        Map<Integer, IndexedLine> byIndex = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            RailLine line = lines.get(index);
            if (line.points().size() < 2) {
                continue;
            }

            EndpointKey start = EndpointKey.of(line.points().getFirst());
            EndpointKey end = EndpointKey.of(line.points().getLast());
            IndexedLine indexedLine = new IndexedLine(index, line, start, end);
            indexed.add(indexedLine);
            byIndex.put(index, indexedLine);
            incident.computeIfAbsent(start, ignored -> new ArrayList<>()).add(index);
            incident.computeIfAbsent(end, ignored -> new ArrayList<>()).add(index);
        }

        if (indexed.size() < 2) {
            return lines;
        }

        boolean[] used = new boolean[lines.size()];
        List<RailLine> merged = new ArrayList<>(lines.size());

        for (IndexedLine line : indexed) {
            if (used[line.index()]) {
                continue;
            }

            int startGroupDegree = degree(incident, line.start());
            int endGroupDegree = degree(incident, line.end());
            int startGlobalDegree = degree(globalDegrees, line.start());
            int endGlobalDegree = degree(globalDegrees, line.end());
            if (startGlobalDegree == 2 && endGlobalDegree == 2) {
                continue;
            }

            EndpointKey startEndpoint = preferredStartEndpoint(
                    line,
                    startGroupDegree,
                    endGroupDegree,
                    startGlobalDegree,
                    endGlobalDegree
            );
            merged.add(buildMergedLine(line, startEndpoint, byIndex, incident, globalDegrees, used));
        }

        for (IndexedLine line : indexed) {
            if (used[line.index()]) {
                continue;
            }
            merged.add(buildMergedLine(line, line.start(), byIndex, incident, globalDegrees, used));
        }

        for (RailLine line : lines) {
            if (line.points().size() < 2) {
                merged.add(line);
            }
        }

        return List.copyOf(merged);
    }

    private static RailLine buildMergedLine(
            IndexedLine first,
            EndpointKey startEndpoint,
            Map<Integer, IndexedLine> byIndex,
            Map<EndpointKey, List<Integer>> incident,
            Map<EndpointKey, Integer> globalDegrees,
            boolean[] used
    ) {
        List<Vector3d> points = new ArrayList<>();
        append(points, first, startEndpoint, false);
        used[first.index()] = true;

        EndpointKey tail = first.otherEndpoint(startEndpoint);
        while (degree(incident, tail) == 2 && degree(globalDegrees, tail) == 2) {
            IndexedLine next = nextUnused(byIndex, incident.getOrDefault(tail, List.of()), used, first.index());
            if (next == null || !next.touches(tail)) {
                break;
            }

            append(points, next, tail, true);
            used[next.index()] = true;
            tail = next.otherEndpoint(tail);
        }

        if (points.size() < 2) {
            return first.line();
        }
        return first.line().withPoints(List.copyOf(points));
    }

    private static void append(List<Vector3d> target, IndexedLine line, EndpointKey startEndpoint, boolean skipFirst) {
        List<Vector3d> points = line.orientedPoints(startEndpoint);
        for (int index = 0; index < points.size(); index++) {
            if (skipFirst && index == 0) {
                continue;
            }
            target.add(points.get(index));
        }
    }

    private static IndexedLine nextUnused(
            Map<Integer, IndexedLine> byIndex,
            List<Integer> candidates,
            boolean[] used,
            int currentIndex
    ) {
        for (Integer candidate : candidates) {
            if (candidate == null || candidate == currentIndex || used[candidate]) {
                continue;
            }
            IndexedLine line = byIndex.get(candidate);
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static EndpointKey preferredStartEndpoint(
            IndexedLine line,
            int startGroupDegree,
            int endGroupDegree,
            int startGlobalDegree,
            int endGlobalDegree
    ) {
        if (startGlobalDegree != 2 && endGlobalDegree == 2) {
            return line.start();
        }
        if (endGlobalDegree != 2 && startGlobalDegree == 2) {
            return line.end();
        }
        if (startGroupDegree == 1 && endGroupDegree != 1) {
            return line.start();
        }
        if (endGroupDegree == 1 && startGroupDegree != 1) {
            return line.end();
        }
        return line.start();
    }

    private static int degree(Map<EndpointKey, ?> incident, EndpointKey endpoint) {
        Object value = incident.get(endpoint);
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        return 0;
    }

    private record GroupKey(
            String componentId,
            String worldName,
            String routeId,
            String routeName,
            String routeColor,
            int routeLineWidth,
            String styleKey
    ) {
        private static GroupKey from(RailLine line, String styleKey) {
            return new GroupKey(
                    line.componentId(),
                    line.worldName(),
                    line.routeId(),
                    line.routeName(),
                    line.routeColor(),
                    line.routeLineWidth(),
                    styleKey == null ? "" : styleKey
            );
        }
    }

    private record IndexedLine(int index, RailLine line, EndpointKey start, EndpointKey end) {
        private boolean touches(EndpointKey endpoint) {
            return start.equals(endpoint) || end.equals(endpoint);
        }

        private EndpointKey otherEndpoint(EndpointKey endpoint) {
            return start.equals(endpoint) ? end : start;
        }

        private List<Vector3d> orientedPoints(EndpointKey startEndpoint) {
            if (start.equals(startEndpoint)) {
                return line.points();
            }

            List<Vector3d> reversed = new ArrayList<>(line.points());
            java.util.Collections.reverse(reversed);
            return reversed;
        }
    }

    private record EndpointKey(double x, double y, double z) {
        private static EndpointKey of(Vector3d point) {
            return new EndpointKey(point.getX(), point.getY(), point.getZ());
        }
    }
}
