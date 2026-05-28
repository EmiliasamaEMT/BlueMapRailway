package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.model.RailDirection;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FabricRailChunkCache {

    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));
    private static final FabricRailChunkCache DISABLED = new FabricRailChunkCache(null, false, Map.of());

    private final Path file;
    private final boolean enabled;
    private final Map<ChunkRef, List<RailNode>> chunks;

    private FabricRailChunkCache(Path file, boolean enabled, Map<ChunkRef, List<RailNode>> chunks) {
        this.file = file;
        this.enabled = enabled;
        this.chunks = new HashMap<>(chunks);
    }

    public static FabricRailChunkCache load(FabricRailwayConfig config, FabricRailwayLogger log) {
        if (!config.cache().enabled()) {
            return DISABLED;
        }

        Path file = cacheFile(config);
        if (!Files.exists(file)) {
            return new FabricRailChunkCache(file, true, Map.of());
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return new FabricRailChunkCache(file, true, Map.of());
            }

            Map<String, Object> root = castMap(map);
            Object chunksValue = root.get("chunks");
            if (!(chunksValue instanceof List<?> list)) {
                return new FabricRailChunkCache(file, true, Map.of());
            }

            Map<ChunkRef, List<RailNode>> chunks = new HashMap<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> chunkMap) {
                    readChunk(castMap(chunkMap)).ifPresent(chunk -> chunks.put(chunk.ref(), chunk.nodes()));
                }
            }

            return new FabricRailChunkCache(file, true, chunks);
        } catch (IOException exception) {
            log.warning("Failed to read Fabric rail cache, using empty cache: " + exception.getMessage());
            return new FabricRailChunkCache(file, true, Map.of());
        }
    }

    public void mergeInto(Map<RailPosition, RailNode> nodes, Set<String> enabledWorlds) {
        if (!enabled) {
            return;
        }

        for (Map.Entry<ChunkRef, List<RailNode>> entry : chunks.entrySet()) {
            if (!enabledWorlds.contains(entry.getKey().worldName())) {
                continue;
            }
            for (RailNode node : entry.getValue()) {
                nodes.put(node.position(), node);
            }
        }
    }

    public void put(ChunkRef ref, List<RailNode> nodes) {
        if (!enabled) {
            return;
        }

        if (nodes.isEmpty()) {
            chunks.remove(ref);
            return;
        }

        chunks.put(ref, List.copyOf(nodes));
    }

    public int chunkCount(Set<String> enabledWorlds) {
        int count = 0;
        for (ChunkRef ref : chunks.keySet()) {
            if (enabledWorlds.contains(ref.worldName())) {
                count++;
            }
        }
        return count;
    }

    public Set<ChunkRef> chunkRefs(Set<String> enabledWorlds) {
        java.util.LinkedHashSet<ChunkRef> refs = new java.util.LinkedHashSet<>();
        for (ChunkRef ref : chunks.keySet()) {
            if (enabledWorlds.contains(ref.worldName())) {
                refs.add(ref);
            }
        }
        return Set.copyOf(refs);
    }

    public int railCount(Set<String> enabledWorlds) {
        int count = 0;
        for (Map.Entry<ChunkRef, List<RailNode>> entry : chunks.entrySet()) {
            if (enabledWorlds.contains(entry.getKey().worldName())) {
                count += entry.getValue().size();
            }
        }
        return count;
    }

    public void save(FabricRailwayLogger log) {
        if (!enabled || file == null) {
            return;
        }

        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 1);
            root.put("chunks", serializeChunks());

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                YAML.dump(root, writer);
            }
        } catch (IOException exception) {
            log.warning("Failed to save Fabric rail cache: " + exception.getMessage());
        }
    }

    private List<Map<String, Object>> serializeChunks() {
        List<Map<String, Object>> serialized = new ArrayList<>();
        chunks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> {
                    int world = a.worldName().compareTo(b.worldName());
                    if (world != 0) {
                        return world;
                    }
                    int x = Integer.compare(a.x(), b.x());
                    return x != 0 ? x : Integer.compare(a.z(), b.z());
                }))
                .forEach(entry -> serialized.add(serializeChunk(entry.getKey(), entry.getValue())));
        return serialized;
    }

    private Map<String, Object> serializeChunk(ChunkRef ref, List<RailNode> nodes) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("world", ref.worldName());
        chunk.put("x", ref.x());
        chunk.put("z", ref.z());

        List<Map<String, Object>> rails = new ArrayList<>();
        for (RailNode node : nodes) {
            Map<String, Object> rail = new LinkedHashMap<>();
            rail.put("x", node.position().x());
            rail.put("y", node.position().y());
            rail.put("z", node.position().z());
            rail.put("type", node.type().name());
            rail.put("shape", node.shapeName());
            rail.put("powered", node.powered());
            rails.add(rail);
        }
        chunk.put("rails", rails);
        return chunk;
    }

    private static java.util.Optional<CachedChunk> readChunk(Map<String, Object> chunkMap) {
        String world = stringValue(chunkMap.get("world"));
        Integer x = intValue(chunkMap.get("x"));
        Integer z = intValue(chunkMap.get("z"));
        if (world == null || x == null || z == null) {
            return java.util.Optional.empty();
        }

        List<RailNode> nodes = new ArrayList<>();
        Object railsValue = chunkMap.get("rails");
        if (railsValue instanceof List<?> rails) {
            for (Object railValue : rails) {
                if (railValue instanceof Map<?, ?> railMap) {
                    readRail(world, castMap(railMap)).ifPresent(nodes::add);
                }
            }
        }

        return java.util.Optional.of(new CachedChunk(new ChunkRef(world, x, z), List.copyOf(nodes)));
    }

    private static java.util.Optional<RailNode> readRail(String worldName, Map<String, Object> railMap) {
        Integer x = intValue(railMap.get("x"));
        Integer y = intValue(railMap.get("y"));
        Integer z = intValue(railMap.get("z"));
        String typeName = stringValue(railMap.get("type"));
        String shapeName = stringValue(railMap.get("shape"));
        boolean powered = boolValue(railMap.get("powered"));
        if (x == null || y == null || z == null || typeName == null || shapeName == null) {
            return java.util.Optional.empty();
        }

        try {
            RailType type = RailType.valueOf(typeName);
            Set<RailDirection> directions = directionsFor(shapeName);
            return java.util.Optional.of(new RailNode(
                    new RailPosition(worldName, x, y, z),
                    type,
                    shapeName,
                    powered,
                    directions
            ));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static Set<RailDirection> directionsFor(String shapeName) {
        return switch (shapeName) {
            case "NORTH_SOUTH" -> Set.of(RailDirection.NORTH, RailDirection.SOUTH);
            case "EAST_WEST" -> Set.of(RailDirection.EAST, RailDirection.WEST);
            case "ASCENDING_EAST" -> Set.of(RailDirection.ASCENDING_EAST, RailDirection.WEST);
            case "ASCENDING_WEST" -> Set.of(RailDirection.EAST, RailDirection.ASCENDING_WEST);
            case "ASCENDING_NORTH" -> Set.of(RailDirection.ASCENDING_NORTH, RailDirection.SOUTH);
            case "ASCENDING_SOUTH" -> Set.of(RailDirection.NORTH, RailDirection.ASCENDING_SOUTH);
            case "SOUTH_EAST" -> Set.of(RailDirection.SOUTH, RailDirection.EAST);
            case "SOUTH_WEST" -> Set.of(RailDirection.SOUTH, RailDirection.WEST);
            case "NORTH_WEST" -> Set.of(RailDirection.NORTH, RailDirection.WEST);
            case "NORTH_EAST" -> Set.of(RailDirection.NORTH, RailDirection.EAST);
            default -> EnumSet.noneOf(RailDirection.class);
        };
    }

    private static Path cacheFile(FabricRailwayConfig config) {
        String configured = config.cache().file();
        return FabricRailwayConfigLoader.dataDirectory().resolve(
                configured == null || configured.isBlank() ? "cache/rail-cache.yml" : configured
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean boolValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private record CachedChunk(ChunkRef ref, List<RailNode> nodes) {
    }
}
