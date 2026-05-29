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
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;

public final class FabricRailwayService {

    private final FabricRailwayLogger log;
    private final FabricSvgRailExporter svgExporter = new FabricSvgRailExporter();
    private final FabricAdminWebServer adminWebServer;
    private final FabricRailwayBackupService backupService;
    private final ScheduledExecutorService scheduler;
    private FabricRailwayConfig config;
    private FabricEditRegistry editRegistry;
    private FabricRouteRegistry routeRegistry;
    private FabricStationRegistry stationRegistry;
    private FabricRailScanner scanner;
    private FabricBlueMapRailRenderer renderer;
    private MinecraftServer server;
    private BlueMapAPI blueMapApi;
    private boolean initialScanCompleted;
    private RailScanResult lastBaseResult;
    private RailScanResult lastResult;
    private String lastSvgPath = "Not exported yet";
    private ScheduledFuture<?> fullRescanFuture;
    private ScheduledFuture<?> chunkRescanFuture;
    private final Set<ChunkRef> pendingChunkScans;
    private final Set<ChunkRef> loadedChunkRefs;
    private final Set<ChunkRef> knownChunkRefs;
    private boolean fullRescanRunning;
    private boolean chunkRescanRunning;

    public FabricRailwayService(FabricRailwayLogger log) {
        this.log = log;
        this.adminWebServer = new FabricAdminWebServer(this, log);
        this.backupService = new FabricRailwayBackupService(log);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BlueMapRailway Fabric Scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.pendingChunkScans = new LinkedHashSet<>();
        this.loadedChunkRefs = new LinkedHashSet<>();
        this.knownChunkRefs = new LinkedHashSet<>();
        reloadConfig();
    }

    public synchronized void reloadConfig() {
        List<String> addedPaths = FabricConfigUpdater.addMissingDefaults(log);
        this.config = FabricRailwayConfigLoader.load(log);
        this.editRegistry = FabricEditRegistry.load(log);
        this.routeRegistry = FabricRouteRegistry.load(log);
        this.stationRegistry = FabricStationRegistry.load(log);
        this.scanner = new FabricRailScanner(config, log);
        this.renderer = new FabricBlueMapRailRenderer(config);
        syncKnownChunkRefsFromCache();
        pruneTrackedChunkRefs();
        if (!addedPaths.isEmpty()) {
            log.info("Added missing Fabric config defaults: " + String.join(", ", addedPaths));
        }
        backupService.reload(config);
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
        cancelScheduledRescans();
        pendingChunkScans.clear();
        loadedChunkRefs.clear();
        knownChunkRefs.clear();
        this.server = null;
        this.initialScanCompleted = false;
        this.lastBaseResult = null;
        this.lastResult = null;
        backupService.stop();
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
        return config.scanner().chunkLoadRescan() &&
                config.cache().enabled() &&
                config.cache().scanNewlyLoadedChunks();
    }

    public synchronized void onChunkLoaded(ChunkRef chunkRef) {
        if (!isWorldEnabled(chunkRef.worldName())) {
            return;
        }

        loadedChunkRefs.add(chunkRef);
        knownChunkRefs.add(chunkRef);
        if (chunkLoadRescanEnabled()) {
            requestChunkLoadRescan(chunkRef);
        }
    }

    public synchronized void onChunkUnloaded(ChunkRef chunkRef) {
        loadedChunkRefs.remove(chunkRef);
    }

    public synchronized void requestChunkLoadRescan(ChunkRef chunkRef) {
        if (!initialScanCompleted || server == null || blueMapApi == null) {
            return;
        }

        pendingChunkScans.add(chunkRef);
        if (fullRescanFuture != null || fullRescanRunning) {
            return;
        }

        if (chunkRescanFuture != null) {
            return;
        }

        chunkRescanFuture = scheduler.schedule(this::enqueueChunkRescanOnServerThread,
                debounceMillis(config.cache().chunkLoadDebounceTicks()),
                TimeUnit.MILLISECONDS);
    }

