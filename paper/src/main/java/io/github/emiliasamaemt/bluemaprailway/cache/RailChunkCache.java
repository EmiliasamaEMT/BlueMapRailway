package io.github.emiliasamaemt.bluemaprailway.cache;

import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import io.github.emiliasamaemt.bluemaprailway.scan.RailBlockReader;
import io.github.emiliasamaemt.bluemaprailway.web.SimpleJson;
import org.bukkit.block.data.Rail;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RailChunkCache {

    private static final RailChunkCache DISABLED = new RailChunkCache(null, false, Map.of(), Set.of());

    private final File splitDirectory;
    private final boolean enabled;
    private final Map<ChunkRef, List<RailNode>> chunks;
    private final Set<ChunkRef> dirtyChunks;

    private RailChunkCache(
            File splitDirectory,
            boolean enabled,
            Map<ChunkRef, List<RailNode>> chunks,
            Set<ChunkRef> dirtyChunks
    ) {
        this.splitDirectory = splitDirectory;
        this.enabled = enabled;
        this.chunks = new HashMap<>(chunks);
        this.dirtyChunks = new HashSet<>(dirtyChunks);
    }

    public static RailChunkCache load(Plugin plugin) {
        if (!plugin.getConfig().getBoolean("cache.enabled", true)) {
            return DISABLED;
        }

        File splitDirectory = new File(plugin.getDataFolder(), plugin.getConfig().getString("cache.directory", "cache/chunks"));

        return new RailChunkCache(splitDirectory, true, loadSplitChunks(plugin, splitDirectory), Set.of());
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized void mergeInto(Map<RailPosition, RailNode> nodes, Set<String> enabledWorlds) {
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

    public synchronized void put(ChunkRef ref, List<RailNode> nodes) {
        if (!enabled) {
            return;
        }

        if (nodes.isEmpty()) {
            if (chunks.remove(ref) != null) {
                markDirty(ref);
            }
            return;
        }

        List<RailNode> newNodes = List.copyOf(nodes);
        if (!newNodes.equals(chunks.put(ref, newNodes))) {
            markDirty(ref);
        }
    }

    public synchronized int chunkCount(Set<String> enabledWorlds) {
        int count = 0;
        for (ChunkRef ref : chunks.keySet()) {
            if (enabledWorlds.contains(ref.worldName())) {
                count++;
            }
        }

        return count;
    }

    public synchronized int railCount(Set<String> enabledWorlds) {
        int count = 0;
        for (Map.Entry<ChunkRef, List<RailNode>> entry : chunks.entrySet()) {
            if (enabledWorlds.contains(entry.getKey().worldName())) {
                count += entry.getValue().size();
            }
        }

        return count;
    }

    public synchronized boolean hasDirtyChanges() {
        return !dirtyChunks.isEmpty();
    }

    public void save(Plugin plugin) {
        if (!enabled) {
            return;
        }

        Snapshot snapshot = snapshotDirtyChunks();
        if (snapshot.entries().isEmpty() && snapshot.removed().isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(splitDirectory.toPath());
            for (ChunkRef ref : snapshot.removed()) {
                File file = chunkFile(ref);
                if (file.exists() && !file.delete()) {
                    throw new IOException("Failed to delete stale cache chunk file: " + file);
                }
            }

            for (Map.Entry<ChunkRef, List<RailNode>> entry : snapshot.entries().entrySet()) {
                File file = chunkFile(entry.getKey());
                File parent = file.getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                Files.writeString(file.toPath(), chunkJson(entry.getKey(), entry.getValue()), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            restoreDirty(snapshot);
            plugin.getLogger().log(Level.WARNING, "保存铁路区块分片缓存失败: " + exception.getMessage(), exception);
        }
    }

    private synchronized void markDirty(ChunkRef ref) {
        dirtyChunks.add(ref);
    }

    private synchronized Snapshot snapshotDirtyChunks() {
        if (!hasDirtyChanges()) {
            return new Snapshot(Map.of(), Set.of());
        }

        Set<ChunkRef> refs = new HashSet<>(dirtyChunks);
        Set<ChunkRef> removed = new HashSet<>();
        Map<ChunkRef, List<RailNode>> entries = new LinkedHashMap<>();
        for (ChunkRef ref : refs) {
            List<RailNode> nodes = chunks.get(ref);
            if (nodes == null) {
                removed.add(ref);
            } else {
                entries.put(ref, List.copyOf(nodes));
            }
        }

        dirtyChunks.clear();
        return new Snapshot(entries, removed);
    }

    private synchronized void restoreDirty(Snapshot snapshot) {
        dirtyChunks.addAll(snapshot.entries().keySet());
        dirtyChunks.addAll(snapshot.removed());
    }

    private File chunkFile(ChunkRef ref) {
        return new File(new File(splitDirectory, sanitizePathPart(ref.worldName())), ref.x() + "_" + ref.z() + ".json");
    }

    private static String sanitizePathPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Map<ChunkRef, List<RailNode>> loadSplitChunks(Plugin plugin, File splitDirectory) {
        Map<ChunkRef, List<RailNode>> chunks = new HashMap<>();
        if (!splitDirectory.isDirectory()) {
            return chunks;
        }

        File[] worldDirectories = splitDirectory.listFiles(File::isDirectory);
        if (worldDirectories == null) {
            return chunks;
        }

        for (File worldDirectory : worldDirectories) {
            File[] files = worldDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
            if (files == null) {
                continue;
            }

            for (File file : files) {
                try {
                    Object parsed = SimpleJson.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                    readChunk(SimpleJson.object(parsed)).ifPresent(chunk -> chunks.put(chunk.ref(), chunk.nodes()));
                } catch (RuntimeException | IOException exception) {
                    plugin.getLogger().warning("跳过损坏的铁路分片缓存 " + file + ": " + exception.getMessage());
                }
            }
        }

        return chunks;
    }

    private static String chunkJson(ChunkRef ref, List<RailNode> nodes) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        appendJsonField(builder, "world", SimpleJson.string(ref.worldName()), true);
        appendJsonField(builder, "x", Integer.toString(ref.x()), true);
        appendJsonField(builder, "z", Integer.toString(ref.z()), true);
        builder.append("  \"rails\": [\n");
        for (int index = 0; index < nodes.size(); index++) {
            RailNode node = nodes.get(index);
            builder.append("    {");
            builder.append("\"x\":").append(node.position().x()).append(',');
            builder.append("\"y\":").append(node.position().y()).append(',');
            builder.append("\"z\":").append(node.position().z()).append(',');
            builder.append("\"type\":").append(SimpleJson.string(node.type().name())).append(',');
            builder.append("\"shape\":").append(SimpleJson.string(node.shapeName())).append(',');
            builder.append("\"powered\":").append(node.powered());
            builder.append('}');
            if (index + 1 < nodes.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static void appendJsonField(StringBuilder builder, String key, String value, boolean comma) {
        builder.append("  ").append(SimpleJson.string(key)).append(": ").append(value);
        if (comma) {
            builder.append(',');
        }
        builder.append('\n');
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
            return java.util.Optional.of(new RailNode(position, type, shape.name(), powered, RailBlockReader.directionsFor(shape)));
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

    private record Snapshot(Map<ChunkRef, List<RailNode>> entries, Set<ChunkRef> removed) {
    }

    private record CachedChunk(ChunkRef ref, List<RailNode> nodes) {
    }
}
