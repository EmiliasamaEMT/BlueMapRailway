package io.github.emiliasamaemt.bluemaprailway.scan;

import io.github.emiliasamaemt.bluemaprailway.cache.RailChunkCache;
import io.github.emiliasamaemt.bluemaprailway.PluginLog;
import io.github.emiliasamaemt.bluemaprailway.model.RailNode;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class RailScanner {

    private final Plugin plugin;
    private final PluginLog log;
    private final RailBlockReader blockReader;
    private final RailGraphBuilder graphBuilder;
    private final Queue<ChunkRef> pendingChunks;
    private final Map<RailPosition, RailNode> nodes;
    private RailChunkCache cache;
    private Set<String> enabledWorlds;
    private int scannedChunks;
    private int cachedChunks;
    private int cachedRails;
    private boolean active;

    public RailScanner(Plugin plugin, PluginLog log) {
        this.plugin = plugin;
        this.log = log;
        this.blockReader = new RailBlockReader();
        this.graphBuilder = new RailGraphBuilder();
        this.pendingChunks = new ArrayDeque<>();
        this.nodes = new HashMap<>();
        this.enabledWorlds = new HashSet<>();
    }

    public void begin() {
        prepareScan();

        ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        if (!loadEnabledWorlds(worldsSection)) {
            return;
        }

        for (String worldName : enabledWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                log.warning("Configured world does not exist, skipped: " + worldName);
                continue;
            }

            queueWorld(world, worldsSection.getInt(worldName + ".scan-radius", -1));
        }

        finishPreparingScan("铁路扫描任务已创建");
    }

    public void begin(Set<ChunkRef> chunkRefs) {
        prepareScan();

        ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        if (!loadEnabledWorlds(worldsSection)) {
            return;
        }

        for (ChunkRef chunkRef : chunkRefs) {
            if (!enabledWorlds.contains(chunkRef.worldName())) {
                continue;
            }

            World world = Bukkit.getWorld(chunkRef.worldName());
            if (world != null && world.isChunkLoaded(chunkRef.x(), chunkRef.z())) {
                pendingChunks.add(chunkRef);
            }
        }

        finishPreparingScan("铁路区块扫描任务已创建");
    }

    public boolean scanNextBatch(int chunksPerTick) {
        int scannedThisBatch = 0;

        while (scannedThisBatch < chunksPerTick && !pendingChunks.isEmpty()) {
            ChunkRef chunkRef = pendingChunks.poll();
            World world = Bukkit.getWorld(chunkRef.worldName());
            if (world != null && world.isChunkLoaded(chunkRef.x(), chunkRef.z())) {
                List<RailNode> chunkNodes = scanChunk(world.getChunkAt(chunkRef.x(), chunkRef.z()));
                cache.put(chunkRef, chunkNodes);
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
        cachedChunks = cache.chunkCount(enabledWorlds);
        cachedRails = cache.railCount(enabledWorlds);
        cache.save(plugin);
        var graphResult = graphBuilder.build(nodes, yOffset, RailLineFilter.fromConfig(plugin.getConfig()));
        return new RailScanResult(
                Map.copyOf(nodes),
                graphResult.components(),
                graphResult.lines(),
                scannedChunks,
                cachedChunks,
                cachedRails,
                graphResult.hiddenLineCount()
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

    private void prepareScan() {
        pendingChunks.clear();
        nodes.clear();
        enabledWorlds = new HashSet<>();
        scannedChunks = 0;
        cachedChunks = 0;
        cachedRails = 0;
        active = true;
        cache = RailChunkCache.load(plugin);
    }

    private boolean loadEnabledWorlds(ConfigurationSection worldsSection) {
        if (worldsSection == null) {
            active = false;
            return false;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            if (worldsSection.getBoolean(worldName + ".enabled", false)) {
                enabledWorlds.add(worldName);
            }
        }

        return true;
    }

    private void finishPreparingScan(String taskName) {
        cache.mergeInto(nodes, enabledWorlds);
        cachedChunks = cache.chunkCount(enabledWorlds);
        cachedRails = cache.railCount(enabledWorlds);

        log.info(taskName + ", pending chunks: " + pendingChunks.size() + ", cached chunks: " + cachedChunks + ", cached rails: " + cachedRails);
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

    private List<RailNode> scanChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        List<RailNode> chunkNodes = new ArrayList<>();

        removeChunkNodes(world.getName(), chunk.getX(), chunk.getZ());

        for (int localX = 0; localX < 16; localX++) {
            int x = baseX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = baseZ + localZ;
                for (int y = minY; y < maxY; y++) {
                    var block = world.getBlockAt(x, y, z);
                    blockReader.read(world, x, y, z, block.getType(), block.getBlockData())
                            .ifPresent(node -> {
                                nodes.put(node.position(), node);
                                chunkNodes.add(node);
                            });
                }
            }
        }

        return chunkNodes;
    }

    private void removeChunkNodes(String worldName, int chunkX, int chunkZ) {
        nodes.entrySet().removeIf(entry -> {
            RailPosition position = entry.getKey();
            return position.worldName().equals(worldName) &&
                    (position.x() >> 4) == chunkX &&
                    (position.z() >> 4) == chunkZ;
        });
    }
}
