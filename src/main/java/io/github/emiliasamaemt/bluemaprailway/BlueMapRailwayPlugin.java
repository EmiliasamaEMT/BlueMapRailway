package io.github.emiliasamaemt.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
}
