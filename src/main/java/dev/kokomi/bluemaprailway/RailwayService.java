package dev.kokomi.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.plugin.Plugin;

public final class RailwayService {

    private final Plugin plugin;
    private BlueMapAPI blueMapApi;
    private boolean fullRescanQueued;

    public RailwayService(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start(BlueMapAPI api) {
        this.blueMapApi = api;
        this.fullRescanQueued = true;
        plugin.getLogger().info("BlueMap API is ready. Railway overlay service started.");
    }

    public synchronized void stop() {
        if (blueMapApi != null) {
            plugin.getLogger().info("Railway overlay service stopped.");
        }

        blueMapApi = null;
    }

    public synchronized void reload() {
        requestFullRescan();
    }

    public synchronized void requestFullRescan() {
        fullRescanQueued = true;
        plugin.getLogger().info("Full railway rescan queued.");
    }

    public synchronized String status() {
        String apiState = blueMapApi == null ? "waiting for BlueMap" : "running";
        String scanState = fullRescanQueued ? "full rescan queued" : "idle";
        return "BlueMapRailway status: " + apiState + ", " + scanState + ".";
    }
}
