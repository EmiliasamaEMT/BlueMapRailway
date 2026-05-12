package io.github.emiliasamaemt.bluemaprailway.cache;

import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import io.github.emiliasamaemt.bluemaprailway.scan.RailBlockReader;
import org.bukkit.block.data.Rail;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RailChunkCache {

    private static final RailChunkCache DISABLED = new RailChunkCache(null, false, Map.of());

    private final File file;
    private final boolean enabled;
    private final Map<ChunkRef, List<RailNode>> chunks;

    private RailChunkCache(File file, boolean enabled, Map<ChunkRef, List<RailNode>> chunks) {
        this.file = file;
        this.enabled = enabled;
        this.chunks = new HashMap<>(chunks);
    }

    public static RailChunkCache load(Plugin plugin) {
        if (!plugin.getConfig().getBoolean("cache.enabled", true)) {
            return DISABLED;
        }

        File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("cache.file", "cache/rail-cache.yml"));
        if (!file.exists()) {
            return new RailChunkCache(file, true, Map.of());
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        Map<ChunkRef, List<RailNode>> chunks = new HashMap<>();
        for (Map<?, ?> chunkMap : configuration.getMapList("chunks")) {
            readChunk(chunkMap).ifPresent(chunk -> chunks.put(chunk.ref(), chunk.nodes()));
        }

        return new RailChunkCache(file, true, chunks);
    }

    public boolean enabled() {
        return enabled;
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

    public int railCount(Set<String> enabledWorlds) {
        int count = 0;
        for (Map.Entry<ChunkRef, List<RailNode>> entry : chunks.entrySet()) {
            if (enabledWorlds.contains(entry.getKey().worldName())) {
                count += entry.getValue().size();
            }
        }

        return count;
    }

    public void save(Plugin plugin) {
        if (!enabled || file == null) {
            return;
        }

        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("version", 1);
            configuration.set("chunks", serializeChunks());
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "保存铁路区块缓存失败: " + exception.getMessage(), exception);
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
            rail.put("shape", node.shape().name());
            rail.put("powered", node.powered());
            rails.add(rail);
        }

        chunk.put("rails", rails);
        return chunk;
    }

    private static java.util.Optional<CachedChunk> readChunk(Map<?, ?> chunkMap) {
        String world = stringValue(chunkMap.get("world"));
        Integer x = intValue(chunkMap.get("x"));
        Integer z = intValue(chunkMap.get("z"));
        if (world == null || x == null || z == null) {
            return java.util.Optional.empty();
        }

        List<RailNode> nodes = new ArrayList<>();
        Object railsObject = chunkMap.get("rails");
        if (railsObject instanceof List<?> rails) {
            for (Object railObject : rails) {
                if (railObject instanceof Map<?, ?> railMap) {
                    readRail(world, railMap).ifPresent(nodes::add);
                }
            }
        }

        return java.util.Optional.of(new CachedChunk(new ChunkRef(world, x, z), nodes));
    }

    private static java.util.Optional<RailNode> readRail(String world, Map<?, ?> railMap) {
        Integer x = intValue(railMap.get("x"));
        Integer y = intValue(railMap.get("y"));
        Integer z = intValue(railMap.get("z"));
        String typeName = stringValue(railMap.get("type"));
        String shapeName = stringValue(railMap.get("shape"));
        boolean powered = booleanValue(railMap.get("powered"));
        if (x == null || y == null || z == null || typeName == null || shapeName == null) {
            return java.util.Optional.empty();
        }

        try {
            RailType type = RailType.valueOf(typeName);
            Rail.Shape shape = Rail.Shape.valueOf(shapeName);
            RailPosition position = new RailPosition(world, x, y, z);
            return java.util.Optional.of(new RailNode(position, type, shape, powered, RailBlockReader.directionsFor(shape)));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private record CachedChunk(ChunkRef ref, List<RailNode> nodes) {
    }
}
