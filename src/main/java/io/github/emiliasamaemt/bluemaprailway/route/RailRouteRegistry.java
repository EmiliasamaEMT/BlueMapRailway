package io.github.emiliasamaemt.bluemaprailway.route;

import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RailRouteRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private List<RailRoute> routes;
    private Map<String, RailRoute> routesByComponentId;
    private RouteDiagnostics lastDiagnostics;

    private RailRouteRegistry(JavaPlugin plugin, File file, List<RailRoute> routes) {
        this.plugin = plugin;
        this.file = file;
        this.routes = routes;
        this.routesByComponentId = buildComponentIndex(routes);
        this.lastDiagnostics = RouteDiagnostics.empty();
    }

    public static RailRouteRegistry load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "routes.yml");
        ensureDefaultFile(file, plugin);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection routesSection = configuration.getConfigurationSection("routes");
        if (routesSection == null) {
            return new RailRouteRegistry(plugin, file, List.of());
        }

        List<RailRoute> routes = routesSection.getKeys(false).stream()
                .map(routeId -> readRoute(routesSection, routeId))
                .toList();

        return new RailRouteRegistry(plugin, file, List.copyOf(routes));
    }

    public RailScanResult apply(RailScanResult result) {
        RouteDiagnostics diagnostics = resolveAutoMatches(result);
        lastDiagnostics = diagnostics;

        List<RailLine> lines = result.lines().stream()
                .map(this::apply)
                .toList();

        return new RailScanResult(
                result.nodes(),
                result.components(),
                lines,
                result.scannedChunks(),
                result.cachedChunks(),
                result.cachedRails(),
                result.hiddenLineCount()
        );
    }

    public int routeCount() {
        return routes.size();
    }

    public List<RailRoute> routes() {
        return routes;
    }

    public RailRoute route(String id) {
        for (RailRoute route : routes) {
            if (route.id().equals(id)) {
                return route;
            }
        }

        return null;
    }

    public int assignedComponentCount() {
        return routesByComponentId.size();
    }

    public String status(RailScanResult result, String routeId) {
        if (routes.isEmpty()) {
            return "routes.yml 中还没有线路。";
        }

        if (result == null) {
            return "还没有扫描结果，无法诊断线路匹配状态。";
        }

        if (routeId != null) {
            RailRoute route = route(routeId);
            if (route == null) {
                return "线路不存在: " + routeId;
            }

            return routeStatus(route, result);
        }

        StringBuilder builder = new StringBuilder("线路匹配状态:");
        for (RailRoute route : routes) {
            builder.append('\n').append(routeStatusLine(route, result));
        }

        return builder.toString();
    }

    private RouteDiagnostics resolveAutoMatches(RailScanResult result) {
        Map<String, RailComponent> componentsById = new HashMap<>();
        for (RailComponent component : result.components()) {
            componentsById.put(component.id(), component);
        }

        Map<String, Set<String>> exactMatches = new HashMap<>();
        Set<String> exactComponentIds = new HashSet<>();
        for (RailRoute route : routes) {
            for (String componentId : route.componentIds()) {
                if (componentsById.containsKey(componentId)) {
                    exactMatches.computeIfAbsent(route.id(), ignored -> new LinkedHashSet<>()).add(componentId);
                    exactComponentIds.add(componentId);
                }
            }
        }

        Map<String, List<AutoCandidate>> candidatesByComponent = new HashMap<>();
        if (plugin.getConfig().getBoolean("routes.auto-match.enabled", true)) {
            for (RailRoute route : routes) {
                if (!route.autoMatch() || exactMatches.containsKey(route.id())) {
                    continue;
                }

                bestCandidate(route, result.components(), exactComponentIds)
                        .ifPresent(candidate -> candidatesByComponent
                                .computeIfAbsent(candidate.component().id(), ignored -> new ArrayList<>())
                                .add(candidate));
            }
        }

        Map<String, Set<String>> autoMatches = new HashMap<>();
        Map<String, Set<String>> conflicts = new HashMap<>();
        boolean changed = false;
        YamlConfiguration configuration = null;

        for (Map.Entry<String, List<AutoCandidate>> entry : candidatesByComponent.entrySet()) {
            List<AutoCandidate> candidates = entry.getValue();
            if (candidates.size() != 1) {
                for (AutoCandidate candidate : candidates) {
                    conflicts.computeIfAbsent(candidate.route().id(), ignored -> new LinkedHashSet<>())
                            .add(entry.getKey());
                }
                continue;
            }

            AutoCandidate candidate = candidates.getFirst();
            autoMatches.computeIfAbsent(candidate.route().id(), ignored -> new LinkedHashSet<>())
                    .add(candidate.component().id());
            addComponentToRoute(candidate.route().id(), candidate.component());

            if (configuration == null) {
                configuration = YamlConfiguration.loadConfiguration(file);
            }
            appendAutoMatchToFile(configuration, candidate.route(), candidate.component());
            changed = true;
        }

        if (changed && configuration != null) {
            save(configuration);
            rebuildRoutesByComponentId();
        }

        return new RouteDiagnostics(exactMatches, autoMatches, conflicts);
    }

    private java.util.Optional<AutoCandidate> bestCandidate(
            RailRoute route,
            List<RailComponent> components,
            Set<String> exactComponentIds
    ) {
        double anchorRadius = Math.max(0.0, plugin.getConfig().getDouble("routes.auto-match.anchor-radius", 16.0));
        double anchorRadiusSquared = anchorRadius * anchorRadius;
        double minOverlapRatio = Math.max(0.0, plugin.getConfig().getDouble("routes.auto-match.min-bounds-overlap-ratio", 0.35));

        AutoCandidate best = null;
        for (RailComponent component : components) {
            if (exactComponentIds.contains(component.id()) || isShortComponent(component)) {
                continue;
            }

            double score = score(route, component, anchorRadiusSquared, minOverlapRatio);
            if (score <= 0) {
                continue;
            }

            AutoCandidate candidate = new AutoCandidate(route, component, score);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        return java.util.Optional.ofNullable(best);
    }

    private boolean isShortComponent(RailComponent component) {
        if (!plugin.getConfig().getBoolean("filters.hide-short-lines", true)) {
            return false;
        }

        int maxPoints = Math.max(0, plugin.getConfig().getInt("filters.short-line-max-points", 3));
        double maxLength = Math.max(0.0, plugin.getConfig().getDouble("filters.short-line-max-length", 6.0));
        return component.pointCount() <= maxPoints || component.length() <= maxLength;
    }

    private double score(RailRoute route, RailComponent component, double anchorRadiusSquared, double minOverlapRatio) {
        double score = 0.0;
        for (RailRouteAnchor anchor : route.anchors()) {
            if (!anchor.worldName().equals(component.worldName())) {
                continue;
            }

            boolean nearAnchor = component.positions().stream()
                    .anyMatch(position -> anchor.distanceSquared(position) <= anchorRadiusSquared);
            if (nearAnchor) {
                score += 2.0;
                break;
            }
        }

        if (route.bounds() != null) {
            double overlapRatio = route.bounds().overlapRatio(component);
            if (overlapRatio >= minOverlapRatio) {
                score += overlapRatio;
            }
        }

        return score;
    }

    private RailLine apply(RailLine line) {
        RailRoute route = routesByComponentId.get(line.componentId());
        if (route == null) {
            return line;
        }

        return line.withRoute(route.id(), route.name(), route.color(), route.lineWidth());
    }

    private String routeStatus(RailRoute route, RailScanResult result) {
        StringBuilder builder = new StringBuilder(routeStatusLine(route, result));
        builder.append('\n').append("  颜色: ").append(route.color() == null ? "默认按铁轨类型" : route.color());
        builder.append('\n').append("  线宽: ").append(route.lineWidth() > 0 ? route.lineWidth() : "默认");
        builder.append('\n').append("  自动延续: ").append(route.autoMatch() ? "开启" : "关闭");
        builder.append('\n').append("  锚点数: ").append(route.anchors().size());
        if (route.bounds() != null) {
            builder.append('\n').append("  范围: [")
                    .append(route.bounds().minX()).append(',').append(route.bounds().minY()).append(',').append(route.bounds().minZ())
                    .append(" -> ")
                    .append(route.bounds().maxX()).append(',').append(route.bounds().maxY()).append(',').append(route.bounds().maxZ())
                    .append(']');
        }

        Set<String> conflicts = lastDiagnostics.conflicts().getOrDefault(route.id(), Set.of());
        if (!conflicts.isEmpty()) {
            builder.append('\n').append("  冲突候选:");
            conflicts.forEach(componentId -> builder.append('\n').append("  - ").append(componentId));
        }

        return builder.toString();
    }

    private String routeStatusLine(RailRoute route, RailScanResult result) {
        Set<String> currentComponentIds = new HashSet<>();
        for (RailComponent component : result.components()) {
            currentComponentIds.add(component.id());
        }

        int exactCount = 0;
        int missingCount = 0;
        for (String componentId : route.componentIds()) {
            if (currentComponentIds.contains(componentId)) {
                exactCount++;
            } else {
                missingCount++;
            }
        }

        int autoCount = lastDiagnostics.autoMatches().getOrDefault(route.id(), Set.of()).size();
        int conflictCount = lastDiagnostics.conflicts().getOrDefault(route.id(), Set.of()).size();
        long visibleLines = result.lines().stream()
                .filter(line -> route.id().equals(line.routeId()))
                .count();

        return "- " + route.id() + " / " + route.name() +
                " / 精确=" + exactCount +
                " / 自动=" + autoCount +
                " / 丢失=" + missingCount +
                " / 冲突=" + conflictCount +
                " / 可见线段=" + visibleLines;
    }

    private void appendAutoMatchToFile(YamlConfiguration configuration, RailRoute route, RailComponent component) {
        ConfigurationSection routesSection = configuration.getConfigurationSection("routes");
        if (routesSection == null) {
            routesSection = configuration.createSection("routes");
        }

        String path = route.id() + ".";
        List<String> componentIds = new ArrayList<>(routesSection.getStringList(path + "components"));
        if (!componentIds.contains(component.id())) {
            componentIds.add(component.id());
        }
        routesSection.set(path + "components", componentIds);
        appendAnchor(routesSection, route.id(), representativeAnchor(component));
        writeBounds(routesSection, route.id(), RailRouteBounds.of(component));
    }

    private static RailRoute readRoute(ConfigurationSection routesSection, String routeId) {
        String path = routeId + ".";
        String name = routesSection.getString(path + "name", routeId);
        String color = routesSection.getString(path + "color", null);
        int lineWidth = routesSection.getInt(path + "line-width", -1);
        Set<String> componentIds = new LinkedHashSet<>(routesSection.getStringList(path + "components"));
        List<RailRouteAnchor> anchors = readAnchors(routesSection, path + "anchors");
        RailRouteBounds bounds = readBounds(routesSection, path + "bounds");
        boolean autoMatch = routesSection.getBoolean(path + "auto-match", true);
        return new RailRoute(routeId, name, color, lineWidth, Set.copyOf(componentIds), List.copyOf(anchors), bounds, autoMatch);
    }

    private static List<RailRouteAnchor> readAnchors(ConfigurationSection section, String path) {
        List<RailRouteAnchor> anchors = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList(path)) {
            Object world = map.get("world");
            Object x = map.get("x");
            Object y = map.get("y");
            Object z = map.get("z");
            if (world instanceof String worldName && x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                anchors.add(new RailRouteAnchor(worldName, nx.intValue(), ny.intValue(), nz.intValue()));
            }
        }
        return anchors;
    }

    private static RailRouteBounds readBounds(ConfigurationSection section, String path) {
        String world = section.getString(path + ".world", null);
        List<Integer> min = section.getIntegerList(path + ".min");
        List<Integer> max = section.getIntegerList(path + ".max");
        if (world == null || min.size() < 3 || max.size() < 3) {
            return null;
        }

        return new RailRouteBounds(world, min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
    }

    private static void appendAnchor(ConfigurationSection routesSection, String routeId, RailRouteAnchor anchor) {
        String path = routeId + ".anchors";
        List<Map<String, Object>> anchors = new ArrayList<>();
        for (Map<?, ?> existing : routesSection.getMapList(path)) {
            anchors.add(new LinkedHashMap<>(stringKeyMap(existing)));
        }

        Map<String, Object> serialized = serializeAnchor(anchor);
        if (!anchors.contains(serialized)) {
            anchors.add(serialized);
        }

        routesSection.set(path, anchors);
    }

    private static void writeBounds(ConfigurationSection routesSection, String routeId, RailRouteBounds bounds) {
        String path = routeId + ".bounds.";
        routesSection.set(path + "world", bounds.worldName());
        routesSection.set(path + "min", List.of(bounds.minX(), bounds.minY(), bounds.minZ()));
        routesSection.set(path + "max", List.of(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }

    private static Map<String, Object> serializeAnchor(RailRouteAnchor anchor) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("world", anchor.worldName());
        serialized.put("x", anchor.x());
        serialized.put("y", anchor.y());
        serialized.put("z", anchor.z());
        return serialized;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static RailRouteAnchor representativeAnchor(RailComponent component) {
        if (component.positions().isEmpty()) {
            return new RailRouteAnchor(component.worldName(), component.minX(), component.minY(), component.minZ());
        }

        RailPosition position = component.positions().get(component.positions().size() / 2);
        return RailRouteAnchor.of(position);
    }

    private void addComponentToRoute(String routeId, RailComponent component) {
        List<RailRoute> updated = new ArrayList<>(routes);
        for (int i = 0; i < updated.size(); i++) {
            RailRoute route = updated.get(i);
            if (route.id().equals(routeId)) {
                updated.set(i, route.withAutoMatchedComponent(
                        component.id(),
                        representativeAnchor(component),
                        RailRouteBounds.of(component)
                ));
                break;
            }
        }
        routes = List.copyOf(updated);
    }

    private void rebuildRoutesByComponentId() {
        routesByComponentId = buildComponentIndex(routes);
    }

    private static Map<String, RailRoute> buildComponentIndex(List<RailRoute> routes) {
        Map<String, RailRoute> byComponentId = new HashMap<>();
        for (RailRoute route : routes) {
            for (String componentId : route.componentIds()) {
                byComponentId.put(componentId, route);
            }
        }
        return Map.copyOf(byComponentId);
    }

    private void save(YamlConfiguration configuration) {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "保存 routes.yml 失败: " + exception.getMessage(), exception);
        }
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

                # 在扫描后，通过 /railmap debug 查看 component ID，或使用 /railmap route assign-nearest 自动绑定。
                routes:
                  # main-line:
                  #   name: "主线"
                  #   color: "#22c55e"
                  #   line-width: 6
                  #   auto-match: true
                  #   components:
                  #     - "world:component:example"
                  #   anchors:
                  #     - world: "world"
                  #       x: 0
                  #       y: 64
                  #       z: 0
                  #   bounds:
                  #     world: "world"
                  #     min: [0, 64, 0]
                  #     max: [100, 70, 100]
                """;
    }

    private record AutoCandidate(RailRoute route, RailComponent component, double score) {
    }

    private record RouteDiagnostics(
            Map<String, Set<String>> exactMatches,
            Map<String, Set<String>> autoMatches,
            Map<String, Set<String>> conflicts
    ) {
        private static RouteDiagnostics empty() {
            return new RouteDiagnostics(Map.of(), Map.of(), Map.of());
        }
    }
}
