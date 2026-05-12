package dev.kokomi.bluemaprailway;

import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class BlueMapRailwayPlugin extends JavaPlugin {

    private RailwayService railwayService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        railwayService = new RailwayService(this);

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

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            railwayService.reload();
            sender.sendMessage("BlueMapRailway configuration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("rescan")) {
            railwayService.requestFullRescan();
            sender.sendMessage("BlueMapRailway full rescan queued.");
            return true;
        }

        return false;
    }
}
