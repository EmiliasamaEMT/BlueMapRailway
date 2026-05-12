package io.github.emiliasamaemt.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class BlueMapRailwayPlugin extends JavaPlugin {

    private RailwayService railwayService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        railwayService = new RailwayService(this);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new RailwayBlockListener(railwayService), this);

        BlueMapAPI.onEnable(railwayService::start);
        BlueMapAPI.onDisable(api -> railwayService.stop());

        BlueMapAPI.getInstance().ifPresent(railwayService::start);
    }

    @Override
    public void onDisable() {
        if (railwayService != null) {
            railwayService.stop();
        }
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("railmap")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(railwayService.status());
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            sender.sendMessage(railwayService.debugStatus());
            return true;
        }

        if (args[0].equalsIgnoreCase("route")) {
            sender.sendMessage(handleRouteCommand(sender, args));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            railwayService.reload();
            sender.sendMessage("BlueMapRailway 配置已重载。");
            return true;
        }

        if (args[0].equalsIgnoreCase("rescan")) {
            railwayService.requestFullRescan();
            sender.sendMessage("BlueMapRailway 已排队执行完整重扫。");
            return true;
        }

        return false;
    }

    private String handleRouteCommand(CommandSender sender, String[] args) {
        try {
            if (args.length < 2) {
                return routeUsage();
            }

            String action = args[1].toLowerCase();
            if (action.equals("list")) {
                return railwayService.routeList();
            }

            if (action.equals("info") && args.length >= 3) {
                return railwayService.routeInfo(args[2]);
            }

            if (action.equals("create") && args.length >= 4) {
                return railwayService.routeCreate(args[2], joinArgs(args, 3));
            }

            if (action.equals("color") && args.length >= 4) {
                return railwayService.routeColor(args[2], args[3]);
            }

            if (action.equals("width") && args.length >= 4) {
                try {
                    return railwayService.routeWidth(args[2], Integer.parseInt(args[3]));
                } catch (NumberFormatException exception) {
                    return "线宽必须是整数。";
                }
            }

            if (action.equals("assign-nearest") && args.length >= 3) {
                if (!(sender instanceof Player player)) {
                    return "该命令只能由游戏内玩家执行。";
                }

                double radius = 16.0;
                if (args.length >= 4) {
                    try {
                        radius = Double.parseDouble(args[3]);
                    } catch (NumberFormatException exception) {
                        return "半径必须是数字。";
                    }
                }

                return railwayService.routeAssignNearest(player, args[2], radius);
            }

            return routeUsage();
        } catch (IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    private String routeUsage() {
        return """
                用法:
                /railmap route list
                /railmap route info <id>
                /railmap route create <id> <名称>
                /railmap route color <id> <#RRGGBB>
                /railmap route width <id> <宽度>
                /railmap route assign-nearest <id> [半径]
                """;
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }

            builder.append(args[i]);
        }

        return builder.toString();
    }
}
