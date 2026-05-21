package io.github.emiliasamaemt.bluemaprailway.station;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

public final class RailStationRegistry {

    private final List<RailStation> stations;

    private RailStationRegistry(List<RailStation> stations) {
        this.stations = stations;
    }

    public static RailStationRegistry load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "stations.yml");
        ensureDefaultFile(file, plugin);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection stationsSection = configuration.getConfigurationSection("stations");
        if (stationsSection == null) {
            return new RailStationRegistry(List.of());
        }

        List<RailStation> stations = stationsSection.getKeys(false).stream()
                .map(stationId -> readStation(stationsSection, stationId))
                .toList();

        return new RailStationRegistry(List.copyOf(stations));
    }

    public List<RailStation> stations() {
        return stations;
    }

    public int stationCount() {
        return stations.size();
    }

    private static RailStation readStation(ConfigurationSection stationsSection, String stationId) {
        String path = stationId + ".";
        String name = stationsSection.getString(path + "name", stationId);
        String world = stationsSection.getString(path + "world", "world");
        List<Integer> min = stationsSection.getIntegerList(path + "area.min");
        List<Integer> max = stationsSection.getIntegerList(path + "area.max");

        int minX = valueAt(min, 0);
        int minY = valueAt(min, 1);
        int minZ = valueAt(min, 2);
        int maxX = valueAt(max, 0);
        int maxY = valueAt(max, 1);
        int maxZ = valueAt(max, 2);

        return new RailStation(
                stationId,
                name,
                world,
                Math.min(minX, maxX),
                Math.min(minY, maxY),
                Math.min(minZ, maxZ),
                Math.max(minX, maxX),
                Math.max(minY, maxY),
                Math.max(minZ, maxZ)
        );
    }

    private static int valueAt(List<Integer> values, int index) {
        if (values.size() <= index) {
            return 0;
        }

        return values.get(index);
    }

    private static void ensureDefaultFile(File file, JavaPlugin plugin) {
        if (file.exists()) {
            return;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), defaultStationsFile(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "无法创建 stations.yml: " + exception.getMessage(), exception);
        }
    }

    private static String defaultStationsFile() {
        return """
                version: 1

                stations:
                  # spawn:
                  #   name: "出生点站"
                  #   world: "world"
                  #   area:
                  #     type: box
                  #     min: [120, 60, -30]
                  #     max: [170, 75, 20]
                """;
    }
}
