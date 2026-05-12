package io.github.emiliasamaemt.bluemaprailway;

import io.github.emiliasamaemt.bluemaprailway.model.RailType;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

public final class RailwayBlockListener implements Listener {

    private final RailwayService railwayService;

    public RailwayBlockListener(RailwayService railwayService) {
        this.railwayService = railwayService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isRail(event.getBlockPlaced())) {
            railwayService.requestFullRescan();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isRail(event.getBlock())) {
            railwayService.requestFullRescan();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isRail(event.getBlock())) {
            railwayService.requestFullRescan();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (isRail(event.getBlock())) {
            railwayService.requestFullRescan();
        }
    }

    private boolean isRail(Block block) {
        return RailType.fromMaterial(block.getType()).isPresent() || block.getBlockData() instanceof Rail;
    }
}