    public synchronized void requestNeighborChunkRescans(Set<ChunkRef> chunkRefs) {
        if (chunkRefs.isEmpty()) {
            return;
        }

        boolean added = false;
        for (ChunkRef chunkRef : chunkRefs) {
            if (!isWorldEnabled(chunkRef.worldName())) {
                continue;
            }
            knownChunkRefs.add(chunkRef);
            if (pendingChunkScans.add(chunkRef)) {
                added = true;
            }
        }

        if (!added || server == null || blueMapApi == null) {
            return;
        }

        if (fullRescanFuture != null || fullRescanRunning || chunkRescanRunning) {
            return;
        }

        if (chunkRescanFuture != null) {
            chunkRescanFuture.cancel(false);
        }

        chunkRescanFuture = scheduler.schedule(this::enqueueChunkRescanOnServerThread,
                debounceMillis(config.scanner().updateDebounceTicks()),
                TimeUnit.MILLISECONDS);
    }

    public synchronized void requestRailBlockUpdate(String worldName, int blockX, int blockZ) {
        requestNeighborChunkRescans(affectedChunks(worldName, blockX >> 4, blockZ >> 4));
    }

    public synchronized void requestFullRescan() {
        if (server == null || blueMapApi == null) {
            return;
        }

        pendingChunkScans.clear();
        if (chunkRescanFuture != null) {
            chunkRescanFuture.cancel(false);
            chunkRescanFuture = null;
        }

        if (fullRescanFuture != null) {
            fullRescanFuture.cancel(false);
        }

        fullRescanFuture = scheduler.schedule(this::enqueueFullRescanOnServerThread,
                debounceMillis(config.scanner().updateDebounceTicks()),
                TimeUnit.MILLISECONDS);
    }

    public synchronized String createBackupNow() {
        return backupService.createBackupNow();
    }

    public synchronized double defaultStationRadius() {
        return config.stations().defaultRadius();
    }

    public synchronized void rescan() {
        if (server == null || blueMapApi == null) {
            return;
        }
        server.execute(this::rescanOnServerThread);
    }

    private synchronized void rescanOnServerThread() {
        if (server == null || blueMapApi == null) {
            return;
        }

        fullRescanFuture = null;
        fullRescanRunning = true;
        Set<ChunkRef> chunkRefs = fullScanTargets(server);
        knownChunkRefs.addAll(chunkRefs);
        lastBaseResult = scanner.scan(server, chunkRefs);
        RailScanResult result = applyRegistries(lastBaseResult);
        lastResult = result;
        renderer.render(blueMapApi, result, stationRegistry.stations());
        exportSvg(result);
        initialScanCompleted = true;
        fullRescanRunning = false;
        log.info("Fabric railway scan completed: " + result.scannedChunks() + " chunks, "
                + result.railCount() + " rails, " + result.lineCount() + " lines.");

        if (!pendingChunkScans.isEmpty()) {
            scheduleChunkRescanIfIdle();
        }
    }

    public synchronized String status() {
        String apiState = blueMapApi == null ? "waiting for BlueMap" : "running";
        String scanState = fullRescanRunning ? "full rescan running"
                : chunkRescanRunning ? "chunk rescan running"
                : fullRescanFuture != null ? "full rescan queued"
                : chunkRescanFuture != null ? "chunk rescan queued"
                : "idle";
        RailScanResult result = lastResult;
        int scannedChunks = result == null ? 0 : result.scannedChunks();
        int railCount = result == null ? 0 : result.railCount();
        int componentCount = result == null ? 0 : result.componentCount();
        int lineCount = result == null ? 0 : result.lineCount();
        int cachedChunks = result == null ? 0 : result.cachedChunks();
        return "BlueMapRailway status: " + apiState + ", " + scanState
                + ". Last result: " + scannedChunks + " chunks, " + railCount + " rails, "
                + componentCount + " components, " + lineCount + " lines, cache " + cachedChunks + " chunks.";
    }

    public synchronized String debugStatus() {
        RailScanResult result = lastResult;
        int hiddenLineCount = editRegistry.hiddenLineCount();
        int classifiedLineCount = result == null ? 0 : result.classifiedLineCount();
        return status()
                + "\nPending chunk rescans: " + pendingChunkScans.size()
                + "\nLoaded chunks tracked: " + loadedChunkRefs.size()
                + "\nKnown chunks tracked: " + knownChunkRefs.size()
                + "\nHidden line rules: " + hiddenLineCount
                + "\nMask rules: " + editRegistry.maskCount()
                + "\nRoutes: " + routeRegistry.routeCount()
                + "\nAssigned route components: " + routeRegistry.assignedComponentCount()
                + "\nStations: " + stationRegistry.stationCount()
                + "\nClassified lines: " + classifiedLineCount
                + "\nSVG: " + lastSvgPath;
    }

