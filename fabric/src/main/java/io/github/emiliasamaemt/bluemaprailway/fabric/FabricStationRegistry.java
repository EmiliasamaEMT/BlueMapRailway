package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.station.RailStation;
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

public final class FabricStationRegistry {

    private static final Yaml YAML = FabricYamlSupport.readerYaml();

    private final List<RailStation> stations;

    private FabricStationRegistry(List<RailStation> stations) {
        this.stations = List.copyOf(stations);
    }

    public static FabricStationRegistry load(FabricRailwayLogger log) {
        ensureDefaultFile(log);

        Path file = stationsFile();
        if (!Files.exists(file)) {
            return new FabricStationRegistry(List.of());
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return new FabricStationRegistry(List.of());
            }

            Object stationsSection = castMap(map).get("stations");
            if (!(stationsSection instanceof Map<?, ?> stationMap)) {
                return new FabricStationRegistry(List.of());
            }

            List<RailStation> stations = new ArrayList<>();
            for (Map.Entry<String, Object> entry : castMap(stationMap).entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> data)) {
                    continue;
                }

                RailStation station = readStation(entry.getKey(), castMap(data));
                if (station != null) {
                    stations.add(station);
                }
            }

            return new FabricStationRegistry(stations);
        } catch (IOException | RuntimeException exception) {
            log.warning("Failed to read stations.yml, using empty stations: " + FabricYamlSupport.errorMessage(exception));
            return new FabricStationRegistry(List.of());
        }
    }

    public List<RailStation> stations() {
        return stations;
    }

    public int stationCount() {
        return stations.size();
    }

    public RailStation station(String stationId) {
        for (RailStation station : stations) {
            if (station.id().equals(stationId)) {
                return station;
            }
        }
        return null;
    }

    public void saveStation(RailStation station, FabricRailwayLogger log) {
        List<RailStation> updated = new ArrayList<>();
        boolean replaced = false;
        for (RailStation existing : stations) {
            if (existing.id().equals(station.id())) {
                updated.add(station);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) {
            updated.add(station);
        }
        writeStations(updated);
    }

    public boolean deleteStation(String stationId, FabricRailwayLogger log) {
        List<RailStation> updated = stations.stream()
                .filter(station -> !station.id().equals(stationId))
                .toList();
        if (updated.size() == stations.size()) {
            return false;
        }
        writeStations(updated);
        return true;
    }

    private static RailStation readStation(String stationId, Map<String, Object> stationMap) {
        String name = nullableString(stationMap.get("name"));
        String world = nullableString(stationMap.get("world"));
        Object areaValue = stationMap.get("area");
        if (name == null || world == null || !(areaValue instanceof Map<?, ?> areaMap)) {
            return null;
        }

        List<Integer> min = integerList(castMap(areaMap).get("min"));
        List<Integer> max = integerList(castMap(areaMap).get("max"));
        if (min.size() < 3 || max.size() < 3) {
            return null;
        }

        return new RailStation(
                stationId,
                name,
                world,
                Math.min(min.get(0), max.get(0)),
                Math.min(min.get(1), max.get(1)),
                Math.min(min.get(2), max.get(2)),
                Math.max(min.get(0), max.get(0)),
                Math.max(min.get(1), max.get(1)),
                Math.max(min.get(2), max.get(2))
        );
    }

    private static Path stationsFile() {
        return FabricRailwayConfigLoader.dataDirectory().resolve("stations.yml");
    }

    private void writeStations(List<RailStation> updatedStations) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> stationsSection = new LinkedHashMap<>();
        for (RailStation station : updatedStations) {
            Map<String, Object> stationMap = new LinkedHashMap<>();
            stationMap.put("name", station.name());
            stationMap.put("world", station.worldName());
            Map<String, Object> areaMap = new LinkedHashMap<>();
            areaMap.put("type", "box");
            areaMap.put("min", List.of(station.minX(), station.minY(), station.minZ()));
            areaMap.put("max", List.of(station.maxX(), station.maxY(), station.maxZ()));
            stationMap.put("area", areaMap);
            stationsSection.put(station.id(), stationMap);
        }
        root.put("stations", stationsSection);

        try (Writer writer = Files.newBufferedWriter(stationsFile(), StandardCharsets.UTF_8)) {
            yamlWriter().dump(root, writer);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save stations.yml: " + exception.getMessage(), exception);
        }
    }

    private static void ensureDefaultFile(FabricRailwayLogger log) {
        Path file = stationsFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                return;
            }

            try (InputStream input = FabricStationRegistry.class.getClassLoader().getResourceAsStream("stations.yml")) {
                if (input == null) {
                    return;
                }
                Files.copy(input, file);
            }
        } catch (IOException exception) {
            log.warning("Failed to create default stations.yml: " + exception.getMessage());
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
}
