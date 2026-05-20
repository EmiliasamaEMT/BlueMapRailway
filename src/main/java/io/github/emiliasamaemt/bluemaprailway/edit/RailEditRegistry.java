package io.github.emiliasamaemt.bluemaprailway.edit;

import com.flowpowered.math.vector.Vector3d;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RailEditRegistry {

    private static final double EPSILON = 1.0E-6;

    private final List<RailEditMask> masks;
    private final List<RailEditHideRule> hiddenLines;

    private RailEditRegistry(List<RailEditMask> masks, List<RailEditHideRule> hiddenLines) {
        this.masks = List.copyOf(masks);
        this.hiddenLines = List.copyOf(hiddenLines);
    }

    public static RailEditRegistry load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "edits.yml");
        ensureDefaultFile(file, plugin);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection masksSection = configuration.getConfigurationSection("masks");
        List<RailEditMask> masks = masksSection == null
                ? List.of()
                : masksSection.getKeys(false).stream()
                .map(maskId -> readMask(masksSection, maskId))
                .filter(mask -> mask != null)
                .toList();
        ConfigurationSection hiddenLinesSection = configuration.getConfigurationSection("hidden-lines");
        List<RailEditHideRule> hiddenLines = hiddenLinesSection == null
                ? List.of()
                : hiddenLinesSection.getKeys(false).stream()
                .map(ruleId -> readHideRule(hiddenLinesSection, ruleId))
                .filter(rule -> rule != null)
                .toList();
        return new RailEditRegistry(masks, hiddenLines);
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
            if (!mask.enabled()) {
                continue;
            }
            if (!mask.appliesTo(line.worldName())) {
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

    private static RailEditMask readMask(ConfigurationSection section, String maskId) {
        String path = maskId + ".";
        String name = section.getString(path + "name", maskId);
        String world = section.getString(path + "world", "world");
        boolean enabled = section.getBoolean(path + "enabled", true);
        List<Integer> min = section.getIntegerList(path + "area.min");
        List<Integer> max = section.getIntegerList(path + "area.max");
        if (min.size() < 3 || max.size() < 3) {
            return null;
        }

        return new RailEditMask(
                maskId,
                name,
                world,
                enabled,
                min.get(0),
                min.get(1),
                min.get(2),
                max.get(0),
                max.get(1),
                max.get(2)
        );
    }

    private static RailEditHideRule readHideRule(ConfigurationSection section, String ruleId) {
        String path = ruleId + ".";
        String name = section.getString(path + "name", ruleId);
        boolean enabled = section.getBoolean(path + "enabled", true);
        Set<String> routeIds = new LinkedHashSet<>(section.getStringList(path + "route-ids"));
        Set<String> componentIds = new LinkedHashSet<>(section.getStringList(path + "component-ids"));
        if (routeIds.isEmpty() && componentIds.isEmpty()) {
            return null;
        }

        return new RailEditHideRule(ruleId, name, enabled, routeIds, componentIds);
    }

    private static void ensureDefaultFile(File file, JavaPlugin plugin) {
        if (file.exists()) {
            return;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), defaultEditsFile(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "无法创建 edits.yml: " + exception.getMessage(), exception);
        }
    }

    private static String defaultEditsFile() {
        return """
                version: 1

                masks:
                  # machine-rail:
                  #   name: "机房误识别铁轨"
                  #   world: "world"
                  #   enabled: true
                  #   area:
                  #     type: box
                  #     min: [120, 0, -30]
                  #     max: [170, 320, 20]

                hidden-lines:
                  # hide-main-line:
                  #   name: "隐藏主线"
                  #   enabled: true
                  #   route-ids:
                  #     - "main-line"
                  #   component-ids: []
                """;
    }

    private record Interval(double start, double end) {
    }

    private record SegmentPiece(Vector3d start, Vector3d end) {
    }
}
