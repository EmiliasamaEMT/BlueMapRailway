package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.edit.RailEditHideRule;
import io.github.emiliasamaemt.bluemaprailway.edit.RailEditMask;
import io.github.emiliasamaemt.bluemaprailway.edit.RailEditProcessor;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

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

    private static final Yaml YAML = FabricYamlSupport.readerYaml();

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
        } catch (IOException | RuntimeException exception) {
            log.warning("Failed to read edits.yml, using empty edits: " + FabricYamlSupport.errorMessage(exception));
            return new FabricEditRegistry(List.of(), List.of());
        }
    }

    public RailScanResult apply(RailScanResult result) {
        return RailEditProcessor.apply(result, masks, hiddenLines);
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

}
