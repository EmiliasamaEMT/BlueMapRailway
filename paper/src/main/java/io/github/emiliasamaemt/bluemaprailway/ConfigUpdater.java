package io.github.emiliasamaemt.bluemaprailway;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class ConfigUpdater {

    private ConfigUpdater() {
    }

    public static List<String> addMissingDefaults(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        YamlConfiguration defaults = loadDefaults(plugin);
        List<String> addedPaths = new ArrayList<>();
        boolean hasCustomWorlds = config.isConfigurationSection("worlds");

        for (String path : defaults.getKeys(true)) {
            Object value = defaults.get(path);
            if (value instanceof ConfigurationSection) {
                continue;
            }

            if (hasCustomWorlds && path.startsWith("worlds.")) {
                continue;
            }

            if (!config.contains(path)) {
                config.set(path, value);
                addedPaths.add(path);
            }
        }

        if (!addedPaths.isEmpty()) {
            try {
                config.save(new File(plugin.getDataFolder(), "config.yml"));
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to save updated config.yml: " + exception.getMessage(), exception);
            }
        }

        return addedPaths;
    }

    private static YamlConfiguration loadDefaults(JavaPlugin plugin) {
        try (InputStream input = plugin.getResource("config.yml")) {
            if (input == null) {
                return new YamlConfiguration();
            }

            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to load default config.yml: " + exception.getMessage(), exception);
            return new YamlConfiguration();
        }
    }
}
