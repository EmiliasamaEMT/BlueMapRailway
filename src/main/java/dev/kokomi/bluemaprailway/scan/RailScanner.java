package dev.kokomi.bluemaprailway.scan;

import dev.kokomi.bluemaprailway.model.RailNode;
import dev.kokomi.bluemaprailway.model.RailPosition;
import dev.kokomi.bluemaprailway.model.RailScanResult;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public final class RailScanner {

    private final Plugin plugin;
    private final RailBlockReader blockReader;
    private final RailGraphBuilder graphBuilder;
    private final Queue<ChunkRef> pendingChunks;
    private final Map<RailPosition, RailNode> nodes;
    private int scannedChunks;
    private boolean active;

    public RailScanner(Plugin plugin) {
        this.plugin = plugin;
        this.blockReader = new RailBlockReader();
        this.graphBuilder = new RailGraphBuilder();
        this.pendingChunks = new ArrayDeque<>();
        this.nodes = new HashMap<>();
    }

    public void begin() {
        pendingChunks.clear();
        nodes.clear();
        scannedChunks = 0;
        active = true;

        ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            active = false;
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            if (!worldsSection.getBoolean(worldName + ".enabled", false)) {
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("配置中的世界不存在，已跳过: " + worldName);
                continue;
            }

            queueWorld(world, worldsSection.getInt(worldName + ".scan-radius", -1));
        }

        plugin.getLogger().info("铁路扫描任务已创建，待扫描区块数: " + pendingChunks.size());
    }

    public boolean scanNextBatch(int chunksPerTick) {
        int scannedThisBatch = 0;

        while (scannedThisBatch < chunksPerTick && !pendingChunks.isEmpty()) {
            ChunkRef chunkRef = pendingChunks.poll();
            World world = Bukkit.getWorld(chunkRef.worldName());
            if (world != null && world.isChunkLoaded(chunkRef.x(), chunkRef.z())) {
                scanChunk(world.getChunkAt(chunkRef.x(), chunkRef.z()));
                scannedChunks++;
            }

            scannedThisBatch++;
        }

        if (pendingChunks.isEmpty()) {
            active = false;
        }

        return active;
    }

    public RailScanResult finish(double yOffset) {
        return new RailScanResult(
                Map.copyOf(nodes),
                graphBuilder.buildLines(nodes, yOffset, RailLineFilter.fromConfig(plugin.getConfig())),
                scannedChunks
        );
    }

    public boolean isActive() {
        return active;
    }

    public int pendingChunkCount() {
        return pendingChunks.size();
    }

    public int scannedChunkCount() {
        return scannedChunks;
    }

    private void queueWorld(World world, int scanRadius) {
        if (scanRadius < 0) {
            for (Chunk chunk : world.getLoadedChunks()) {
                pendingChunks.add(new ChunkRef(world.getName(), chunk.getX(), chunk.getZ()));
            }
            return;
        }

        int centerChunkX = world.getSpawnLocation().getBlockX() >> 4;
        int centerChunkZ = world.getSpawnLocation().getBlockZ() >> 4;
        int chunkRadius = Math.max(0, (scanRadius + 15) >> 4);

        for (int x = centerChunkX - chunkRadius; x <= centerChunkX + chunkRadius; x++) {
            for (int z = centerChunkZ - chunkRadius; z <= centerChunkZ + chunkRadius; z++) {
                if (world.isChunkLoaded(x, z)) {
                    pendingChunks.add(new ChunkRef(world.getName(), x, z));
                }
            }
        }
    }

    private void scanChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int localX = 0; localX < 16; localX++) {
            int x = baseX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = baseZ + localZ;
                for (int y = minY; y < maxY; y++) {
                    var block = world.getBlockAt(x, y, z);
                    blockReader.read(world, x, y, z, block.getType(), block.getBlockData())
                            .ifPresent(node -> nodes.put(node.position(), node));
                }
            }
        }
    }
}
