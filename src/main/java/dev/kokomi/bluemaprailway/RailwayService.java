package dev.kokomi.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import dev.kokomi.bluemaprailway.render.BlueMapRailRenderer;
import dev.kokomi.bluemaprailway.scan.RailScanner;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RailwayService {

    private final JavaPlugin plugin;
    private final BlueMapRailRenderer renderer;
    private BlueMapAPI blueMapApi;
    private RailScanner scanner;
    private BukkitTask scanTask;
    private BukkitTask rescanTask;
    private int lastRailCount;
    private int lastLineCount;
    private int lastScannedChunks;

    public RailwayService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BlueMapRailRenderer(plugin);
    }

    public synchronized void start(BlueMapAPI api) {
        if (this.blueMapApi == api && scanTask != null) {
            return;
        }

        this.blueMapApi = api;
        plugin.getLogger().info("BlueMap API 已就绪，铁路覆盖层服务已启动。");
        queueFullRescan(0L);
    }

    public synchronized void stop() {
        if (blueMapApi != null) {
            plugin.getLogger().info("铁路覆盖层服务已停止。");
        }

        cancelTasks();
        blueMapApi = null;
        scanner = null;
    }

    public synchronized void reload() {
        requestFullRescan();
    }

    public synchronized void requestFullRescan() {
        int debounceTicks = plugin.getConfig().getInt("scanner.update-debounce-ticks", 40);
        queueFullRescan(Math.max(1, debounceTicks));
    }

    public synchronized String status() {
        String apiState = blueMapApi == null ? "等待 BlueMap" : "运行中";
        String scanState;
        if (scanner != null && scanner.isActive()) {
            scanState = "扫描中，已扫描 " + scanner.scannedChunkCount() + " 个区块，剩余 " + scanner.pendingChunkCount() + " 个区块";
        } else if (rescanTask != null) {
            scanState = "重扫已排队";
        } else {
            scanState = "空闲";
        }

        return "BlueMapRailway 状态: " + apiState + "，" + scanState +
                "。上次结果: " + lastScannedChunks + " 个区块，" + lastRailCount + " 个铁轨，" + lastLineCount + " 条线。";
    }

    private synchronized void queueFullRescan(long delayTicks) {
        if (blueMapApi == null) {
            return;
        }

        if (rescanTask != null) {
            rescanTask.cancel();
        }

        rescanTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (RailwayService.this) {
                rescanTask = null;
                beginScan();
            }
        }, delayTicks);
    }

    private synchronized void beginScan() {
        if (blueMapApi == null) {
            return;
        }

        if (scanTask != null) {
            scanTask.cancel();
        }

        scanner = new RailScanner(plugin);
        scanner.begin();

        int chunksPerTick = Math.max(1, plugin.getConfig().getInt("scanner.chunks-per-tick", 1));
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickScan(chunksPerTick), 1L, 1L);
    }

    private synchronized void tickScan(int chunksPerTick) {
        if (scanner == null || blueMapApi == null) {
            cancelScanTask();
            return;
        }

        boolean stillActive = scanner.scanNextBatch(chunksPerTick);
        if (stillActive) {
            return;
        }

        var result = scanner.finish(plugin.getConfig().getDouble("markers.y-offset", 0.35));
        lastScannedChunks = result.scannedChunks();
        lastRailCount = result.railCount();
        lastLineCount = result.lineCount();

        renderer.render(blueMapApi, result);
        plugin.getLogger().info("铁路扫描完成: " + lastScannedChunks + " 个区块，" +
                lastRailCount + " 个铁轨，" + lastLineCount + " 条线。");

        cancelScanTask();
    }

    private void cancelTasks() {
        if (rescanTask != null) {
            rescanTask.cancel();
            rescanTask = null;
        }

        cancelScanTask();
    }

    private void cancelScanTask() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }
}
