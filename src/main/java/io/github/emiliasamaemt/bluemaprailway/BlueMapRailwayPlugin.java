package io.github.emiliasamaemt.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import io.github.emiliasamaemt.bluemaprailway.web.AdminWebServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class BlueMapRailwayPlugin extends JavaPlugin {

    private RailwayService railwayService;
    private AdminWebServer adminWebServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        railwayService = new RailwayService(this);
        adminWebServer = new AdminWebServer(this, railwayService);
        adminWebServer.start();

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

        if (adminWebServer != null) {
            adminWebServer.stop();
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

        if (args[0].equalsIgnoreCase("station")) {
            sender.sendMessage(handleStationCommand(sender, args));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            railwayService.reload();
            if (adminWebServer != null) {
                adminWebServer.stop();
                adminWebServer.start();
            }
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

            if (action.equals("status")) {
                return railwayService.routeStatus(args.length >= 3 ? args[2] : null);
            }

            if (action.equals("create") && args.length >= 4) {
                return railwayService.routeCreate(args[2], joinArgs(args, 3));
            }

            if (action.equals("rename") && args.length >= 4) {
                return railwayService.routeRename(args[2], joinArgs(args, 3));
            }

            if (action.equals("color") && args.length >= 4) {
                return railwayService.routeColor(args[2], args[3]);
            }

            if (action.equals("auto-match") && args.length >= 4) {
                if (!args[3].equalsIgnoreCase("true") && !args[3].equalsIgnoreCase("false")) {
                    return "auto-match 必须是 true 或 false。";
                }

                return railwayService.routeAutoMatch(args[2], Boolean.parseBoolean(args[3]));
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

            if (action.equals("anchor-nearest") && args.length >= 3) {
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

                return railwayService.routeAnchorNearest(player, args[2], radius);
            }

            return routeUsage();
        } catch (IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    private String handleStationCommand(CommandSender sender, String[] args) {
        try {
            if (args.length < 2) {
                return stationUsage();
            }

            String action = args[1].toLowerCase();
            if (action.equals("list")) {
                return railwayService.stationList();
            }

            if (action.equals("info") && args.length >= 3) {
                return railwayService.stationInfo(args[2]);
            }

            if (action.equals("add") && args.length >= 4) {
                if (!(sender instanceof Player player)) {
                    return "该命令只能由游戏内玩家执行。";
                }

                ParsedNameAndRadius parsed = parseNameAndRadius(args, 3, defaultStationRadius());
                return railwayService.stationAddHere(player, args[2], parsed.name(), parsed.radius());
            }

            if (action.equals("set-area-here") && args.length >= 3) {
                if (!(sender instanceof Player player)) {
                    return "该命令只能由游戏内玩家执行。";
                }

                double radius = defaultStationRadius();
                if (args.length >= 4) {
                    try {
                        radius = Double.parseDouble(args[3]);
                    } catch (NumberFormatException exception) {
                        return "半径必须是数字。";
                    }
                }

                return railwayService.stationSetAreaHere(player, args[2], radius);
            }

            if (action.equals("remove") && args.length >= 3) {
                return railwayService.stationRemove(args[2]);
            }

            return stationUsage();
        } catch (IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    private String routeUsage() {
        return """
                用法:
                /railmap route list
                /railmap route info <id>
                /railmap route status [id]
                /railmap route create <id> <名称>
                /railmap route rename <id> <名称>
                /railmap route color <id> <#RRGGBB>
                /railmap route width <id> <宽度>
                /railmap route auto-match <id> <true|false>
                /railmap route assign-nearest <id> [半径]
                /railmap route anchor-nearest <id> [半径]
                """;
    }

    private String stationUsage() {
        return """
                用法:
                /railmap station list
                /railmap station info <id>
                /railmap station add <id> <名称> [半径]
                /railmap station set-area-here <id> [半径]
                /railmap station remove <id>
                """;
    }

    private ParsedNameAndRadius parseNameAndRadius(String[] args, int startIndex, double defaultRadius) {
        double radius = defaultRadius;
        int endExclusive = args.length;
        if (args.length > startIndex + 1) {
            try {
                radius = Double.parseDouble(args[args.length - 1]);
                endExclusive = args.length - 1;
            } catch (NumberFormatException ignored) {
                endExclusive = args.length;
            }
        }

        if (endExclusive <= startIndex) {
            endExclusive = args.length;
            radius = defaultRadius;
        }

        return new ParsedNameAndRadius(joinArgs(args, startIndex, endExclusive), radius);
    }

    private double defaultStationRadius() {
        return getConfig().getDouble("stations.default-radius", 24.0);
    }

    private String joinArgs(String[] args, int startIndex) {
        return joinArgs(args, startIndex, args.length);
    }

    private String joinArgs(String[] args, int startIndex, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < endExclusive; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }

            builder.append(args[i]);
        }

        return builder.toString();
    }

    private record ParsedNameAndRadius(String name, double radius) {
    }
}
