package io.github.emiliasamaemt.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import io.github.emiliasamaemt.bluemaprailway.exporter.SvgRailExporter;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.render.BlueMapRailRenderer;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteRegistry;
import io.github.emiliasamaemt.bluemaprailway.scan.RailScanner;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Path;

public final class RailwayService {

    private final JavaPlugin plugin;
    private final BlueMapRailRenderer renderer;
    private final SvgRailExporter svgExporter;
    private RailRouteRegistry routeRegistry;
    private BlueMapAPI blueMapApi;
    private RailScanner scanner;
    private BukkitTask scanTask;
    private BukkitTask rescanTask;
    private int lastRailCount;
    private int lastLineCount;
    private int lastComponentCount;
    private int lastScannedChunks;
    private int lastHiddenLineCount;
    private int lastClassifiedLineCount;
    private String lastSvgPath = "尚未导出";
    private RailScanResult lastResult;

    public RailwayService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BlueMapRailRenderer(plugin);
        this.svgExporter = new SvgRailExporter(plugin);
        this.routeRegistry = RailRouteRegistry.load(plugin);
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
        routeRegistry = RailRouteRegistry.load(plugin);
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
                "。上次结果: " + lastScannedChunks + " 个区块，" + lastRailCount + " 个铁轨，" +
                lastComponentCount + " 个连通分量，" + lastLineCount + " 条线。";
    }

    public synchronized String debugStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append(status()).append('\n');
        builder.append("过滤隐藏线段: ").append(lastHiddenLineCount).append('\n');
        builder.append("已归类线段: ").append(lastClassifiedLineCount).append('\n');
        builder.append("routes.yml 线路数: ").append(routeRegistry.routeCount()).append('\n');
        builder.append("routes.yml 已绑定 component 数: ").append(routeRegistry.assignedComponentCount()).append('\n');
        builder.append("SVG 输出: ").append(lastSvgPath);

        if (lastResult != null && !lastResult.components().isEmpty()) {
            builder.append('\n').append("前 8 个 component:");
            lastResult.components().stream()
                    .limit(8)
                    .forEach(component -> appendComponent(builder, component));
        }

        return builder.toString();
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
        routeRegistry = RailRouteRegistry.load(plugin);
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

        var result = routeRegistry.apply(scanner.finish(plugin.getConfig().getDouble("markers.y-offset", 0.35)));
        lastScannedChunks = result.scannedChunks();
        lastRailCount = result.railCount();
        lastComponentCount = result.componentCount();
        lastLineCount = result.lineCount();
        lastHiddenLineCount = result.hiddenLineCount();
        lastClassifiedLineCount = result.classifiedLineCount();
        lastResult = result;

        renderer.render(blueMapApi, result);
        exportSvg(result);
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

    private void exportSvg(RailScanResult result) {
        if (!plugin.getConfig().getBoolean("export.svg.enabled", true)) {
            lastSvgPath = "已禁用";
            return;
        }

        try {
            Path outputFile = svgExporter.export(result);
            lastSvgPath = outputFile.toString();
            plugin.getLogger().info("铁路 SVG 已导出: " + lastSvgPath);
        } catch (IOException exception) {
            plugin.getLogger().warning("铁路 SVG 导出失败: " + exception.getMessage());
        }
    }

    private void appendComponent(StringBuilder builder, RailComponent component) {
        builder.append('\n')
                .append("- ").append(component.id())
                .append(" 点数=").append(component.pointCount())
                .append(" 长度=").append(Math.round(component.length() * 10.0) / 10.0)
                .append(" 范围=[")
                .append(component.minX()).append(',').append(component.minY()).append(',').append(component.minZ())
                .append(" -> ")
                .append(component.maxX()).append(',').append(component.maxY()).append(',').append(component.maxZ())
                .append(']');
    }
}
