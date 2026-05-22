package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.scan.RailGraphBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public RailScanResult scan(MinecraftServer server) {
        Map<RailPosition, RailNode> nodes = new HashMap<>();
        int scannedChunks = 0;

        for (Map.Entry<String, FabricRailwayConfig.FabricWorldConfig> entry : config.worlds().entrySet()) {
            FabricRailwayConfig.FabricWorldConfig worldConfig = entry.getValue();
            if (!worldConfig.enabled()) {
                continue;
            }

            ServerLevel world = findWorld(server, entry.getKey());
            if (world == null) {
                log.warning("Configured Fabric world not found: " + entry.getKey());
                continue;
            }

            scannedChunks += scanWorld(world, worldConfig.scanRadius(), nodes);
        }

        if (scannedChunks == 0 && !config.worlds().isEmpty()) {
            log.warning("Fabric scan found no chunks. Configured worlds=" + config.worlds().keySet()
                    + ", available worlds=" + availableWorldIds(server));
        }

        var graphResult = graphBuilder.build(nodes, config.yOffset(), config.core().lineFilter());
        return new RailScanResult(
                Map.copyOf(nodes),
                graphResult.components(),
                graphResult.lines(),
                scannedChunks,
                0,
                0,
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

    private int scanWorld(ServerLevel world, int scanRadius, Map<RailPosition, RailNode> nodes) {
        LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
        BlockPos spawn = respawnData != null ? respawnData.pos() : BlockPos.ZERO;
        int centerChunkX = spawn.getX() >> 4;
        int centerChunkZ = spawn.getZ() >> 4;
        int chunkRadius = Math.max(0, (scanRadius + 15) >> 4);
        int scanned = 0;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = world.getChunk(chunkX, chunkZ);
                scanChunk(world, chunk, nodes);
                scanned++;
            }
        }

        return scanned;
    }

    private void scanChunk(ServerLevel world, LevelChunk chunk, Map<RailPosition, RailNode> nodes) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        String worldId = worldId(world);
        int minY = world.getMinY();
        int maxY = world.getMaxY() - 1;
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int localX = 0; localX < 16; localX++) {
            int x = baseX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = baseZ + localZ;
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);
                    blockReader.read(worldId, x, y, z, chunk.getBlockState(mutable))
                            .ifPresent(node -> nodes.put(node.position(), node));
                }
            }
        }
    }

    private String worldId(ServerLevel world) {
        Identifier value = world.dimension().identifier();
        return value.toString();
    }
}
