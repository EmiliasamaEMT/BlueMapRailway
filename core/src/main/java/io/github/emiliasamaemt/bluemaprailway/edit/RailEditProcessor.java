package io.github.emiliasamaemt.bluemaprailway.edit;

import com.flowpowered.math.vector.Vector3d;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies persisted edit rules to a scan result without platform or file side effects.
 */
public final class RailEditProcessor {

    private static final double EPSILON = 1.0E-6;

    private RailEditProcessor() {
    }

    public static RailScanResult apply(
            RailScanResult result,
            List<RailEditMask> masks,
            List<RailEditHideRule> hiddenLines
    ) {
        if ((masks.isEmpty() && hiddenLines.isEmpty()) || result == null || result.lines().isEmpty()) {
            return result;
        }

        List<RailLine> lines = new ArrayList<>();
        Set<String> visibleComponentIds = new LinkedHashSet<>();
        for (RailLine line : result.lines()) {
            if (isHidden(line, hiddenLines)) {
                continue;
            }
            List<RailLine> visibleLines = applyMasks(line, masks);
            lines.addAll(visibleLines);
            for (RailLine visibleLine : visibleLines) {
                visibleComponentIds.add(visibleLine.componentId());
            }
        }

        List<RailComponent> components = result.components().stream()
                .filter(component -> visibleComponentIds.contains(component.id()))
                .toList();

        return new RailScanResult(
                result.nodes(),
                components,
                List.copyOf(lines),
                result.scannedChunks(),
                result.cachedChunks(),
                result.cachedRails(),
                result.hiddenLineCount()
        );
    }

    private static boolean isHidden(RailLine line, List<RailEditHideRule> hiddenLines) {
        for (RailEditHideRule hiddenLine : hiddenLines) {
            if (hiddenLine.hides(line)) {
                return true;
            }
        }
        return false;
    }

    private static List<RailLine> applyMasks(RailLine line, List<RailEditMask> masks) {
        List<RailLine> current = List.of(line);
        for (RailEditMask mask : masks) {
            if (!mask.enabled() || !mask.appliesTo(line.worldName())) {
                continue;
            }

            List<RailLine> next = new ArrayList<>();
            for (RailLine candidate : current) {
                next.addAll(applyMask(candidate, mask));
            }
            current = List.copyOf(next);
            if (current.isEmpty()) {
                break;
            }
        }
        return current;
    }

    private static List<RailLine> applyMask(RailLine line, RailEditMask mask) {
        List<List<Vector3d>> segments = clipOutsideMask(line.points(), mask);
        if (segments.isEmpty()) {
            return List.of();
        }

        List<RailLine> result = new ArrayList<>();
        for (List<Vector3d> segment : segments) {
            result.add(line.withPoints(segment));
        }
        return result;
    }

    private static List<List<Vector3d>> clipOutsideMask(List<Vector3d> points, RailEditMask mask) {
        if (points.size() < 2) {
            return List.of();
        }

        List<List<Vector3d>> result = new ArrayList<>();
        List<Vector3d> current = new ArrayList<>();

        for (int i = 1; i < points.size(); i++) {
            Vector3d a = points.get(i - 1);
            Vector3d b = points.get(i);
            List<SegmentPiece> pieces = visiblePieces(a, b, mask);

            if (pieces.isEmpty()) {
                flush(result, current);
                continue;
            }

            for (int pieceIndex = 0; pieceIndex < pieces.size(); pieceIndex++) {
                SegmentPiece piece = pieces.get(pieceIndex);
                if (current.isEmpty()) {
                    current.add(piece.start());
                } else if (!samePoint(current.get(current.size() - 1), piece.start())) {
                    flush(result, current);
                    current.add(piece.start());
                }

                appendIfNeeded(current, piece.end());
                if (pieceIndex < pieces.size() - 1) {
                    flush(result, current);
                }
            }
        }

        flush(result, current);
        return List.copyOf(result);
    }

    private static List<SegmentPiece> visiblePieces(Vector3d a, Vector3d b, RailEditMask mask) {
        boolean aInside = mask.contains(a);
        boolean bInside = mask.contains(b);
        Interval inside = insideInterval(a, b, mask);

        if (inside == null || inside.end() - inside.start() <= EPSILON) {
            return aInside && bInside ? List.of() : List.of(new SegmentPiece(a, b));
        }

        List<SegmentPiece> pieces = new ArrayList<>();
        if (inside.start() > EPSILON) {
            pieces.add(new SegmentPiece(a, lerp(a, b, inside.start())));
        }
        if (inside.end() < 1.0 - EPSILON) {
            pieces.add(new SegmentPiece(lerp(a, b, inside.end()), b));
        }
        return pieces;
    }

    private static Interval insideInterval(Vector3d a, Vector3d b, RailEditMask mask) {
        double[] start = {a.getX(), a.getY(), a.getZ()};
        double[] delta = {b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ()};
        double[] min = {mask.minX(), mask.minY(), mask.minZ()};
        double[] max = {mask.maxX() + 1.0, mask.maxY() + 1.0, mask.maxZ() + 1.0};

        double t0 = 0.0;
        double t1 = 1.0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(delta[i]) <= EPSILON) {
                if (start[i] < min[i] || start[i] > max[i]) {
                    return null;
                }
                continue;
            }

            double near = (min[i] - start[i]) / delta[i];
            double far = (max[i] - start[i]) / delta[i];
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }

            t0 = Math.max(t0, near);
            t1 = Math.min(t1, far);
            if (t0 > t1) {
                return null;
            }
        }

        return new Interval(Math.max(0.0, t0), Math.min(1.0, t1));
    }

    private static Vector3d lerp(Vector3d a, Vector3d b, double t) {
        return new Vector3d(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    private static void appendIfNeeded(List<Vector3d> current, Vector3d point) {
        if (current.isEmpty() || !samePoint(current.get(current.size() - 1), point)) {
            current.add(point);
        }
    }

    private static boolean samePoint(Vector3d a, Vector3d b) {
        return a.distanceSquared(b) <= EPSILON * EPSILON;
    }

    private static void flush(List<List<Vector3d>> result, List<Vector3d> current) {
        if (current.size() >= 2) {
            result.add(List.copyOf(current));
        }
        current.clear();
    }

    private record Interval(double start, double end) {
    }

    private record SegmentPiece(Vector3d start, Vector3d end) {
    }
}