    public synchronized String routeList() {
        if (routeRegistry.routes().isEmpty()) {
            return "routes.yml is empty. Use /railmap route create <id> <name> to create one.";
        }

        StringBuilder builder = new StringBuilder("Routes:");
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
            return "Route not found: " + routeId;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Route ").append(route.id()).append('\n');
        builder.append("Name: ").append(route.name()).append('\n');
        builder.append("Color: ").append(route.color() == null ? "default" : route.color()).append('\n');
        builder.append("Line width: ").append(route.lineWidth() > 0 ? route.lineWidth() : "default").append('\n');
        builder.append("Auto match: ").append(route.autoMatch()).append('\n');
        builder.append("Anchors: ").append(route.anchors().size()).append('\n');
        builder.append("Bound components:");
        if (route.componentIds().isEmpty()) {
            builder.append(" none");
        } else {
            for (String componentId : route.componentIds()) {
                builder.append('\n').append("- ").append(componentId);
            }
        }
        return builder.toString();
    }

    public synchronized String routeCreate(String routeId, String name) {
        if (!isValidRouteId(routeId)) {
            return "Route ID may only contain letters, numbers, underscores, and hyphens.";
        }
        if (routeRegistry.route(routeId) != null) {
            return "Route already exists: " + routeId;
        }
        return parseOkMessage(webSaveRoute(Map.of(
                "id", routeId,
                "name", name,
                "color", "",
                "lineWidth", -1,
                "autoMatch", true,
                "componentIds", List.of()
        )), "Created route " + routeId + " / " + name);
    }

