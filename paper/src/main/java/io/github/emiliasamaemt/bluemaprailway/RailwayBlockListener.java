package io.github.emiliasamaemt.bluemaprailway;

import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RailwayBlockListener implements Listener {

    private final RailwayService railwayService;

    public RailwayBlockListener(RailwayService railwayService) {
        this.railwayService = railwayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isRail(event.getBlockPlaced())) {
            railwayService.requestNeighborChunkRescans(affectedChunks(event.getBlockPlaced()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isRail(event.getBlock())) {
            railwayService.requestNeighborChunkRescans(affectedChunks(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (railwayService.rescanOnPhysics() && isRail(event.getBlock())) {
            railwayService.requestNeighborChunkRescans(affectedChunks(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (railwayService.rescanOnRedstone() && isRail(event.getBlock())) {
            railwayService.requestNeighborChunkRescans(affectedChunks(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        railwayService.requestChunkRescan(new ChunkRef(
                event.getWorld().getName(),
                event.getChunk().getX(),
                event.getChunk().getZ()
        ));
    }

    private boolean isRail(Block block) {
        return switch (block.getType()) {
            case RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL -> true;
            default -> block.getBlockData() instanceof Rail;
        };
    }

    private Set<ChunkRef> affectedChunks(Block block) {
        int radius = railwayService.blockUpdateNeighborRadius();
        int chunkX = block.getChunk().getX();
        int chunkZ = block.getChunk().getZ();
        String worldName = block.getWorld().getName();
        Set<ChunkRef> chunkRefs = new LinkedHashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            int zRadius = radius - Math.abs(dx);
            for (int dz = -zRadius; dz <= zRadius; dz++) {
                chunkRefs.add(new ChunkRef(worldName, chunkX + dx, chunkZ + dz));
            }
        }

        return chunkRefs;
    }
}
