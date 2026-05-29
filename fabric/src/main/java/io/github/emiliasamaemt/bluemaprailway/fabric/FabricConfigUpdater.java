package io.github.emiliasamaemt.bluemaprailway.fabric;

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
import java.util.List;
import java.util.Map;

public final class FabricConfigUpdater {

    private static final Yaml YAML = FabricYamlSupport.readerYaml();

    private FabricConfigUpdater() {
    }

    public static List<String> addMissingDefaults(FabricRailwayLogger log) {
        Path file = FabricRailwayConfigLoader.configFile();
        Map<String, Object> current = loadMap(file, log);
        Map<String, Object> defaults = loadDefaultMap(log);
        List<String> addedPaths = new ArrayList<>();
        boolean hasCustomWorlds = current.get("worlds") instanceof Map<?, ?>;

        mergeMissing(current, defaults, "", hasCustomWorlds, addedPaths);
        if (addedPaths.isEmpty()) {
            return List.of();
        }

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            yamlWriter().dump(current, writer);
        } catch (IOException exception) {
            log.warning("Failed to save updated Fabric config.yml: " + exception.getMessage());
        }
        return List.copyOf(addedPaths);
    }

    @SuppressWarnings("unchecked")
    private static void mergeMissing(
            Map<String, Object> current,
            Map<String, Object> defaults,
            String prefix,
            boolean hasCustomWorlds,
            List<String> addedPaths
    ) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            String path = prefix.isBlank() ? key : prefix + "." + key;
            Object defaultValue = entry.getValue();

            if (hasCustomWorlds && path.startsWith("worlds.")) {
                continue;
            }

            if (!current.containsKey(key)) {
                current.put(key, deepCopy(defaultValue));
                addedPaths.add(path);
                continue;
            }

            Object currentValue = current.get(key);
            if (currentValue instanceof Map<?, ?> currentMap && defaultValue instanceof Map<?, ?> defaultMap) {
                Map<String, Object> mutableCurrentMap = mutableMap(current, key, currentMap);
                mergeMissing(
                        mutableCurrentMap,
                        castMap(defaultMap),
                        path,
                        hasCustomWorlds,
                        addedPaths
                );
            }
        }
    }

    private static Map<String, Object> loadMap(Path file, FabricRailwayLogger log) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (loaded instanceof Map<?, ?> map) {
                return castMap(map);
            }
        } catch (IOException | RuntimeException exception) {
            log.warning("Failed to read Fabric config.yml for update: " + FabricYamlSupport.errorMessage(exception));
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> loadDefaultMap(FabricRailwayLogger log) {
        try (InputStream input = FabricConfigUpdater.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) {
                return new LinkedHashMap<>();
            }
            try (Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
                Object loaded = YAML.load(reader);
                if (loaded instanceof Map<?, ?> map) {
                    return castMap(map);
                }
            }
        } catch (IOException | RuntimeException exception) {
            log.warning("Failed to load default Fabric config.yml: " + FabricYamlSupport.errorMessage(exception));
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Map<String, Object> parent, String key, Map<?, ?> rawMap) {
        if (rawMap instanceof LinkedHashMap<?, ?> || rawMap instanceof java.util.HashMap<?, ?>) {
            return (Map<String, Object>) rawMap;
        }

        Map<String, Object> converted = castMap(rawMap);
        parent.put(key, converted);
        return converted;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, deepCopy(entry.getValue()));
                }
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    private static Yaml yamlWriter() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }
}
