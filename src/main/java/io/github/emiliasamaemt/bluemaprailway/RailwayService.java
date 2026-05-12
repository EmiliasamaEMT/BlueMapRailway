package io.github.emiliasamaemt.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import io.github.emiliasamaemt.bluemaprailway.exporter.SvgRailExporter;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.render.BlueMapRailRenderer;
import io.github.emiliasamaemt.bluemaprailway.route.RailRoute;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteAnchor;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteBounds;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteRegistry;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import io.github.emiliasamaemt.bluemaprailway.scan.RailScanner;
import io.github.emiliasamaemt.bluemaprailway.station.RailStation;
import io.github.emiliasamaemt.bluemaprailway.station.RailStationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RailwayService {

    private final JavaPlugin plugin;
    private final BlueMapRailRenderer renderer;
    private final SvgRailExporter svgExporter;
    private RailRouteRegistry routeRegistry;
    private RailStationRegistry stationRegistry;
    private BlueMapAPI blueMapApi;
    private RailScanner scanner;
    private BukkitTask scanTask;
    private BukkitTask rescanTask;
    private BukkitTask chunkScanTask;
    private final Set<ChunkRef> pendingChunkScans;
    private int lastRailCount;
    private int lastLineCount;
    private int lastComponentCount;
    private int lastScannedChunks;
    private int lastCachedChunks;
    private int lastCachedRails;
    private int lastHiddenLineCount;
    private int lastClassifiedLineCount;
    private String lastSvgPath = "尚未导出";
    private RailScanResult lastResult;

    public RailwayService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BlueMapRailRenderer(plugin);
        this.svgExporter = new SvgRailExporter(plugin);
        this.routeRegistry = RailRouteRegistry.load(plugin);
        this.stationRegistry = RailStationRegistry.load(plugin);
        this.pendingChunkScans = new HashSet<>();
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
        stationRegistry = RailStationRegistry.load(plugin);
        requestFullRescan();
    }

    public synchronized void requestFullRescan() {
        int debounceTicks = plugin.getConfig().getInt("scanner.update-debounce-ticks", 40);
        queueFullRescan(Math.max(1, debounceTicks));
    }

    public synchronized void requestChunkRescan(ChunkRef chunkRef) {
        if (blueMapApi == null || !isChunkLoadScanningEnabled() || !isWorldEnabled(chunkRef.worldName())) {
            return;
        }

        pendingChunkScans.add(chunkRef);

        int debounceTicks = plugin.getConfig().getInt("cache.chunk-load-debounce-ticks", 100);
        queueChunkRescan(Math.max(1, debounceTicks));
    }

    public synchronized String status() {
        String apiState = blueMapApi == null ? "等待 BlueMap" : "运行中";
        String scanState;
        if (scanner != null && scanner.isActive()) {
            scanState = "扫描中，已扫描 " + scanner.scannedChunkCount() + " 个区块，剩余 " + scanner.pendingChunkCount() + " 个区块";
        } else if (rescanTask != null) {
            scanState = "重扫已排队";
        } else if (chunkScanTask != null) {
            scanState = "区块扫描已排队，待处理 " + pendingChunkScans.size() + " 个区块";
        } else {
            scanState = "空闲";
        }

        return "BlueMapRailway 状态: " + apiState + "，" + scanState +
                "。上次结果: " + lastScannedChunks + " 个区块，" + lastRailCount + " 个铁轨，" +
                lastComponentCount + " 个连通分量，" + lastLineCount + " 条线，缓存 " +
                lastCachedChunks + " 个区块。";
    }

    public synchronized String debugStatus() {
        StringBuilder builder = new StringBuilder();
        builder.append(status()).append('\n');
        builder.append("过滤隐藏线段: ").append(lastHiddenLineCount).append('\n');
        builder.append("缓存区块: ").append(lastCachedChunks).append('\n');
        builder.append("缓存铁轨: ").append(lastCachedRails).append('\n');
        builder.append("已归类线段: ").append(lastClassifiedLineCount).append('\n');
        builder.append("routes.yml 线路数: ").append(routeRegistry.routeCount()).append('\n');
        builder.append("routes.yml 已绑定 component 数: ").append(routeRegistry.assignedComponentCount()).append('\n');
        builder.append("stations.yml 站点数: ").append(stationRegistry.stationCount()).append('\n');
        builder.append("SVG 输出: ").append(lastSvgPath);

        if (lastResult != null && !lastResult.components().isEmpty()) {
            builder.append('\n').append("前 8 个 component:");
            lastResult.components().stream()
                    .limit(8)
                    .forEach(component -> appendComponent(builder, component));
        }

        return builder.toString();
    }

    public synchronized String routeList() {
        if (routeRegistry.routes().isEmpty()) {
            return "routes.yml 中还没有线路。可以使用 /railmap route create <id> <名称> 创建。";
        }

        StringBuilder builder = new StringBuilder("线路列表:");
        for (RailRoute route : routeRegistry.routes()) {
            builder.append('\n')
                    .append("- ").append(route.id())
                    .append(" / ").append(route.name())
                    .append(" / components=").append(route.componentIds().size());
        }

        return builder.toString();
    }

    public synchronized String routeInfo(String routeId) {
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "线路不存在: " + routeId;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("线路 ").append(route.id()).append('\n');
        builder.append("名称: ").append(route.name()).append('\n');
        builder.append("颜色: ").append(route.color() == null ? "默认" : route.color()).append('\n');
        builder.append("线宽: ").append(route.lineWidth() > 0 ? route.lineWidth() : "默认").append('\n');
        builder.append("自动延续: ").append(route.autoMatch() ? "开启" : "关闭").append('\n');
        builder.append("锚点数: ").append(route.anchors().size()).append('\n');
        builder.append("绑定 components:");
        if (route.componentIds().isEmpty()) {
            builder.append(" 无");
        } else {
            route.componentIds().forEach(componentId -> builder.append('\n').append("- ").append(componentId));
        }

        return builder.toString();
    }

    public synchronized String routeCreate(String routeId, String name) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        if (routes.isConfigurationSection(routeId)) {
            return "线路已存在: " + routeId;
        }

        routes.set(routeId + ".name", name);
        routes.set(routeId + ".components", List.of());
        routes.set(routeId + ".auto-match", true);
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已创建线路: " + routeId + " / " + name;
    }

    public synchronized String routeRename(String routeId, String name) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        ensureRoute(routes, routeId);
        routes.set(routeId + ".name", name);
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已重命名线路: " + routeId + " / " + name;
    }

    public synchronized String routeColor(String routeId, String color) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        if (!color.matches("#[0-9a-fA-F]{6}")) {
            return "颜色必须是 #RRGGBB 格式。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        ensureRoute(routes, routeId);
        routes.set(routeId + ".color", color);
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已设置线路颜色: " + routeId + " -> " + color;
    }

    public synchronized String routeWidth(String routeId, int width) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        if (width < 1 || width > 64) {
            return "线宽必须在 1 到 64 之间。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        ensureRoute(routes, routeId);
        routes.set(routeId + ".line-width", width);
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已设置线路线宽: " + routeId + " -> " + width;
    }

    public synchronized String routeAssignNearest(Player player, String routeId, double radius) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        if (radius <= 0) {
            return "半径必须大于 0。";
        }

        if (lastResult == null || lastResult.components().isEmpty()) {
            return "还没有可用扫描结果，请先等待扫描完成或执行 /railmap rescan。";
        }

        NearestComponent nearest = nearestComponent(player.getLocation(), radius);
        if (nearest == null) {
            return "半径 " + radius + " 格内没有找到铁路 component。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        ensureRoute(routes, routeId);

        String path = routeId + ".components";
        List<String> componentIds = new ArrayList<>(routes.getStringList(path));
        if (!componentIds.contains(nearest.component().id())) {
            componentIds.add(nearest.component().id());
        }

        routes.set(path, componentIds);
        appendAnchor(routes, routeId, RailRouteAnchor.of(nearest.position()));
        writeBounds(routes, routeId, RailRouteBounds.of(nearest.component()));
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已将最近 component 绑定到线路 " + routeId + ": " + nearest.component().id();
    }

    public synchronized String routeAnchorNearest(Player player, String routeId, double radius) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        if (radius <= 0) {
            return "半径必须大于 0。";
        }

        if (lastResult == null || lastResult.components().isEmpty()) {
            return "还没有可用扫描结果，请先等待扫描完成或执行 /railmap rescan。";
        }

        NearestComponent nearest = nearestComponent(player.getLocation(), radius);
        if (nearest == null) {
            return "半径 " + radius + " 格内没有找到铁路 component。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        ensureRoute(routes, routeId);
        appendAnchor(routes, routeId, RailRouteAnchor.of(nearest.position()));
        writeBounds(routes, routeId, RailRouteBounds.of(nearest.component()));
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已为线路 " + routeId + " 增加自动延续锚点: " +
                nearest.position().worldName() + " " + nearest.position().x() + "," +
                nearest.position().y() + "," + nearest.position().z();
    }

    public synchronized String routeAutoMatch(String routeId, boolean enabled) {
        if (!isValidRouteId(routeId)) {
            return "线路 ID 只能包含字母、数字、下划线和短横线。";
        }

        YamlConfiguration configuration = loadRoutesConfiguration();
        ConfigurationSection routes = routesSection(configuration);
        ensureRoute(routes, routeId);
        routes.set(routeId + ".auto-match", enabled);
        saveRoutesConfiguration(configuration);
        reloadRoutesAndRescan();
        return "已" + (enabled ? "开启" : "关闭") + "线路自动延续: " + routeId;
    }

    public synchronized String routeStatus(String routeId) {
        return routeRegistry.status(lastResult, routeId);
    }

    public synchronized String stationList() {
        if (stationRegistry.stations().isEmpty()) {
            return "stations.yml 中还没有站点。可以使用 /railmap station add <id> <名称> [半径] 创建。";
        }

        StringBuilder builder = new StringBuilder("站点列表:");
        for (RailStation station : stationRegistry.stations()) {
            builder.append('\n')
                    .append("- ").append(station.id())
                    .append(" / ").append(station.name())
                    .append(" / ").append(station.worldName())
                    .append(" / area=[")
                    .append(station.minX()).append(',').append(station.minY()).append(',').append(station.minZ())
                    .append(" -> ")
                    .append(station.maxX()).append(',').append(station.maxY()).append(',').append(station.maxZ())
                    .append(']');
        }

        return builder.toString();
    }

    public synchronized String stationInfo(String stationId) {
        RailStation station = station(stationId);
        if (station == null) {
            return "站点不存在: " + stationId;
        }

        return "站点 " + station.id() + '\n' +
                "名称: " + station.name() + '\n' +
                "世界: " + station.worldName() + '\n' +
                "区域: [" + station.minX() + ',' + station.minY() + ',' + station.minZ() + "] -> [" +
                station.maxX() + ',' + station.maxY() + ',' + station.maxZ() + "]";
    }

    public synchronized String stationAddHere(Player player, String stationId, String name, double radius) {
        if (!isValidRouteId(stationId)) {
            return "站点 ID 只能包含字母、数字、下划线和短横线。";
        }

        if (radius <= 0) {
            return "半径必须大于 0。";
        }

        if (player.getWorld() == null) {
            return "无法读取玩家所在世界。";
        }

        YamlConfiguration configuration = loadStationsConfiguration();
        ConfigurationSection stations = stationsSection(configuration);
        if (stations.isConfigurationSection(stationId)) {
            return "站点已存在: " + stationId + "。可以使用 /railmap station set-area-here <id> [半径] 更新区域。";
        }

        stations.set(stationId + ".name", name);
        stations.set(stationId + ".world", player.getWorld().getName());
        stations.set(stationId + ".area.type", "box");
        writeStationArea(stations, stationId, player.getLocation(), radius);
        saveStationsConfiguration(configuration);
        reloadStationsAndRescan();
        return "已创建站点: " + stationId + " / " + name;
    }

    public synchronized String stationSetAreaHere(Player player, String stationId, double radius) {
        if (!isValidRouteId(stationId)) {
            return "站点 ID 只能包含字母、数字、下划线和短横线。";
        }

        if (radius <= 0) {
            return "半径必须大于 0。";
        }

        if (player.getWorld() == null) {
            return "无法读取玩家所在世界。";
        }

        YamlConfiguration configuration = loadStationsConfiguration();
        ConfigurationSection stations = stationsSection(configuration);
        if (!stations.isConfigurationSection(stationId)) {
            return "站点不存在: " + stationId;
        }

        stations.set(stationId + ".world", player.getWorld().getName());
        stations.set(stationId + ".area.type", "box");
        writeStationArea(stations, stationId, player.getLocation(), radius);
        saveStationsConfiguration(configuration);
        reloadStationsAndRescan();
        return "已更新站点区域: " + stationId;
    }

    public synchronized String stationRemove(String stationId) {
        if (!isValidRouteId(stationId)) {
            return "站点 ID 只能包含字母、数字、下划线和短横线。";
        }

        YamlConfiguration configuration = loadStationsConfiguration();
        ConfigurationSection stations = stationsSection(configuration);
        if (!stations.isConfigurationSection(stationId)) {
            return "站点不存在: " + stationId;
        }

        stations.set(stationId, null);
        saveStationsConfiguration(configuration);
        reloadStationsAndRescan();
        return "已删除站点: " + stationId;
    }

    private synchronized void queueFullRescan(long delayTicks) {
        if (blueMapApi == null) {
            return;
        }

        cancelChunkScanTask();
        pendingChunkScans.clear();

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

    private synchronized void queueChunkRescan(long delayTicks) {
        if (blueMapApi == null || pendingChunkScans.isEmpty()) {
            return;
        }

        if (chunkScanTask != null) {
            return;
        }

        chunkScanTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (RailwayService.this) {
                chunkScanTask = null;
                beginChunkScan();
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
        stationRegistry = RailStationRegistry.load(plugin);
        scanner.begin();

        int chunksPerTick = Math.max(1, plugin.getConfig().getInt("scanner.chunks-per-tick", 1));
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickScan(chunksPerTick), 1L, 1L);
    }

    private synchronized void beginChunkScan() {
        if (blueMapApi == null || pendingChunkScans.isEmpty()) {
            return;
        }

        if (scanTask != null) {
            int debounceTicks = plugin.getConfig().getInt("cache.chunk-load-debounce-ticks", 100);
            queueChunkRescan(Math.max(1, debounceTicks));
            return;
        }

        Set<ChunkRef> chunkRefs = Set.copyOf(pendingChunkScans);
        pendingChunkScans.clear();

        scanner = new RailScanner(plugin);
        routeRegistry = RailRouteRegistry.load(plugin);
        stationRegistry = RailStationRegistry.load(plugin);
        scanner.begin(chunkRefs);

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
        lastCachedChunks = result.cachedChunks();
        lastCachedRails = result.cachedRails();
        lastRailCount = result.railCount();
        lastComponentCount = result.componentCount();
        lastLineCount = result.lineCount();
        lastHiddenLineCount = result.hiddenLineCount();
        lastClassifiedLineCount = result.classifiedLineCount();
        lastResult = result;

        renderer.render(blueMapApi, result, stationRegistry.stations());
        exportSvg(result);
        plugin.getLogger().info("铁路扫描完成: " + lastScannedChunks + " 个区块，" +
                lastRailCount + " 个铁轨，" + lastLineCount + " 条线。");

        cancelScanTask();
        if (!pendingChunkScans.isEmpty()) {
            queueChunkRescan(1L);
        }
    }

    private void cancelTasks() {
        if (rescanTask != null) {
            rescanTask.cancel();
            rescanTask = null;
        }

        cancelChunkScanTask();
        pendingChunkScans.clear();
        cancelScanTask();
    }

    private void cancelChunkScanTask() {
        if (chunkScanTask != null) {
            chunkScanTask.cancel();
            chunkScanTask = null;
        }
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
            Path outputFile = svgExporter.export(result, stationRegistry.stations());
            lastSvgPath = outputFile.toString();
            plugin.getLogger().info("铁路 SVG 已导出: " + lastSvgPath);
        } catch (IOException exception) {
            plugin.getLogger().warning("铁路 SVG 导出失败: " + exception.getMessage());
        }
    }

    private NearestComponent nearestComponent(Location location, double radius) {
        if (location.getWorld() == null || lastResult == null) {
            return null;
        }

        String worldName = location.getWorld().getName();
        double radiusSquared = radius * radius;
        RailComponent nearest = null;
        RailPosition nearestPosition = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (RailComponent component : lastResult.components()) {
            if (!component.worldName().equals(worldName)) {
                continue;
            }

            for (RailPosition position : component.positions()) {
                double dx = position.x() + 0.5 - location.getX();
                double dy = position.y() + 0.5 - location.getY();
                double dz = position.z() + 0.5 - location.getZ();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= radiusSquared && distance < nearestDistance) {
                    nearest = component;
                    nearestPosition = position;
                    nearestDistance = distance;
                }
            }
        }

        if (nearest == null || nearestPosition == null) {
            return null;
        }

        return new NearestComponent(nearest, nearestPosition);
    }

    private YamlConfiguration loadRoutesConfiguration() {
        return YamlConfiguration.loadConfiguration(routesFile());
    }

    private YamlConfiguration loadStationsConfiguration() {
        return YamlConfiguration.loadConfiguration(stationsFile());
    }

    private ConfigurationSection routesSection(YamlConfiguration configuration) {
        ConfigurationSection routes = configuration.getConfigurationSection("routes");
        if (routes == null) {
            routes = configuration.createSection("routes");
        }

        return routes;
    }

    private ConfigurationSection stationsSection(YamlConfiguration configuration) {
        ConfigurationSection stations = configuration.getConfigurationSection("stations");
        if (stations == null) {
            stations = configuration.createSection("stations");
        }

        return stations;
    }

    private void ensureRoute(ConfigurationSection routes, String routeId) {
        if (!routes.isConfigurationSection(routeId)) {
            routes.set(routeId + ".name", routeId);
            routes.set(routeId + ".components", List.of());
            routes.set(routeId + ".auto-match", true);
        }
    }

    private void appendAnchor(ConfigurationSection routes, String routeId, RailRouteAnchor anchor) {
        String path = routeId + ".anchors";
        List<Map<String, Object>> anchors = new ArrayList<>();
        for (Map<?, ?> existing : routes.getMapList(path)) {
            anchors.add(new LinkedHashMap<>(stringKeyMap(existing)));
        }

        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("world", anchor.worldName());
        serialized.put("x", anchor.x());
        serialized.put("y", anchor.y());
        serialized.put("z", anchor.z());
        if (!anchors.contains(serialized)) {
            anchors.add(serialized);
        }

        routes.set(path, anchors);
    }

    private void writeBounds(ConfigurationSection routes, String routeId, RailRouteBounds bounds) {
        String path = routeId + ".bounds.";
        routes.set(path + "world", bounds.worldName());
        routes.set(path + "min", List.of(bounds.minX(), bounds.minY(), bounds.minZ()));
        routes.set(path + "max", List.of(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }

        return result;
    }

    private void saveRoutesConfiguration(YamlConfiguration configuration) {
        try {
            configuration.save(routesFile());
        } catch (IOException exception) {
            throw new IllegalStateException("保存 routes.yml 失败: " + exception.getMessage(), exception);
        }
    }

    private void saveStationsConfiguration(YamlConfiguration configuration) {
        try {
            configuration.save(stationsFile());
        } catch (IOException exception) {
            throw new IllegalStateException("保存 stations.yml 失败: " + exception.getMessage(), exception);
        }
    }

    private File routesFile() {
        return new File(plugin.getDataFolder(), "routes.yml");
    }

    private File stationsFile() {
        return new File(plugin.getDataFolder(), "stations.yml");
    }

    private void reloadRoutesAndRescan() {
        routeRegistry = RailRouteRegistry.load(plugin);
        requestFullRescan();
    }

    private void reloadStationsAndRescan() {
        stationRegistry = RailStationRegistry.load(plugin);
        requestFullRescan();
    }

    private RailStation station(String stationId) {
        for (RailStation station : stationRegistry.stations()) {
            if (station.id().equals(stationId)) {
                return station;
            }
        }

        return null;
    }

    private void writeStationArea(ConfigurationSection stations, String stationId, Location location, double radius) {
        int horizontalRadius = (int) Math.ceil(radius);
        int yRadius = Math.max(1, plugin.getConfig().getInt("stations.default-y-radius", 6));
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        stations.set(stationId + ".area.min", List.of(x - horizontalRadius, y - yRadius, z - horizontalRadius));
        stations.set(stationId + ".area.max", List.of(x + horizontalRadius, y + yRadius, z + horizontalRadius));
    }

    private boolean isChunkLoadScanningEnabled() {
        return plugin.getConfig().getBoolean("cache.enabled", true) &&
                plugin.getConfig().getBoolean("cache.scan-newly-loaded-chunks", true);
    }

    private boolean isWorldEnabled(String worldName) {
        ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        return worldsSection != null && worldsSection.getBoolean(worldName + ".enabled", false);
    }

    private boolean isValidRouteId(String routeId) {
        return routeId.matches("[A-Za-z0-9_-]+");
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

    private record NearestComponent(RailComponent component, RailPosition position) {
    }
}
