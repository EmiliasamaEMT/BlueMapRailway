package io.github.emiliasamaemt.bluemaprailway.fabric;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import io.github.emiliasamaemt.bluemaprailway.edit.RailEditHideRule;
import io.github.emiliasamaemt.bluemaprailway.edit.RailEditMask;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.route.RailRoute;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteAnchor;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteBounds;
import io.github.emiliasamaemt.bluemaprailway.station.RailStation;
import io.github.emiliasamaemt.bluemaprailway.web.SimpleJson;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class FabricRailwayService {

    private static final long RESCAN_COOLDOWN_MILLIS = 2_000L;

    private final FabricRailwayLogger log;
    private final FabricSvgRailExporter svgExporter = new FabricSvgRailExporter();
    private final FabricAdminWebServer adminWebServer;
    private FabricRailwayConfig config;
    private FabricEditRegistry editRegistry;
    private FabricRouteRegistry routeRegistry;
    private FabricStationRegistry stationRegistry;
    private FabricRailScanner scanner;
    private FabricBlueMapRailRenderer renderer;
    private MinecraftServer server;
    private BlueMapAPI blueMapApi;
    private boolean rescanQueued;
    private boolean initialScanCompleted;
    private long lastRescanAt;
    private RailScanResult lastBaseResult;
    private RailScanResult lastResult;

    public FabricRailwayService(FabricRailwayLogger log) {
        this.log = log;
        this.adminWebServer = new FabricAdminWebServer(this, log);
        reloadConfig();
    }

    public synchronized void reloadConfig() {
        this.config = FabricRailwayConfigLoader.load(log);
        this.editRegistry = FabricEditRegistry.load(log);
        this.routeRegistry = FabricRouteRegistry.load(log);
        this.stationRegistry = FabricStationRegistry.load(log);
        this.scanner = new FabricRailScanner(config, log);
        this.renderer = new FabricBlueMapRailRenderer(config);
        refreshCurrentResult();
        restartAdminWebServer();
    }

    public synchronized FabricRailwayConfig config() {
        return config;
    }

    public synchronized void setServer(MinecraftServer server) {
        this.server = server;
    }

    public synchronized void clearServer() {
        this.server = null;
        this.initialScanCompleted = false;
        this.lastBaseResult = null;
        this.lastResult = null;
        adminWebServer.stop();
    }

    public synchronized void startBlueMap(BlueMapAPI api) {
        this.blueMapApi = api;
        log.info("BlueMap API is ready on Fabric, starting railway scan.");
        rescan();
    }

    public synchronized void stopBlueMap() {
        this.blueMapApi = null;
        this.initialScanCompleted = false;
    }

    public synchronized boolean isWorldEnabled(String worldId) {
        FabricRailwayConfig.FabricWorldConfig world = config.worlds().get(worldId);
        return world != null && world.enabled();
    }

    public synchronized boolean chunkLoadRescanEnabled() {
        return config.chunkLoadRescan();
    }

    public synchronized void requestChunkLoadRescan() {
        if (!initialScanCompleted) {
            return;
        }
        rescan();
    }

    public synchronized void requestFullRescan() {
        rescan();
    }

    public synchronized void rescan() {
        if (server == null || blueMapApi == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (rescanQueued || now - lastRescanAt < RESCAN_COOLDOWN_MILLIS) {
            return;
        }

        rescanQueued = true;
        server.execute(this::rescanOnServerThread);
    }

    private synchronized void rescanOnServerThread() {
        rescanQueued = false;
        if (server == null || blueMapApi == null) {
            return;
        }

        lastRescanAt = System.currentTimeMillis();
        lastBaseResult = scanner.scan(server);
        RailScanResult result = applyRegistries(lastBaseResult);
        lastResult = result;
        renderer.render(blueMapApi, result, stationRegistry.stations());
        exportSvg(result);
        initialScanCompleted = true;
        log.info("Fabric railway scan completed: " + result.scannedChunks() + " chunks, "
                + result.railCount() + " rails, " + result.lineCount() + " lines.");
    }

    public synchronized String webStateJson(boolean includeAdminData) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"ok\":true");
        json.append(",\"admin\":").append(includeAdminData);
        appendWebBackground(json);
        appendWebBounds(json, lastResult);
        if (includeAdminData) {
            appendWebMasks(json);
            appendWebHiddenLines(json);
        } else {
            json.append(",\"masks\":[]");
            json.append(",\"hiddenLines\":[]");
        }
        appendWebRoutes(json);
        appendWebStations(json);
        appendWebComponents(json, lastResult);
        appendWebLines(json, lastResult);
        json.append('}');
        return json.toString();
    }

    public synchronized String webSaveRoute(Map<String, Object> request) {
        String routeId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(routeId)) {
            return errorJson("线路 ID 只能包含字母、数字、下划线和短横线");
        }

        String name = SimpleJson.text(request, "name", routeId).trim();
        if (name.isBlank()) {
            name = routeId;
        }

        String color = SimpleJson.text(request, "color", "").trim();
        if (!color.isBlank() && !color.matches("#[0-9a-fA-F]{6}")) {
            return errorJson("颜色必须是 #RRGGBB 格式");
        }

        int lineWidth = SimpleJson.integer(request, "lineWidth", -1);
        boolean autoMatch = SimpleJson.bool(request, "autoMatch", true);
        List<String> componentIds = SimpleJson.stringList(request, "componentIds").stream()
                .filter(componentId -> component(componentId) != null)
                .distinct()
                .toList();

        RailRoute existing = routeRegistry.route(routeId);
        List<RailRouteAnchor> anchors = existing == null ? List.of() : existing.anchors();
        RailRouteBounds bounds = existing == null ? null : existing.bounds();
        if (!componentIds.isEmpty()) {
            anchors = routeAnchors(componentIds);
            bounds = routeBounds(componentIds);
        }

        RailRoute route = new RailRoute(
                routeId,
                name,
                color.isBlank() ? null : color,
                lineWidth > 0 ? lineWidth : -1,
                new LinkedHashSet<>(componentIds),
                anchors,
                bounds,
                autoMatch
        );
        routeRegistry.saveRoute(route, log);
        reloadRoutesAndRescan();
        return okJson();
    }

    public synchronized String webDeleteRoute(Map<String, Object> request) {
        String routeId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(routeId)) {
            return errorJson("线路 ID 只能包含字母、数字、下划线和短横线");
        }
        if (!routeRegistry.deleteRoute(routeId, log)) {
            return errorJson("线路不存在");
        }
        reloadRoutesAndRescan();
        return okJson();
    }

    public synchronized String webSaveStation(Map<String, Object> request) {
        String stationId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(stationId)) {
            return errorJson("站点 ID 只能包含字母、数字、下划线和短横线");
        }

        String name = SimpleJson.text(request, "name", stationId).trim();
        if (name.isBlank()) {
            name = stationId;
        }

        String world = SimpleJson.text(request, "world", firstConfiguredWorld()).trim();
        int minX = SimpleJson.integer(request, "minX", 0);
        int minY = SimpleJson.integer(request, "minY", 0);
        int minZ = SimpleJson.integer(request, "minZ", 0);
        int maxX = SimpleJson.integer(request, "maxX", minX);
        int maxY = SimpleJson.integer(request, "maxY", minY);
        int maxZ = SimpleJson.integer(request, "maxZ", minZ);

        RailStation station = new RailStation(
                stationId,
                name,
                world,
                Math.min(minX, maxX),
                Math.min(minY, maxY),
                Math.min(minZ, maxZ),
                Math.max(minX, maxX),
                Math.max(minY, maxY),
                Math.max(minZ, maxZ)
        );
        stationRegistry.saveStation(station, log);
        reloadStationsAndRescan();
        return okJson();
    }

    public synchronized String webDeleteStation(Map<String, Object> request) {
        String stationId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(stationId)) {
            return errorJson("站点 ID 只能包含字母、数字、下划线和短横线");
        }
        if (!stationRegistry.deleteStation(stationId, log)) {
            return errorJson("站点不存在");
        }
        reloadStationsAndRescan();
        return okJson();
    }

    public synchronized String webSaveMask(Map<String, Object> request) {
        String maskId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(maskId)) {
            return errorJson("裁切规则 ID 只能包含字母、数字、下划线和短横线");
        }

        String name = SimpleJson.text(request, "name", maskId).trim();
        if (name.isBlank()) {
            name = maskId;
        }

        String world = SimpleJson.text(request, "world", firstConfiguredWorld()).trim();
        RailEditMask mask = new RailEditMask(
                maskId,
                name,
                world,
                SimpleJson.bool(request, "enabled", true),
                SimpleJson.integer(request, "minX", 0),
                SimpleJson.integer(request, "minY", 0),
                SimpleJson.integer(request, "minZ", 0),
                SimpleJson.integer(request, "maxX", 0),
                SimpleJson.integer(request, "maxY", 0),
                SimpleJson.integer(request, "maxZ", 0)
        );
        editRegistry.saveMask(mask, log);
        reloadEditsAndRescan();
        return okJson();
    }

    public synchronized String webDeleteMask(Map<String, Object> request) {
        String maskId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(maskId)) {
            return errorJson("裁切规则 ID 只能包含字母、数字、下划线和短横线");
        }
        if (!editRegistry.deleteMask(maskId, log)) {
            return errorJson("裁切规则不存在");
        }
        reloadEditsAndRescan();
        return okJson();
    }

    public synchronized String webSaveHiddenLine(Map<String, Object> request) {
        String ruleId = SimpleJson.text(request, "id", "").trim();
        if (ruleId.isBlank()) {
            ruleId = "hide-" + System.currentTimeMillis();
        }
        if (!isValidRouteId(ruleId)) {
            return errorJson("隐藏规则 ID 只能包含字母、数字、下划线和短横线");
        }

        String name = SimpleJson.text(request, "name", ruleId).trim();
        if (name.isBlank()) {
            name = ruleId;
        }

        List<String> routeIds = SimpleJson.stringList(request, "routeIds").stream()
                .filter(routeId -> routeRegistry.route(routeId) != null)
                .distinct()
                .toList();
        List<String> componentIds = SimpleJson.stringList(request, "componentIds").stream()
                .filter(componentId -> component(componentId) != null || routeOwnsComponent(componentId))
                .distinct()
                .toList();
        if (routeIds.isEmpty() && componentIds.isEmpty()) {
            return errorJson("至少需要指定一条线路或一个 component");
        }

        RailEditHideRule rule = new RailEditHideRule(
                ruleId,
                name,
                SimpleJson.bool(request, "enabled", true),
                Set.copyOf(routeIds),
                Set.copyOf(componentIds)
        );
        editRegistry.saveHiddenLine(rule, log);
        reloadEditsAndRescan();
        return okJson();
    }

    public synchronized String webDeleteHiddenLine(Map<String, Object> request) {
        String ruleId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(ruleId)) {
            return errorJson("隐藏规则 ID 只能包含字母、数字、下划线和短横线");
        }
        if (!editRegistry.deleteHiddenLine(ruleId, log)) {
            return errorJson("隐藏规则不存在");
        }
        reloadEditsAndRescan();
        return okJson();
    }

    private void restartAdminWebServer() {
        adminWebServer.stop();
        if (server != null) {
            adminWebServer.start();
        }
    }

    private void exportSvg(RailScanResult result) {
        try {
            Path path = svgExporter.export(result, stationRegistry.stations());
            log.info("Fabric SVG exported: " + path);
        } catch (IOException exception) {
            log.warning("Failed to export Fabric SVG: " + exception.getMessage());
        }
    }

    private RailScanResult applyRegistries(RailScanResult result) {
        return editRegistry.apply(routeRegistry.apply(result));
    }

    private void reloadRoutesAndRescan() {
        routeRegistry = FabricRouteRegistry.load(log);
        refreshCurrentResult();
        requestFullRescan();
    }

    private void reloadEditsAndRescan() {
        editRegistry = FabricEditRegistry.load(log);
        refreshCurrentResult();
        requestFullRescan();
    }

    private void reloadStationsAndRescan() {
        stationRegistry = FabricStationRegistry.load(log);
        refreshCurrentResult();
        requestFullRescan();
    }

    private void refreshCurrentResult() {
        if (lastBaseResult == null) {
            return;
        }
        lastResult = applyRegistries(lastBaseResult);
        if (blueMapApi != null) {
            renderer.render(blueMapApi, lastResult, stationRegistry.stations());
        }
        exportSvg(lastResult);
    }

    private String okJson() {
        return "{\"ok\":true}";
    }

    private String errorJson(String message) {
        return "{\"ok\":false,\"error\":" + SimpleJson.string(message) + "}";
    }

    private boolean routeOwnsComponent(String componentId) {
        for (RailRoute route : routeRegistry.routes()) {
            if (route.componentIds().contains(componentId)) {
                return true;
            }
        }
        return false;
    }

    private RailComponent component(String componentId) {
        if (lastResult == null) {
            return null;
        }
        for (RailComponent component : lastResult.components()) {
            if (component.id().equals(componentId)) {
                return component;
            }
        }
        return null;
    }

    private List<RailRouteAnchor> routeAnchors(List<String> componentIds) {
        List<RailRouteAnchor> anchors = new ArrayList<>();
        for (String componentId : componentIds) {
            RailComponent component = component(componentId);
            if (component == null) {
                continue;
            }
            RailRouteAnchor anchor = representativeAnchor(component);
            if (!anchors.contains(anchor)) {
                anchors.add(anchor);
            }
        }
        return List.copyOf(anchors);
    }

    private RailRouteBounds routeBounds(List<String> componentIds) {
        List<RailComponent> components = componentIds.stream()
                .map(this::component)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (components.isEmpty()) {
            return null;
        }
        String worldName = components.getFirst().worldName();
        int minX = components.stream().mapToInt(RailComponent::minX).min().orElse(0);
        int minY = components.stream().mapToInt(RailComponent::minY).min().orElse(0);
        int minZ = components.stream().mapToInt(RailComponent::minZ).min().orElse(0);
        int maxX = components.stream().mapToInt(RailComponent::maxX).max().orElse(0);
        int maxY = components.stream().mapToInt(RailComponent::maxY).max().orElse(0);
        int maxZ = components.stream().mapToInt(RailComponent::maxZ).max().orElse(0);
        return new RailRouteBounds(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private RailRouteAnchor representativeAnchor(RailComponent component) {
        if (component.positions().isEmpty()) {
            return new RailRouteAnchor(component.worldName(), component.minX(), component.minY(), component.minZ());
        }
        RailPosition position = component.positions().get(component.positions().size() / 2);
        return RailRouteAnchor.of(position);
    }

    private void appendWebBackground(StringBuilder json) {
        FabricRailwayConfig.FabricAdminWebConfig adminWeb = config.adminWeb();
        Path backgroundFile = FabricRailwayConfigLoader.dataDirectory().resolve(adminWeb.backgroundImage());
        double pixelsPerBlock = java.nio.file.Files.isRegularFile(backgroundFile)
                ? adminWeb.backgroundPixelsPerBlock()
                : 1.0;

        json.append(",\"background\":{");
        json.append("\"world\":").append(SimpleJson.string(adminWeb.backgroundWorld().isBlank() ? firstConfiguredWorld() : adminWeb.backgroundWorld())).append(',');
        json.append("\"centerX\":").append(adminWeb.backgroundCenterX()).append(',');
        json.append("\"centerZ\":").append(adminWeb.backgroundCenterZ()).append(',');
        json.append("\"pixelsPerBlock\":").append(pixelsPerBlock).append(',');
        json.append("\"imageUrl\":\"/background.png\"");
        json.append('}');
    }

    private void appendWebBounds(StringBuilder json, RailScanResult result) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        if (result != null) {
            for (RailLine line : result.lines()) {
                for (Vector3d point : line.points()) {
                    minX = Math.min(minX, point.getX());
                    minZ = Math.min(minZ, point.getZ());
                    maxX = Math.max(maxX, point.getX());
                    maxZ = Math.max(maxZ, point.getZ());
                }
            }
        }

        for (RailStation station : stationRegistry.stations()) {
            minX = Math.min(minX, station.minX());
            minZ = Math.min(minZ, station.minZ());
            maxX = Math.max(maxX, station.maxX());
            maxZ = Math.max(maxZ, station.maxZ());
        }

        for (RailEditMask mask : editRegistry.masks()) {
            minX = Math.min(minX, mask.minX());
            minZ = Math.min(minZ, mask.minZ());
            maxX = Math.max(maxX, mask.maxX());
            maxZ = Math.max(maxZ, mask.maxZ());
        }

        if (!Double.isFinite(minX)) {
            minX = -128;
            minZ = -128;
            maxX = 128;
            maxZ = 128;
        }

        json.append(",\"bounds\":{")
                .append("\"minX\":").append(Math.floor(minX)).append(',')
                .append("\"minZ\":").append(Math.floor(minZ)).append(',')
                .append("\"maxX\":").append(Math.ceil(maxX)).append(',')
                .append("\"maxZ\":").append(Math.ceil(maxZ))
                .append('}');
    }

    private void appendWebRoutes(StringBuilder json) {
        json.append(",\"routes\":[");
        boolean first = true;
        for (RailRoute route : routeRegistry.routes()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append("\"id\":").append(SimpleJson.string(route.id())).append(',')
                    .append("\"name\":").append(SimpleJson.string(route.name())).append(',')
                    .append("\"color\":").append(SimpleJson.string(route.color() == null ? "" : route.color())).append(',')
                    .append("\"lineWidth\":").append(route.lineWidth()).append(',')
                    .append("\"autoMatch\":").append(route.autoMatch()).append(',')
                    .append("\"componentIds\":[");
            boolean firstComponent = true;
            for (String componentId : route.componentIds()) {
                if (!firstComponent) {
                    json.append(',');
                }
                firstComponent = false;
                json.append(SimpleJson.string(componentId));
            }
            json.append("]}");
        }
        json.append(']');
    }

    private void appendWebMasks(StringBuilder json) {
        json.append(",\"masks\":[");
        boolean first = true;
        for (RailEditMask mask : editRegistry.masks()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append("\"id\":").append(SimpleJson.string(mask.id())).append(',')
                    .append("\"name\":").append(SimpleJson.string(mask.name())).append(',')
                    .append("\"world\":").append(SimpleJson.string(mask.worldName())).append(',')
                    .append("\"enabled\":").append(mask.enabled()).append(',')
                    .append("\"minX\":").append(mask.minX()).append(',')
                    .append("\"minY\":").append(mask.minY()).append(',')
                    .append("\"minZ\":").append(mask.minZ()).append(',')
                    .append("\"maxX\":").append(mask.maxX()).append(',')
                    .append("\"maxY\":").append(mask.maxY()).append(',')
                    .append("\"maxZ\":").append(mask.maxZ())
                    .append('}');
        }
        json.append(']');
    }

    private void appendWebHiddenLines(StringBuilder json) {
        json.append(",\"hiddenLines\":[");
        boolean first = true;
        for (RailEditHideRule hiddenLine : editRegistry.hiddenLines()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append("\"id\":").append(SimpleJson.string(hiddenLine.id())).append(',')
                    .append("\"name\":").append(SimpleJson.string(hiddenLine.name())).append(',')
                    .append("\"enabled\":").append(hiddenLine.enabled()).append(',')
                    .append("\"routeIds\":[");
            boolean firstRoute = true;
            for (String routeId : hiddenLine.routeIds()) {
                if (!firstRoute) {
                    json.append(',');
                }
                firstRoute = false;
                json.append(SimpleJson.string(routeId));
            }
            json.append("],\"componentIds\":[");
            boolean firstComponent = true;
            for (String componentId : hiddenLine.componentIds()) {
                if (!firstComponent) {
                    json.append(',');
                }
                firstComponent = false;
                json.append(SimpleJson.string(componentId));
            }
            json.append("]}");
        }
        json.append(']');
    }

    private void appendWebStations(StringBuilder json) {
        json.append(",\"stations\":[");
        boolean first = true;
        for (RailStation station : stationRegistry.stations()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append("\"id\":").append(SimpleJson.string(station.id())).append(',')
                    .append("\"name\":").append(SimpleJson.string(station.name())).append(',')
                    .append("\"world\":").append(SimpleJson.string(station.worldName())).append(',')
                    .append("\"minX\":").append(station.minX()).append(',')
                    .append("\"minY\":").append(station.minY()).append(',')
                    .append("\"minZ\":").append(station.minZ()).append(',')
                    .append("\"maxX\":").append(station.maxX()).append(',')
                    .append("\"maxY\":").append(station.maxY()).append(',')
                    .append("\"maxZ\":").append(station.maxZ())
                    .append('}');
        }
        json.append(']');
    }

    private void appendWebComponents(StringBuilder json, RailScanResult result) {
        json.append(",\"components\":[");
        if (result == null) {
            json.append(']');
            return;
        }

        boolean first = true;
        for (RailComponent component : result.components()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            RailRoute route = routeForComponent(component.id());
            json.append('{')
                    .append("\"id\":").append(SimpleJson.string(component.id())).append(',')
                    .append("\"world\":").append(SimpleJson.string(component.worldName())).append(',')
                    .append("\"pointCount\":").append(component.pointCount()).append(',')
                    .append("\"length\":").append(Math.round(component.length() * 10.0) / 10.0).append(',')
                    .append("\"minX\":").append(component.minX()).append(',')
                    .append("\"minY\":").append(component.minY()).append(',')
                    .append("\"minZ\":").append(component.minZ()).append(',')
                    .append("\"maxX\":").append(component.maxX()).append(',')
                    .append("\"maxY\":").append(component.maxY()).append(',')
                    .append("\"maxZ\":").append(component.maxZ()).append(',')
                    .append("\"routeId\":").append(SimpleJson.string(route == null ? "" : route.id())).append(',')
                    .append("\"routeName\":").append(SimpleJson.string(route == null ? "" : route.name()))
                    .append('}');
        }
        json.append(']');
    }

    private void appendWebLines(StringBuilder json, RailScanResult result) {
        json.append(",\"lines\":[");
        if (result == null) {
            json.append(']');
            return;
        }

        boolean first = true;
        for (RailLine line : result.lines()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append("\"componentId\":").append(SimpleJson.string(line.componentId())).append(',')
                    .append("\"world\":").append(SimpleJson.string(line.worldName())).append(',')
                    .append("\"type\":").append(SimpleJson.string(line.type().configKey())).append(',')
                    .append("\"powered\":").append(line.powered()).append(',')
                    .append("\"routeId\":").append(SimpleJson.string(line.routeId() == null ? "" : line.routeId())).append(',')
                    .append("\"routeName\":").append(SimpleJson.string(line.routeName() == null ? "" : line.routeName())).append(',')
                    .append("\"color\":").append(SimpleJson.string(lineColor(line))).append(',')
                    .append("\"points\":[");
            for (int i = 0; i < line.points().size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                Vector3d point = line.points().get(i);
                json.append('[')
                        .append(Math.round(point.getX() * 100.0) / 100.0).append(',')
                        .append(Math.round(point.getY() * 100.0) / 100.0).append(',')
                        .append(Math.round(point.getZ() * 100.0) / 100.0)
                        .append(']');
            }
            json.append("]}");
        }
        json.append(']');
    }

    private RailRoute routeForComponent(String componentId) {
        for (RailRoute route : routeRegistry.routes()) {
            if (route.componentIds().contains(componentId)) {
                return route;
            }
        }
        return null;
    }

    private String lineColor(RailLine line) {
        if (line.routeColor() != null && !line.routeColor().isBlank()) {
            return line.routeColor();
        }
        if (line.type() == io.github.emiliasamaemt.bluemaprailway.model.RailType.POWERED_RAIL && !line.powered()) {
            return config.colors().getOrDefault("powered-rail-inactive", "#65a30d");
        }
        return config.colors().getOrDefault(line.type().configKey(), "#9ca3af");
    }

    private String firstConfiguredWorld() {
        if (!config.worlds().isEmpty()) {
            return new TreeSet<>(config.worlds().keySet()).first();
        }
        return "minecraft:overworld";
    }

    private boolean isValidRouteId(String routeId) {
        return routeId != null && routeId.matches("[A-Za-z0-9_-]+");
    }
}