    public synchronized String routeRename(String routeId, String name) {
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "Route not found: " + routeId;
        }
        return parseOkMessage(webSaveRoute(Map.of(
                "id", routeId,
                "name", name,
                "color", route.color() == null ? "" : route.color(),
                "lineWidth", route.lineWidth(),
                "autoMatch", route.autoMatch(),
                "componentIds", List.copyOf(route.componentIds())
        )), "Renamed route " + routeId + " to " + name);
    }

    public synchronized String routeColor(String routeId, String color) {
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "Route not found: " + routeId;
        }
        return parseOkMessage(webSaveRoute(Map.of(
                "id", routeId,
                "name", route.name(),
                "color", color,
                "lineWidth", route.lineWidth(),
                "autoMatch", route.autoMatch(),
                "componentIds", List.copyOf(route.componentIds())
        )), "Set route color: " + routeId + " -> " + color);
    }

    public synchronized String routeWidth(String routeId, int width) {
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "Route not found: " + routeId;
        }
        if (width < 1 || width > 64) {
            return "Line width must be between 1 and 64.";
        }
        return parseOkMessage(webSaveRoute(Map.of(
                "id", routeId,
                "name", route.name(),
                "color", route.color() == null ? "" : route.color(),
                "lineWidth", width,
                "autoMatch", route.autoMatch(),
                "componentIds", List.copyOf(route.componentIds())
        )), "Set route width: " + routeId + " -> " + width);
    }

    public synchronized String routeAutoMatch(String routeId, boolean enabled) {
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "Route not found: " + routeId;
        }
        return parseOkMessage(webSaveRoute(Map.of(
                "id", routeId,
                "name", route.name(),
                "color", route.color() == null ? "" : route.color(),
                "lineWidth", route.lineWidth(),
                "autoMatch", enabled,
                "componentIds", List.copyOf(route.componentIds())
        )), (enabled ? "Enabled" : "Disabled") + " route auto match: " + routeId);
    }

    public synchronized String routeStatus(String routeId) {
        return routeRegistry.status(lastResult, config.core(), routeId);
    }

    public synchronized String routeAssignNearest(ServerPlayer player, String routeId, double radius) {
        if (radius <= 0) {
            return "Radius must be greater than 0.";
        }
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "Route not found: " + routeId;
        }
        NearestComponent nearest = nearestComponent(worldId(player), player.getX(), player.getY(), player.getZ(), radius);
        if (nearest == null) {
            return "No railway component found within radius " + radius + ".";
        }
        LinkedHashSet<String> componentIds = new LinkedHashSet<>(route.componentIds());
        componentIds.add(nearest.component().id());
        return parseOkMessage(webSaveRoute(Map.of(
                "id", routeId,
                "name", route.name(),
                "color", route.color() == null ? "" : route.color(),
                "lineWidth", route.lineWidth(),
                "autoMatch", route.autoMatch(),
                "componentIds", List.copyOf(componentIds)
        )), "Bound nearest component to route " + routeId + ": " + nearest.component().id());
    }

    public synchronized String routeAnchorNearest(ServerPlayer player, String routeId, double radius) {
        if (radius <= 0) {
            return "Radius must be greater than 0.";
        }
        RailRoute route = routeRegistry.route(routeId);
        if (route == null) {
            return "Route not found: " + routeId;
        }
        NearestComponent nearest = nearestComponent(worldId(player), player.getX(), player.getY(), player.getZ(), radius);
        if (nearest == null) {
            return "No railway component found within radius " + radius + ".";
        }
        List<RailRouteAnchor> anchors = new ArrayList<>(route.anchors());
        RailRouteAnchor anchor = RailRouteAnchor.of(nearest.position());
        if (!anchors.contains(anchor)) {
            anchors.add(anchor);
        }
        RailRoute updated = new RailRoute(
                route.id(),
                route.name(),
                route.color(),
                route.lineWidth(),
                route.componentIds(),
                List.copyOf(anchors),
                RailRouteBounds.of(nearest.component()),
                route.autoMatch()
        );
        routeRegistry.saveRoute(updated, log);
        reloadRoutesAndRescan();
        return "Added route auto-match anchor for " + routeId + ": "
                + anchor.worldName() + " " + anchor.x() + "," + anchor.y() + "," + anchor.z();
    }

    public synchronized String stationList() {
        if (stationRegistry.stations().isEmpty()) {
            return "stations.yml is empty. Use /railmap station add <id> <name> [radius] to create one.";
        }
        StringBuilder builder = new StringBuilder("Stations:");
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
        RailStation station = stationRegistry.station(stationId);
        if (station == null) {
            return "Station not found: " + stationId;
        }
        return "Station " + station.id() + '\n'
                + "Name: " + station.name() + '\n'
                + "World: " + station.worldName() + '\n'
                + "Area: [" + station.minX() + ',' + station.minY() + ',' + station.minZ() + "] -> ["
                + station.maxX() + ',' + station.maxY() + ',' + station.maxZ() + "]";
    }

    public synchronized String stationAddHere(ServerPlayer player, String stationId, String name, double radius) {
        if (!isValidRouteId(stationId)) {
            return "Station ID may only contain letters, numbers, underscores, and hyphens.";
        }
        if (stationRegistry.station(stationId) != null) {
            return "Station already exists: " + stationId;
        }
        StationArea area = stationArea(worldId(player), player.getBlockX(), player.getBlockY(), player.getBlockZ(), radius);
        return parseOkMessage(webSaveStation(Map.of(
                "id", stationId,
                "name", name,
                "world", area.worldName(),
                "minX", area.minX(),
                "minY", area.minY(),
                "minZ", area.minZ(),
                "maxX", area.maxX(),
                "maxY", area.maxY(),
                "maxZ", area.maxZ()
        )), "Created station " + stationId + " / " + name);
    }

    public synchronized String stationSetAreaHere(ServerPlayer player, String stationId, double radius) {
        RailStation station = stationRegistry.station(stationId);
        if (station == null) {
            return "Station not found: " + stationId;
        }
        StationArea area = stationArea(worldId(player), player.getBlockX(), player.getBlockY(), player.getBlockZ(), radius);
        return parseOkMessage(webSaveStation(Map.of(
                "id", stationId,
                "name", station.name(),
                "world", area.worldName(),
                "minX", area.minX(),
                "minY", area.minY(),
                "minZ", area.minZ(),
                "maxX", area.maxX(),
                "maxY", area.maxY(),
                "maxZ", area.maxZ()
        )), "Updated station area: " + stationId);
    }

    public synchronized String stationRemove(String stationId) {
        return parseOkMessage(webDeleteStation(Map.of("id", stationId)), "Removed station " + stationId);
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
            return errorJson("Route ID may only contain letters, numbers, underscores, and hyphens.");
        }

        String name = SimpleJson.text(request, "name", routeId).trim();
        if (name.isBlank()) {
            name = routeId;
        }

        String color = SimpleJson.text(request, "color", "").trim();
        if (!color.isBlank() && !color.matches("#[0-9a-fA-F]{6}")) {
            return errorJson("Color must use the #RRGGBB format.");
        }

        int lineWidth = SimpleJson.integer(request, "lineWidth", -1);
        boolean autoMatch = SimpleJson.bool(request, "autoMatch", true);
        List<String> componentIds = SimpleJson.stringList(request, "componentIds").stream()
                .distinct()
                .toList();

        RailRoute existing = routeRegistry.route(routeId);
        Set<String> normalizedComponentIds = new LinkedHashSet<>(componentIds);
        Set<String> existingComponentIds = existing == null ? Set.of() : existing.componentIds();
        List<RailRouteAnchor> anchors = existing == null ? new ArrayList<>() : new ArrayList<>(existing.anchors());
        RailRouteBounds bounds = existing == null ? null : existing.bounds();
        if (!normalizedComponentIds.isEmpty()) {
            List<RailRouteAnchor> resolvedAnchors = routeAnchors(componentIds);
            RailRouteBounds resolvedBounds = routeBounds(componentIds);
            if (!resolvedAnchors.isEmpty() && (existing == null || !existingComponentIds.equals(normalizedComponentIds))) {
                for (RailRouteAnchor anchor : resolvedAnchors) {
                    if (!anchors.contains(anchor)) {
                        anchors.add(anchor);
                    }
                }
            }
            if (resolvedBounds != null) {
                bounds = resolvedBounds;
            }
        }

        RailRoute route = new RailRoute(
                routeId,
                name,
                color.isBlank() ? null : color,
                lineWidth > 0 ? lineWidth : -1,
                normalizedComponentIds,
                List.copyOf(anchors),
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
            return errorJson("Route ID may only contain letters, numbers, underscores, and hyphens.");
        }
        if (!routeRegistry.deleteRoute(routeId, log)) {
            return errorJson("Route not found.");
        }
        reloadRoutesAndRescan();
        return okJson();
    }

    public synchronized String webSaveStation(Map<String, Object> request) {
        String stationId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(stationId)) {
            return errorJson("Station ID may only contain letters, numbers, underscores, and hyphens.");
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
            return errorJson("Station ID may only contain letters, numbers, underscores, and hyphens.");
        }
        if (!stationRegistry.deleteStation(stationId, log)) {
            return errorJson("Station not found.");
        }
        reloadStationsAndRescan();
        return okJson();
    }

    public synchronized String webSaveMask(Map<String, Object> request) {
        String maskId = SimpleJson.text(request, "id", "").trim();
        if (!isValidRouteId(maskId)) {
            return errorJson("Mask ID may only contain letters, numbers, underscores, and hyphens.");
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
            return errorJson("Mask ID may only contain letters, numbers, underscores, and hyphens.");
        }
        if (!editRegistry.deleteMask(maskId, log)) {
            return errorJson("Mask not found.");
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
            return errorJson("Hide-rule ID may only contain letters, numbers, underscores, and hyphens.");
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
            return errorJson("At least one route or component must be selected.");
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
            return errorJson("Hide-rule ID may only contain letters, numbers, underscores, and hyphens.");
        }
        if (!editRegistry.deleteHiddenLine(ruleId, log)) {
            return errorJson("Hide rule not found.");
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
        if (!config.export().svg().enabled()) {
            lastSvgPath = "Disabled by config";
            return;
        }

        try {
            Path path = svgExporter.export(result, stationRegistry.stations());
            lastSvgPath = path.toString();
            log.info("Fabric SVG exported: " + path);
        } catch (IOException exception) {
            log.warning("Failed to export Fabric SVG: " + exception.getMessage());
        }
    }

    private RailScanResult applyRegistries(RailScanResult result) {
        return editRegistry.apply(routeRegistry.apply(result, config.core()));
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

    private String worldId(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private StationArea stationArea(String worldName, int x, int y, int z, double radius) {
        int horizontalRadius = (int) Math.ceil(radius);
        int yRadius = Math.max(1, config.stations().defaultYRadius());
        return new StationArea(
                worldName,
                x - horizontalRadius,
                y - yRadius,
                z - horizontalRadius,
                x + horizontalRadius,
                y + yRadius,
                z + horizontalRadius
        );
    }

    private NearestComponent nearestComponent(String worldName, double x, double y, double z, double radius) {
        if (lastResult == null) {
            return null;
        }

        double radiusSquared = radius * radius;
        RailComponent nearest = null;
        RailPosition nearestPosition = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (RailComponent component : lastResult.components()) {
            if (!component.worldName().equals(worldName)) {
                continue;
            }

            for (RailPosition position : component.positions()) {
                double dx = position.x() + 0.5 - x;
                double dy = position.y() + 0.5 - y;
                double dz = position.z() + 0.5 - z;
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

    private String parseOkMessage(String json, String successMessage) {
        Map<String, Object> response = SimpleJson.object(SimpleJson.parse(json));
        if (SimpleJson.bool(response, "ok", false)) {
            return successMessage;
        }
        return SimpleJson.text(response, "error", "Unknown error");
    }

    private boolean isValidRouteId(String routeId) {
        return routeId != null && routeId.matches("[A-Za-z0-9_-]+");
    }

    private Set<ChunkRef> fullScanTargets(MinecraftServer server) {
        LinkedHashSet<ChunkRef> chunkRefs = new LinkedHashSet<>();
        chunkRefs.addAll(knownChunkRefs);
        chunkRefs.addAll(loadedChunkRefs);
        if (chunkRefs.isEmpty()) {
            chunkRefs.addAll(scanner.initialChunkRefs(server));
        }
        return Set.copyOf(chunkRefs);
    }

    private Set<ChunkRef> affectedChunks(String worldName, int chunkX, int chunkZ) {
        int radius = Math.max(0, config.scanner().blockUpdateNeighborRadius());
        LinkedHashSet<ChunkRef> chunkRefs = new LinkedHashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            int zRadius = radius - Math.abs(dx);
            for (int dz = -zRadius; dz <= zRadius; dz++) {
                chunkRefs.add(new ChunkRef(worldName, chunkX + dx, chunkZ + dz));
            }
        }
        return Set.copyOf(chunkRefs);
    }

    private void syncKnownChunkRefsFromCache() {
        knownChunkRefs.addAll(scanner.cachedChunkRefs());
    }

    private void pruneTrackedChunkRefs() {
        loadedChunkRefs.removeIf(chunkRef -> !isWorldEnabled(chunkRef.worldName()));
        knownChunkRefs.removeIf(chunkRef -> !isWorldEnabled(chunkRef.worldName()));
        pendingChunkScans.removeIf(chunkRef -> !isWorldEnabled(chunkRef.worldName()));
    }

    private synchronized void scheduleChunkRescanIfIdle() {
        if (server == null || blueMapApi == null || pendingChunkScans.isEmpty() || fullRescanRunning || fullRescanFuture != null) {
            return;
        }
        if (chunkRescanFuture != null || chunkRescanRunning) {
            return;
        }

        chunkRescanFuture = scheduler.schedule(this::enqueueChunkRescanOnServerThread,
                debounceMillis(config.cache().chunkLoadDebounceTicks()),
                TimeUnit.MILLISECONDS);
    }

    private synchronized void enqueueFullRescanOnServerThread() {
        if (server == null || blueMapApi == null) {
            fullRescanFuture = null;
            return;
        }
        server.execute(this::rescanOnServerThread);
    }

    private synchronized void enqueueChunkRescanOnServerThread() {
        if (server == null || blueMapApi == null || pendingChunkScans.isEmpty()) {
            chunkRescanFuture = null;
            return;
        }
        server.execute(this::chunkRescanOnServerThread);
    }

    private synchronized void chunkRescanOnServerThread() {
        if (server == null || blueMapApi == null || pendingChunkScans.isEmpty()) {
            chunkRescanFuture = null;
            return;
        }

        chunkRescanFuture = null;
        chunkRescanRunning = true;
        Set<ChunkRef> chunkRefs = Set.copyOf(pendingChunkScans);
        pendingChunkScans.clear();
        lastBaseResult = scanner.scan(server, chunkRefs);
        RailScanResult result = applyRegistries(lastBaseResult);
        lastResult = result;
        renderer.render(blueMapApi, result, stationRegistry.stations());
        exportSvg(result);
        initialScanCompleted = true;
        chunkRescanRunning = false;
        log.info("Fabric chunk railway scan completed: " + result.scannedChunks() + " chunks, "
                + result.railCount() + " rails, " + result.lineCount() + " lines.");

        if (!pendingChunkScans.isEmpty()) {
            scheduleChunkRescanIfIdle();
        }
    }

    private synchronized void cancelScheduledRescans() {
        if (fullRescanFuture != null) {
            fullRescanFuture.cancel(false);
            fullRescanFuture = null;
        }
        if (chunkRescanFuture != null) {
            chunkRescanFuture.cancel(false);
            chunkRescanFuture = null;
        }
        fullRescanRunning = false;
        chunkRescanRunning = false;
    }

    private long debounceMillis(int ticks) {
        return Math.max(0L, ticks) * 50L;
    }

    private record NearestComponent(RailComponent component, RailPosition position) {
    }

    private record StationArea(
            String worldName,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
    }
}
