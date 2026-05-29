package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import io.github.emiliasamaemt.bluemaprailway.scan.RailGraphBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class FabricRailScanner {

    private final FabricRailwayConfig config;
    private final FabricRailwayLogger log;
    private final FabricRailBlockReader blockReader;
    private final RailGraphBuilder graphBuilder;

    public FabricRailScanner(FabricRailwayConfig config, FabricRailwayLogger log) {
        this.config = config;
        this.log = log;
        this.blockReader = new FabricRailBlockReader();
        this.graphBuilder = new RailGraphBuilder();
    }

    public Set<ChunkRef> cachedChunkRefs() {
        FabricRailChunkCache cache = FabricRailChunkCache.load(config, log);
        return cache.chunkRefs(enabledWorldIds());
    }

    public Set<ChunkRef> initialChunkRefs(MinecraftServer server) {
        Set<ChunkRef> chunkRefs = new LinkedHashSet<>();
        for (Map.Entry<String, FabricRailwayConfig.FabricWorldConfig> entry : config.worlds().entrySet()) {
            FabricRailwayConfig.FabricWorldConfig worldConfig = entry.getValue();
            if (!worldConfig.enabled()) {
                continue;
            }

            ServerLevel world = findWorld(server, entry.getKey());
            if (world == null) {
                continue;
            }

            chunkRefs.addAll(initialChunkRefs(world, worldConfig.scanRadius()));
        }
        return Set.copyOf(chunkRefs);
    }

    public RailScanResult scan(MinecraftServer server) {
        return scan(server, initialChunkRefs(server));
    }

    public RailScanResult scan(MinecraftServer server, Set<ChunkRef> chunkRefs) {
        Map<RailPosition, RailNode> nodes = new HashMap<>();
        FabricRailChunkCache cache = FabricRailChunkCache.load(config, log);
        Set<String> enabledWorlds = enabledWorldIds();
        cache.mergeInto(nodes, enabledWorlds);

        int scannedChunks = 0;
        for (ChunkRef chunkRef : new LinkedHashSet<>(chunkRefs)) {
            if (!enabledWorlds.contains(chunkRef.worldName())) {
                continue;
            }

            ServerLevel world = findWorld(server, chunkRef.worldName());
            if (world == null) {
                continue;
            }

            scannedChunks += scanChunkRef(world, chunkRef, nodes, cache);
        }

        cache.save(log);
        var graphResult = graphBuilder.build(nodes, config.yOffset(), config.core().lineFilter());
        return new RailScanResult(
                Map.copyOf(nodes),
                graphResult.components(),
                graphResult.lines(),
                scannedChunks,
                cache.chunkCount(enabledWorlds),
                cache.railCount(enabledWorlds),
                graphResult.hiddenLineCount()
        );
    }

    private ServerLevel findWorld(MinecraftServer server, String configuredId) {
        for (ServerLevel world : server.getAllLevels()) {
            String fullId = worldId(world);
            String pathId = world.dimension().identifier().getPath();
            if (configuredId.equals(fullId) || configuredId.equals(pathId)) {
                return world;
            }
        }
        return null;
    }

    private List<String> availableWorldIds(MinecraftServer server) {
        List<String> worldIds = new ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            worldIds.add(worldId(world));
        }
        return worldIds;
    }

    private Set<ChunkRef> initialChunkRefs(ServerLevel world, int scanRadius) {
        LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
        BlockPos spawn = respawnData != null ? respawnData.pos() : BlockPos.ZERO;
        int centerChunkX = spawn.getX() >> 4;
        int centerChunkZ = spawn.getZ() >> 4;
        int chunkRadius = Math.max(0, (scanRadius + 15) >> 4);
        Set<ChunkRef> chunkRefs = new LinkedHashSet<>();

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                chunkRefs.add(new ChunkRef(worldId(world), chunkX, chunkZ));
            }
        }

        return Set.copyOf(chunkRefs);
    }

    private int scanChunkRef(ServerLevel world, ChunkRef chunkRef, Map<RailPosition, RailNode> nodes, FabricRailChunkCache cache) {
        ServerChunkCache chunkSource = world.getChunkSource();
        LevelChunk chunk = chunkSource.getChunkNow(chunkRef.x(), chunkRef.z());
        if (chunk == null) {
            return 0;
        }

        List<RailNode> chunkNodes = scanChunk(world, chunk, nodes);
        cache.put(chunkRef, chunkNodes);
        return 1;
    }

    private List<RailNode> scanChunk(ServerLevel world, LevelChunk chunk, Map<RailPosition, RailNode> nodes) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        String worldId = worldId(world);
        int minY = world.getMinY();
        int maxY = world.getMaxY() - 1;
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        List<RailNode> chunkNodes = new ArrayList<>();

        removeChunkNodes(nodes, worldId, chunk.getPos().x, chunk.getPos().z);

        for (int localX = 0; localX < 16; localX++) {
            int x = baseX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = baseZ + localZ;
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);
                    blockReader.read(worldId, x, y, z, chunk.getBlockState(mutable))
                            .ifPresent(node -> {
                                nodes.put(node.position(), node);
                                chunkNodes.add(node);
                            });
                }
            }
        }

        return chunkNodes;
    }

    private Set<String> enabledWorldIds() {
        Set<String> enabledWorlds = new LinkedHashSet<>();
        for (Map.Entry<String, FabricRailwayConfig.FabricWorldConfig> entry : config.worlds().entrySet()) {
            if (entry.getValue().enabled()) {
                enabledWorlds.add(entry.getKey());
            }
        }
        return Set.copyOf(enabledWorlds);
    }

    private void removeChunkNodes(Map<RailPosition, RailNode> nodes, String worldName, int chunkX, int chunkZ) {
        nodes.entrySet().removeIf(entry -> {
            RailPosition position = entry.getKey();
            return position.worldName().equals(worldName) &&
                    (position.x() >> 4) == chunkX &&
                    (position.z() >> 4) == chunkZ;
        });
    }

    private String worldId(ServerLevel world) {
        Identifier value = world.dimension().identifier();
        return value.toString();
    }
}
