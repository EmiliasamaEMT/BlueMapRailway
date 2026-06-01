package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.model.RailDirection;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import io.github.emiliasamaemt.bluemaprailway.web.SimpleJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class FabricRailChunkCache {

    private static final FabricRailChunkCache DISABLED = new FabricRailChunkCache(null, false, Map.of(), Set.of());

    private final Path directory;
    private final boolean enabled;
    private final Map<ChunkRef, List<RailNode>> chunks;
    private final Set<ChunkRef> dirtyChunks;

    private FabricRailChunkCache(
            Path directory,
            boolean enabled,
            Map<ChunkRef, List<RailNode>> chunks,
            Set<ChunkRef> dirtyChunks
    ) {
        this.directory = directory;
        this.enabled = enabled;
        this.chunks = new HashMap<>(chunks);
        this.dirtyChunks = new HashSet<>(dirtyChunks);
    }

    public static FabricRailChunkCache load(FabricRailwayConfig config, FabricRailwayLogger log) {
        if (!config.cache().enabled()) {
            return DISABLED;
        }

        Path directory = cacheDirectory(config);
        return new FabricRailChunkCache(directory, true, loadSplitChunks(log, directory), Set.of());
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

    public synchronized Set<ChunkRef> chunkRefs(Set<String> enabledWorlds) {
        LinkedHashSet<ChunkRef> refs = new LinkedHashSet<>();
        for (ChunkRef ref : chunks.keySet()) {
            if (enabledWorlds.contains(ref.worldName())) {
                refs.add(ref);
            }
        }
        return Set.copyOf(refs);
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

    public void save(FabricRailwayLogger log) {
        if (!enabled) {
            return;
        }

        Snapshot snapshot = snapshotDirtyChunks();
        if (snapshot.entries().isEmpty() && snapshot.removed().isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(directory);
            for (ChunkRef ref : snapshot.removed()) {
                Path file = chunkFile(ref);
                if (Files.exists(file)) {
                    Files.delete(file);
                }
            }

            for (Map.Entry<ChunkRef, List<RailNode>> entry : snapshot.entries().entrySet()) {
                Path file = chunkFile(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, chunkJson(entry.getKey(), entry.getValue()), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            restoreDirty(snapshot);
            log.warning("Failed to save Fabric split rail cache: " + exception.getMessage());
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

    private Path chunkFile(ChunkRef ref) {
        return directory.resolve(sanitizePathPart(ref.worldName())).resolve(ref.x() + "_" + ref.z() + ".json");
    }

    private static Path cacheDirectory(FabricRailwayConfig config) {
        String configured = config.cache().directory();
        return FabricRailwayConfigLoader.dataDirectory().resolve(
                configured == null || configured.isBlank() ? "cache/chunks" : configured
        );
    }

    private static String sanitizePathPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Map<ChunkRef, List<RailNode>> loadSplitChunks(FabricRailwayLogger log, Path directory) {
        Map<ChunkRef, List<RailNode>> chunks = new HashMap<>();
        if (!Files.isDirectory(directory)) {
            return chunks;
        }

        try (Stream<Path> paths = Files.walk(directory, 2)) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readChunkFile(log, path, chunks));
        } catch (IOException exception) {
            log.warning("Failed to list Fabric split rail cache: " + exception.getMessage());
        }

        return chunks;
    }

    private static void readChunkFile(FabricRailwayLogger log, Path path, Map<ChunkRef, List<RailNode>> chunks) {
        try {
            Object parsed = SimpleJson.parse(Files.readString(path, StandardCharsets.UTF_8));
            readChunk(SimpleJson.object(parsed)).ifPresent(chunk -> chunks.put(chunk.ref(), chunk.nodes()));
        } catch (RuntimeException | IOException exception) {
            log.warning("Skipping broken Fabric rail cache chunk " + path + ": " + exception.getMessage());
        }
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
        Object railsValue = chunkMap.get("rails");
        if (railsValue instanceof List<?> rails) {
            for (Object railValue : rails) {
                if (railValue instanceof Map<?, ?> railMap) {
                    readRail(world, railMap).ifPresent(nodes::add);
                }
            }
        }

        return java.util.Optional.of(new CachedChunk(new ChunkRef(world, x, z), List.copyOf(nodes)));
    }

    private static java.util.Optional<RailNode> readRail(String worldName, Map<?, ?> railMap) {
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

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean boolValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private record Snapshot(Map<ChunkRef, List<RailNode>> entries, Set<ChunkRef> removed) {
    }

    private record CachedChunk(ChunkRef ref, List<RailNode> nodes) {
    }
}
