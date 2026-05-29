package io.github.emiliasamaemt.bluemaprailway.fabric;

import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import io.github.emiliasamaemt.bluemaprailway.scan.ChunkRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlueMapRailwayFabricMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "bluemaprailway";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final FabricRailwayLogger log = new FabricRailwayLogger(LOGGER);
    private final FabricRailwayService service = new FabricRailwayService(log);
    private final FabricRailwayCommands commands = new FabricRailwayCommands(service);

    @Override
    public void onInitializeServer() {
        log.info("Initializing Fabric railway module.");

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> onChunkLoad(world, chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> onChunkUnload(world, chunk));
        PlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
        UseBlockCallback.EVENT.register(this::onUseBlock);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands.register(dispatcher));

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

    private void onChunkLoad(ServerLevel world, LevelChunk chunk) {
        String worldId = world.dimension().identifier().toString();
        service.onChunkLoaded(new ChunkRef(worldId, chunk.getPos().x, chunk.getPos().z));
    }

    private void onChunkUnload(ServerLevel world, LevelChunk chunk) {
        String worldId = world.dimension().identifier().toString();
        service.onChunkUnloaded(new ChunkRef(worldId, chunk.getPos().x, chunk.getPos().z));
    }

    private void onBlockBreak(Level world, net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(world instanceof ServerLevel serverLevel) || !isRail(state)) {
            return;
        }
        service.requestRailBlockUpdate(serverLevel.dimension().identifier().toString(), pos.getX(), pos.getZ());
    }

    private InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player, Level world, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        ItemStack itemStack = player.getItemInHand(hand);
        if (!isRailItem(itemStack)) {
            return InteractionResult.PASS;
        }

        var clickedPos = hitResult.getBlockPos();
        service.requestRailBlockUpdate(serverLevel.dimension().identifier().toString(), clickedPos.getX(), clickedPos.getZ());
        var placedPos = clickedPos.relative(hitResult.getDirection());
        service.requestRailBlockUpdate(serverLevel.dimension().identifier().toString(), placedPos.getX(), placedPos.getZ());
        return InteractionResult.PASS;
    }

    private boolean isRail(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock;
    }

    private boolean isRailItem(ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BaseRailBlock;
    }
}
