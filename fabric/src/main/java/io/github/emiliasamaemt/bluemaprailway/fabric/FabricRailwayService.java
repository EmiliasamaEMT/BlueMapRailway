package io.github.emiliasamaemt.bluemaprailway.fabric;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.minecraft.server.MinecraftServer;

public final class FabricRailwayService {

    private static final long RESCAN_COOLDOWN_MILLIS = 2_000L;

    private final FabricRailwayLogger log;
    private FabricRailwayConfig config;
    private FabricRailScanner scanner;
    private FabricBlueMapRailRenderer renderer;
    private MinecraftServer server;
    private BlueMapAPI blueMapApi;
    private boolean rescanQueued;
    private boolean initialScanCompleted;
    private long lastRescanAt;

    public FabricRailwayService(FabricRailwayLogger log) {
        this.log = log;
        reloadConfig();
    }

    public void reloadConfig() {
        this.config = FabricRailwayConfigLoader.load(log);
        this.scanner = new FabricRailScanner(config, log);
        this.renderer = new FabricBlueMapRailRenderer(config);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void clearServer() {
        this.server = null;
        this.initialScanCompleted = false;
    }

    public void startBlueMap(BlueMapAPI api) {
        this.blueMapApi = api;
        log.info("BlueMap API is ready on Fabric, starting railway scan.");
        rescan();
    }

    public void stopBlueMap() {
        this.blueMapApi = null;
        this.initialScanCompleted = false;
    }

    public boolean isWorldEnabled(String worldId) {
        FabricRailwayConfig.FabricWorldConfig world = config.worlds().get(worldId);
        return world != null && world.enabled();
    }

    public boolean chunkLoadRescanEnabled() {
        return config.chunkLoadRescan();
    }

    public void requestChunkLoadRescan() {
        if (!initialScanCompleted) {
            return;
        }
        rescan();
    }

    public void rescan() {
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

    private void rescanOnServerThread() {
        rescanQueued = false;
        if (server == null || blueMapApi == null) {
            return;
        }

        lastRescanAt = System.currentTimeMillis();
        var result = scanner.scan(server);
        renderer.render(blueMapApi, result);
        initialScanCompleted = true;
        log.info("Fabric railway scan completed: " + result.scannedChunks() + " chunks, "
                + result.railCount() + " rails, " + result.lineCount() + " lines.");
    }
}
