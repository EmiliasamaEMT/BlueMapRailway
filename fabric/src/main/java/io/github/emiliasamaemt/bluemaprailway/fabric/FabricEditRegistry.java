package io.github.emiliasamaemt.bluemaprailway.fabric;

import com.flowpowered.math.vector.Vector3d;
import io.github.emiliasamaemt.bluemaprailway.edit.RailEditHideRule;
import io.github.emiliasamaemt.bluemaprailway.edit.RailEditMask;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FabricEditRegistry {

    private static final double EPSILON = 1.0E-6;
    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

    private final List<RailEditMask> masks;
    private final List<RailEditHideRule> hiddenLines;

    private FabricEditRegistry(List<RailEditMask> masks, List<RailEditHideRule> hiddenLines) {
        this.masks = List.copyOf(masks);
        this.hiddenLines = List.copyOf(hiddenLines);
    }

    public static FabricEditRegistry load(FabricRailwayLogger log) {
        ensureDefaultFile(log);

        Path file = editsFile();
        if (!Files.exists(file)) {
            return new FabricEditRegistry(List.of(), List.of());
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return new FabricEditRegistry(List.of(), List.of());
            }

            Map<String, Object> root = castMap(map);
            List<RailEditMask> masks = readMasks(root.get("masks"));
            List<RailEditHideRule> hiddenLines = readHiddenLines(root.get("hidden-lines"));
            return new FabricEditRegistry(masks, hiddenLines);
        } catch (IOException exception) {
            log.warning("Failed to read edits.yml, using empty edits: " + exception.getMessage());
            return new FabricEditRegistry(List.of(), List.of());
        }
    }

    public RailScanResult apply(RailScanResult result) {
        if ((masks.isEmpty() && hiddenLines.isEmpty()) || result == null || result.lines().isEmpty()) {
            return result;
        }

        List<RailLine> lines = new ArrayList<>();
        Set<String> visibleComponentIds = new LinkedHashSet<>();
        for (RailLine line : result.lines()) {
            if (isHidden(line)) {
                continue;
            }
            List<RailLine> visibleLines = applyMasks(line);
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

    public List<RailEditMask> masks() {
        return masks;
    }

    public List<RailEditHideRule> hiddenLines() {
        return hiddenLines;
    }

    public int maskCount() {
        return masks.size();
    }

    public int hiddenLineCount() {
        return hiddenLines.size();
    }

    public RailEditMask mask(String maskId) {
        for (RailEditMask mask : masks) {
            if (mask.id().equals(maskId)) {
                return mask;
            }
        }
        return null;
    }

    public RailEditHideRule hiddenLine(String ruleId) {
        for (RailEditHideRule hiddenLine : hiddenLines) {
            if (hiddenLine.id().equals(ruleId)) {
                return hiddenLine;
            }
        }
        return null;
    }

    public void saveMask(RailEditMask mask, FabricRailwayLogger log) {
        List<RailEditMask> updatedMasks = new ArrayList<>();
        boolean replaced = false;
        for (RailEditMask existing : masks) {
            if (existing.id().equals(mask.id())) {
                updatedMasks.add(mask);
                replaced = true;
            } else {
                updatedMasks.add(existing);
            }
        }
        if (!replaced) {
            updatedMasks.add(mask);
        }
        writeEdits(updatedMasks, hiddenLines);
    }

    public boolean deleteMask(String maskId, FabricRailwayLogger log) {
        List<RailEditMask> updatedMasks = masks.stream()
                .filter(mask -> !mask.id().equals(maskId))
                .toList();
        if (updatedMasks.size() == masks.size()) {
            return false;
        }
        writeEdits(updatedMasks, hiddenLines);
        return true;
    }

    public void saveHiddenLine(RailEditHideRule rule, FabricRailwayLogger log) {
        List<RailEditHideRule> updatedRules = new ArrayList<>();
        boolean replaced = false;
        for (RailEditHideRule existing : hiddenLines) {
            if (existing.id().equals(rule.id())) {
                updatedRules.add(rule);
                replaced = true;
            } else {
                updatedRules.add(existing);
            }
        }
        if (!replaced) {
            updatedRules.add(rule);
        }
        writeEdits(masks, updatedRules);
    }

    public boolean deleteHiddenLine(String ruleId, FabricRailwayLogger log) {
        List<RailEditHideRule> updatedRules = hiddenLines.stream()
                .filter(hiddenLine -> !hiddenLine.id().equals(ruleId))
                .toList();
        if (updatedRules.size() == hiddenLines.size()) {
            return false;
        }
        writeEdits(masks, updatedRules);
        return true;
    }

    private boolean isHidden(RailLine line) {
        for (RailEditHideRule hiddenLine : hiddenLines) {
            if (hiddenLine.hides(line)) {
                return true;
            }
        }
        return false;
    }

    private List<RailLine> applyMasks(RailLine line) {
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

    private List<RailLine> applyMask(RailLine line, RailEditMask mask) {
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

    private List<List<Vector3d>> clipOutsideMask(List<Vector3d> points, RailEditMask mask) {
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

    private List<SegmentPiece> visiblePieces(Vector3d a, Vector3d b, RailEditMask mask) {
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

    private Interval insideInterval(Vector3d a, Vector3d b, RailEditMask mask) {
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

    private Vector3d lerp(Vector3d a, Vector3d b, double t) {
        return new Vector3d(
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    private void appendIfNeeded(List<Vector3d> current, Vector3d point) {
        if (current.isEmpty() || !samePoint(current.get(current.size() - 1), point)) {
            current.add(point);
        }
    }

    private boolean samePoint(Vector3d a, Vector3d b) {
        return a.distanceSquared(b) <= EPSILON * EPSILON;
    }

    private void flush(List<List<Vector3d>> result, List<Vector3d> current) {
        if (current.size() >= 2) {
            result.add(List.copyOf(current));
        }
        current.clear();
    }

    private static List<RailEditMask> readMasks(Object value) {
        if (!(value instanceof Map<?, ?> maskMap)) {
            return List.of();
        }

        List<RailEditMask> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : castMap(maskMap).entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                continue;
            }

            RailEditMask mask = readMask(entry.getKey(), castMap(data));
            if (mask != null) {
                result.add(mask);
            }
        }
        return List.copyOf(result);
    }

    private static List<RailEditHideRule> readHiddenLines(Object value) {
        if (!(value instanceof Map<?, ?> hiddenMap)) {
            return List.of();
        }

        List<RailEditHideRule> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : castMap(hiddenMap).entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                continue;
            }

            RailEditHideRule rule = readHideRule(entry.getKey(), castMap(data));
            if (rule != null) {
                result.add(rule);
            }
        }
        return List.copyOf(result);
    }

    private static RailEditMask readMask(String maskId, Map<String, Object> maskMap) {
        String name = nullableString(maskMap.get("name"));
        String world = nullableString(maskMap.get("world"));
        boolean enabled = bool(maskMap.get("enabled"), true);
        Object areaValue = maskMap.get("area");
        if (name == null || world == null || !(areaValue instanceof Map<?, ?> areaMap)) {
            return null;
        }

        List<Integer> min = integerList(castMap(areaMap).get("min"));
        List<Integer> max = integerList(castMap(areaMap).get("max"));
        if (min.size() < 3 || max.size() < 3) {
            return null;
        }

        return new RailEditMask(maskId, name, world, enabled, min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
    }

    private static RailEditHideRule readHideRule(String ruleId, Map<String, Object> ruleMap) {
        String name = nullableString(ruleMap.get("name"));
        boolean enabled = bool(ruleMap.get("enabled"), true);
        Set<String> routeIds = stringSet(ruleMap.get("route-ids"));
        Set<String> componentIds = stringSet(ruleMap.get("component-ids"));
        if (name == null || (routeIds.isEmpty() && componentIds.isEmpty())) {
            return null;
        }

        return new RailEditHideRule(ruleId, name, enabled, routeIds, componentIds);
    }

    private static Path editsFile() {
        return FabricRailwayConfigLoader.dataDirectory().resolve("edits.yml");
    }

    private void writeEdits(List<RailEditMask> updatedMasks, List<RailEditHideRule> updatedHiddenLines) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);

        Map<String, Object> masksSection = new LinkedHashMap<>();
        for (RailEditMask mask : updatedMasks) {
            Map<String, Object> maskMap = new LinkedHashMap<>();
            maskMap.put("name", mask.name());
            maskMap.put("world", mask.worldName());
            maskMap.put("enabled", mask.enabled());
            Map<String, Object> areaMap = new LinkedHashMap<>();
            areaMap.put("type", "box");
            areaMap.put("min", List.of(mask.minX(), mask.minY(), mask.minZ()));
            areaMap.put("max", List.of(mask.maxX(), mask.maxY(), mask.maxZ()));
            maskMap.put("area", areaMap);
            masksSection.put(mask.id(), maskMap);
        }
        root.put("masks", masksSection);

        Map<String, Object> hiddenLinesSection = new LinkedHashMap<>();
        for (RailEditHideRule hiddenLine : updatedHiddenLines) {
            Map<String, Object> hiddenLineMap = new LinkedHashMap<>();
            hiddenLineMap.put("name", hiddenLine.name());
            hiddenLineMap.put("enabled", hiddenLine.enabled());
            hiddenLineMap.put("route-ids", new ArrayList<>(hiddenLine.routeIds().stream().sorted().toList()));
            hiddenLineMap.put("component-ids", new ArrayList<>(hiddenLine.componentIds().stream().sorted().toList()));
            hiddenLinesSection.put(hiddenLine.id(), hiddenLineMap);
        }
        root.put("hidden-lines", hiddenLinesSection);

        try (Writer writer = Files.newBufferedWriter(editsFile(), StandardCharsets.UTF_8)) {
            yamlWriter().dump(root, writer);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save edits.yml: " + exception.getMessage(), exception);
        }
    }

    private static void ensureDefaultFile(FabricRailwayLogger log) {
        Path file = editsFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                return;
            }

            try (InputStream input = FabricEditRegistry.class.getClassLoader().getResourceAsStream("edits.yml")) {
                if (input == null) {
                    return;
                }
                Files.copy(input, file);
            }
        } catch (IOException exception) {
            log.warning("Failed to create default edits.yml: " + exception.getMessage());
        }
    }

    private static Yaml yamlWriter() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String nullableString(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }

        Set<String> result = new LinkedHashSet<>();
        for (Object entry : list) {
            if (entry instanceof String string) {
                result.add(string);
            }
        }
        return Set.copyOf(result);
    }

    private record Interval(double start, double end) {
    }

    private record SegmentPiece(Vector3d start, Vector3d end) {
    }
}
