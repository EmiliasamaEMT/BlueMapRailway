package io.github.emiliasamaemt.bluemaprailway.fabric;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlueMapRailwayFabricMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "bluemaprailway";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final FabricRailwayLogger log = new FabricRailwayLogger(LOGGER);
    private final FabricRailwayService service = new FabricRailwayService(log);

    @Override
    public void onInitializeServer() {
        log.info("Initializing Fabric railway module.");

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> onChunkLoad(world.getServer(), world));

        BlueMapAPI.onEnable(service::startBlueMap);
        BlueMapAPI.onDisable(api -> service.stopBlueMap());
        BlueMapAPI.getInstance().ifPresent(service::startBlueMap);
    }

    private void onServerStarted(MinecraftServer server) {
        service.setServer(server);
        service.reloadConfig();
        service.rescan();
    }

    private void onServerStopping(MinecraftServer server) {
        service.stopBlueMap();
        service.clearServer();
    }

    private void onChunkLoad(MinecraftServer server, ServerLevel world) {
        String worldId = world.dimension().identifier().toString();
        if (!service.chunkLoadRescanEnabled() || !service.isWorldEnabled(worldId)) {
            return;
        }
        service.requestChunkLoadRescan();
    }
}
