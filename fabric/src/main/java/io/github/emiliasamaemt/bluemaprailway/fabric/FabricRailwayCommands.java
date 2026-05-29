package io.github.emiliasamaemt.bluemaprailway.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class FabricRailwayCommands {

    private final FabricRailwayService service;

    public FabricRailwayCommands(FabricRailwayService service) {
        this.service = service;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("railmap")
                .executes(context -> success(context.getSource(), service.status()))
                .then(literal("status")
                        .executes(context -> success(context.getSource(), service.status())))
                .then(literal("debug")
                        .executes(context -> success(context.getSource(), service.debugStatus())))
                .then(literal("reload")
                        .executes(context -> success(context.getSource(), reload())))
                .then(literal("rescan")
                        .executes(context -> success(context.getSource(), rescan())))
                .then(literal("backup")
                        .executes(context -> success(context.getSource(), service.createBackupNow())))
                .then(routeTree())
                .then(stationTree()));
    }

    private LiteralArgumentBuilder<CommandSourceStack> routeTree() {
        return literal("route")
                .then(literal("list")
                        .executes(context -> success(context.getSource(), service.routeList())))
                .then(literal("info")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> success(context.getSource(),
                                        service.routeInfo(StringArgumentType.getString(context, "id"))))))
                .then(literal("status")
                        .executes(context -> success(context.getSource(), service.routeStatus(null)))
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> success(context.getSource(),
                                        service.routeStatus(StringArgumentType.getString(context, "id"))))))
                .then(literal("create")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> success(context.getSource(), service.routeCreate(
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "name")))))))
                .then(literal("rename")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> success(context.getSource(), service.routeRename(
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "name")))))))
                .then(literal("color")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("color", StringArgumentType.word())
                                        .executes(context -> success(context.getSource(), service.routeColor(
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "color")))))))
                .then(literal("width")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("width", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> success(context.getSource(), service.routeWidth(
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "width")))))))
                .then(literal("auto-match")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> success(context.getSource(), service.routeAutoMatch(
                                                StringArgumentType.getString(context, "id"),
                                                BoolArgumentType.getBool(context, "enabled")))))))
                .then(literal("assign-nearest")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> withPlayer(context.getSource(), player ->
                                        service.routeAssignNearest(player, StringArgumentType.getString(context, "id"), 16.0)))
                                .then(argument("radius", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> withPlayer(context.getSource(), player ->
                                                service.routeAssignNearest(
                                                        player,
                                                        StringArgumentType.getString(context, "id"),
                                                        DoubleArgumentType.getDouble(context, "radius")))))))
                .then(literal("anchor-nearest")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> withPlayer(context.getSource(), player ->
                                        service.routeAnchorNearest(player, StringArgumentType.getString(context, "id"), 16.0)))
                                .then(argument("radius", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> withPlayer(context.getSource(), player ->
                                                service.routeAnchorNearest(
                                                        player,
                                                        StringArgumentType.getString(context, "id"),
                                                        DoubleArgumentType.getDouble(context, "radius")))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> stationTree() {
        return literal("station")
                .then(literal("list")
                        .executes(context -> success(context.getSource(), service.stationList())))
                .then(literal("info")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> success(context.getSource(),
                                        service.stationInfo(StringArgumentType.getString(context, "id"))))))
                .then(literal("add")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("radius", DoubleArgumentType.doubleArg(0.1))
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(context -> withPlayer(context.getSource(), player ->
                                                        service.stationAddHere(
                                                                player,
                                                                StringArgumentType.getString(context, "id"),
                                                                StringArgumentType.getString(context, "name"),
                                                                DoubleArgumentType.getDouble(context, "radius"))))))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> withPlayer(context.getSource(), player ->
                                                service.stationAddHere(
                                                        player,
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "name"),
                                                        service.defaultStationRadius()))))))
                .then(literal("set-area-here")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> withPlayer(context.getSource(), player ->
                                        service.stationSetAreaHere(player, StringArgumentType.getString(context, "id"), service.defaultStationRadius())))
                                .then(argument("radius", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> withPlayer(context.getSource(), player ->
                                                service.stationSetAreaHere(
                                                        player,
                                                        StringArgumentType.getString(context, "id"),
                                                        DoubleArgumentType.getDouble(context, "radius")))))))
                .then(literal("remove")
                        .then(argument("id", StringArgumentType.word())
                                .executes(context -> success(context.getSource(),
                                        service.stationRemove(StringArgumentType.getString(context, "id"))))));
    }

    private String reload() {
        service.reloadConfig();
        return "BlueMapRailway Fabric config reloaded.";
    }

    private String rescan() {
        service.requestFullRescan();
        return "BlueMapRailway Fabric full rescan queued.";
    }

    private int withPlayer(CommandSourceStack source, PlayerAction action) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return success(source, action.run(player));
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
    }

    private int success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    @FunctionalInterface
    private interface PlayerAction {
        String run(ServerPlayer player);
    }
}
