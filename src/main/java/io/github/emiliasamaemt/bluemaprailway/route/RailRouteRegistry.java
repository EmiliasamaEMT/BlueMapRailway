package io.github.emiliasamaemt.bluemaprailway.route;

import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RailRouteRegistry {

    private static final RailRouteRegistry EMPTY = new RailRouteRegistry(List.of(), Map.of());

    private final List<RailRoute> routes;
    private final Map<String, RailRoute> routesByComponentId;

    private RailRouteRegistry(List<RailRoute> routes, Map<String, RailRoute> routesByComponentId) {
        this.routes = routes;
        this.routesByComponentId = routesByComponentId;
    }

    public static RailRouteRegistry load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "routes.yml");
        ensureDefaultFile(file, plugin);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection routesSection = configuration.getConfigurationSection("routes");
        if (routesSection == null) {
            return EMPTY;
        }

        Map<String, RailRoute> byComponentId = new HashMap<>();
        List<RailRoute> routes = routesSection.getKeys(false).stream()
                .map(routeId -> readRoute(routesSection, routeId))
                .toList();

        for (RailRoute route : routes) {
            for (String componentId : route.componentIds()) {
                byComponentId.put(componentId, route);
            }
        }

        return new RailRouteRegistry(List.copyOf(routes), Map.copyOf(byComponentId));
    }

    public RailScanResult apply(RailScanResult result) {
        List<RailLine> lines = result.lines().stream()
                .map(this::apply)
                .toList();

        return new RailScanResult(
                result.nodes(),
                result.components(),
                lines,
                result.scannedChunks(),
                result.hiddenLineCount()
        );
    }

    public int routeCount() {
        return routes.size();
    }

    public int assignedComponentCount() {
        return routesByComponentId.size();
    }

    private RailLine apply(RailLine line) {
        RailRoute route = routesByComponentId.get(line.componentId());
        if (route == null) {
            return line;
        }

        return line.withRoute(route.id(), route.name(), route.color(), route.lineWidth());
    }

    private static RailRoute readRoute(ConfigurationSection routesSection, String routeId) {
        String path = routeId + ".";
        String name = routesSection.getString(path + "name", routeId);
        String color = routesSection.getString(path + "color", null);
        int lineWidth = routesSection.getInt(path + "line-width", -1);
        Set<String> componentIds = new HashSet<>(routesSection.getStringList(path + "components"));
        return new RailRoute(routeId, name, color, lineWidth, Set.copyOf(componentIds));
    }

    private static void ensureDefaultFile(File file, JavaPlugin plugin) {
        if (file.exists()) {
            return;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), defaultRoutesFile(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "无法创建 routes.yml: " + exception.getMessage(), exception);
        }
    }

    private static String defaultRoutesFile() {
        return """
                version: 1

                # 在扫描后，通过 /railmap debug 查看 component ID。
                # 然后把需要命名和改色的 component ID 填入对应线路。
                routes:
                  # main-line:
                  #   name: "主线"
                  #   color: "#22c55e"
                  #   line-width: 6
                  #   components:
                  #     - "world:component:example"
                """;
    }
}
