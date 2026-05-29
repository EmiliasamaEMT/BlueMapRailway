package io.github.emiliasamaemt.bluemaprailway.fabric;

import io.github.emiliasamaemt.bluemaprailway.config.RailwayCoreConfig;
import io.github.emiliasamaemt.bluemaprailway.model.RailComponent;
import io.github.emiliasamaemt.bluemaprailway.model.RailLine;
import io.github.emiliasamaemt.bluemaprailway.model.RailPosition;
import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import io.github.emiliasamaemt.bluemaprailway.route.RailRoute;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteAnchor;
import io.github.emiliasamaemt.bluemaprailway.route.RailRouteBounds;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FabricRouteRegistry {

    private static final Yaml YAML = FabricYamlSupport.readerYaml();

    private List<RailRoute> routes;
    private Map<String, RailRoute> routesByComponentId;
    private RouteDiagnostics lastDiagnostics;

    private FabricRouteRegistry(List<RailRoute> routes) {
        this.routes = List.copyOf(routes);
        this.routesByComponentId = buildComponentIndex(routes);
        this.lastDiagnostics = RouteDiagnostics.empty();
    }

    public static FabricRouteRegistry load(FabricRailwayLogger log) {
        ensureDefaultFile(log);

        Path file = routesFile();
        if (!Files.exists(file)) {
            return new FabricRouteRegistry(List.of());
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = YAML.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return new FabricRouteRegistry(List.of());
            }

            Object routesSection = castMap(map).get("routes");
            if (!(routesSection instanceof Map<?, ?> routesMap)) {
                return new FabricRouteRegistry(List.of());
            }

            List<RailRoute> routes = new ArrayList<>();
            for (Map.Entry<String, Object> entry : castMap(routesMap).entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> routeMap)) {
                    continue;
                }
                routes.add(readRoute(entry.getKey(), castMap(routeMap)));
            }

            return new FabricRouteRegistry(routes);
        } catch (IOException | RuntimeException exception) {
            log.warning("Failed to read routes.yml, using empty routes: " + FabricYamlSupport.errorMessage(exception));
            return new FabricRouteRegistry(List.of());
        }
    }

    public RailScanResult apply(RailScanResult result, RailwayCoreConfig coreConfig) {
        RouteDiagnostics diagnostics = resolveAutoMatches(result, coreConfig);
        lastDiagnostics = diagnostics;

        if (routesByComponentId.isEmpty()) {
            return result;
        }

        List<RailLine> lines = result.lines().stream()
                .map(this::applyLine)
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

    public int assignedComponentCount() {
        return routesByComponentId.size();
    }

    public List<RailRoute> routes() {
        return routes;
    }

    public RailRoute route(String routeId) {
        for (RailRoute route : routes) {
            if (route.id().equals(routeId)) {
                return route;
            }
        }
        return null;
    }

    public void saveRoute(RailRoute route, FabricRailwayLogger log) {
        List<RailRoute> updated = new ArrayList<>();
        boolean replaced = false;
        for (RailRoute existing : routes) {
            if (existing.id().equals(route.id())) {
                updated.add(route);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) {
            updated.add(route);
        }
        writeRoutes(updated);
    }

    public boolean deleteRoute(String routeId, FabricRailwayLogger log) {
        List<RailRoute> updated = routes.stream()
                .filter(route -> !route.id().equals(routeId))
                .toList();
        if (updated.size() == routes.size()) {
            return false;
        }
        writeRoutes(updated);
        return true;
    }

    public String status(RailScanResult result, RailwayCoreConfig coreConfig, String routeId) {
        if (routes.isEmpty()) {
            return "routes.yml is empty.";
        }
        if (result == null) {
            return "No scan result available yet.";
        }
        if (routeId != null) {
            RailRoute route = route(routeId);
            if (route == null) {
                return "Route not found: " + routeId;
            }
            return routeStatus(route, result, coreConfig);
        }

        StringBuilder builder = new StringBuilder("Route match status:");
        for (RailRoute route : routes) {
            builder.append('\n').append(routeStatusLine(route, result));
        }
        return builder.toString();
    }

    private RouteDiagnostics resolveAutoMatches(RailScanResult result, RailwayCoreConfig coreConfig) {
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
        if (coreConfig.routeAutoMatch().enabled()) {
            for (RailRoute route : routes) {
                if (!route.autoMatch() || exactMatches.containsKey(route.id())) {
                    continue;
                }

                bestCandidate(route, result.components(), exactComponentIds, coreConfig)
                        .ifPresent(candidate -> candidatesByComponent
                                .computeIfAbsent(candidate.component().id(), ignored -> new ArrayList<>())
                                .add(candidate));
            }
        }

        Map<String, Set<String>> autoMatches = new HashMap<>();
        Map<String, Set<String>> conflicts = new HashMap<>();
        boolean changed = false;

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
            changed = true;
        }

        if (changed) {
            writeRoutes(routes);
        }

        return new RouteDiagnostics(exactMatches, autoMatches, conflicts);
    }

    private java.util.Optional<AutoCandidate> bestCandidate(
            RailRoute route,
            List<RailComponent> components,
            Set<String> exactComponentIds,
            RailwayCoreConfig coreConfig
    ) {
        double anchorRadius = coreConfig.routeAutoMatch().anchorRadius();
        double anchorRadiusSquared = anchorRadius * anchorRadius;
        double minOverlapRatio = coreConfig.routeAutoMatch().minBoundsOverlapRatio();

        AutoCandidate best = null;
        for (RailComponent component : components) {
            if (exactComponentIds.contains(component.id()) || isShortComponent(component, coreConfig)) {
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

    private boolean isShortComponent(RailComponent component, RailwayCoreConfig coreConfig) {
        if (!coreConfig.lineFilter().hideShortLines()) {
            return false;
        }

        int maxPoints = Math.max(0, coreConfig.lineFilter().shortLineMaxPoints());
        double maxLength = Math.max(0.0, coreConfig.lineFilter().shortLineMaxLength());
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

    private RailLine applyLine(RailLine line) {
        RailRoute route = routesByComponentId.get(line.componentId());
        if (route == null) {
            return line.withRoute(null, null, null, -1);
        }

        return line.withRoute(route.id(), route.name(), route.color(), route.lineWidth());
    }

    private String routeStatus(RailRoute route, RailScanResult result, RailwayCoreConfig coreConfig) {
        StringBuilder builder = new StringBuilder(routeStatusLine(route, result));
        builder.append('\n').append("  Color: ").append(route.color() == null ? "default" : route.color());
        builder.append('\n').append("  Width: ").append(route.lineWidth() > 0 ? route.lineWidth() : "default");
        builder.append('\n').append("  Auto match: ").append(route.autoMatch());
        builder.append('\n').append("  Anchors: ").append(route.anchors().size());
        if (route.bounds() != null) {
            builder.append('\n').append("  Bounds: [")
                    .append(route.bounds().minX()).append(',').append(route.bounds().minY()).append(',').append(route.bounds().minZ())
                    .append(" -> ")
                    .append(route.bounds().maxX()).append(',').append(route.bounds().maxY()).append(',').append(route.bounds().maxZ())
                    .append(']');
        }
        builder.append('\n').append("  Eligible for auto match: ")
                .append(route.autoMatch() && coreConfig.routeAutoMatch().enabled());

        Set<String> conflicts = lastDiagnostics.conflicts().getOrDefault(route.id(), Set.of());
        if (!conflicts.isEmpty()) {
            builder.append('\n').append("  Conflicts:");
            for (String componentId : conflicts) {
                builder.append('\n').append("  - ").append(componentId);
            }
        }

        return builder.toString();
    }

    private String routeStatusLine(RailRoute route, RailScanResult result) {
        int exactCount = lastDiagnostics.exactMatches().getOrDefault(route.id(), Set.of()).size();
        int missingCount = Math.max(0, route.componentIds().size() - exactCount);
        int autoCount = lastDiagnostics.autoMatches().getOrDefault(route.id(), Set.of()).size();
        int conflictCount = lastDiagnostics.conflicts().getOrDefault(route.id(), Set.of()).size();

        long visibleLines = result.lines().stream()
                .filter(line -> route.id().equals(line.routeId()))
                .count();

        return "- " + route.id() + " / " + route.name()
                + " / exact=" + exactCount
                + " / auto=" + autoCount
                + " / missing=" + missingCount
                + " / conflicts=" + conflictCount
                + " / visibleLines=" + visibleLines;
    }

    private static RailRoute readRoute(String routeId, Map<String, Object> routeMap) {
        String name = string(routeMap, "name", routeId);
        String color = nullableString(routeMap.get("color"));
        int lineWidth = integer(routeMap, "line-width", -1);
        boolean autoMatch = bool(routeMap, "auto-match", true);

        Set<String> componentIds = new LinkedHashSet<>();
        Object componentsValue = routeMap.get("components");
        if (componentsValue instanceof List<?> components) {
            for (Object component : components) {
                if (component instanceof String componentId) {
                    componentIds.add(componentId);
                }
            }
        }

        List<RailRouteAnchor> anchors = new ArrayList<>();
        Object anchorsValue = routeMap.get("anchors");
        if (anchorsValue instanceof List<?> anchorList) {
            for (Object anchorValue : anchorList) {
                if (!(anchorValue instanceof Map<?, ?> anchorMap)) {
                    continue;
                }

                Map<String, Object> typed = castMap(anchorMap);
                String world = nullableString(typed.get("world"));
                Number x = numberValue(typed.get("x"));
                Number y = numberValue(typed.get("y"));
                Number z = numberValue(typed.get("z"));
                if (world != null && x != null && y != null && z != null) {
                    anchors.add(new RailRouteAnchor(world, x.intValue(), y.intValue(), z.intValue()));
                }
            }
        }

        RailRouteBounds bounds = null;
        Object boundsValue = routeMap.get("bounds");
        if (boundsValue instanceof Map<?, ?> boundsMap) {
            Map<String, Object> typed = castMap(boundsMap);
            String world = nullableString(typed.get("world"));
            List<Integer> min = integerList(typed.get("min"));
            List<Integer> max = integerList(typed.get("max"));
            if (world != null && min.size() >= 3 && max.size() >= 3) {
                bounds = new RailRouteBounds(world, min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
            }
        }

        return new RailRoute(routeId, name, color, lineWidth, Set.copyOf(componentIds), List.copyOf(anchors), bounds, autoMatch);
    }

    private static Path routesFile() {
        return FabricRailwayConfigLoader.dataDirectory().resolve("routes.yml");
    }

    private void writeRoutes(List<RailRoute> updatedRoutes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> routesSection = new LinkedHashMap<>();
        for (RailRoute route : updatedRoutes) {
            Map<String, Object> routeMap = new LinkedHashMap<>();
            routeMap.put("name", route.name());
            if (route.color() != null && !route.color().isBlank()) {
                routeMap.put("color", route.color());
            }
            if (route.lineWidth() > 0) {
                routeMap.put("line-width", route.lineWidth());
            }
            routeMap.put("auto-match", route.autoMatch());
            routeMap.put("components", new ArrayList<>(route.componentIds().stream().sorted().toList()));

            if (!route.anchors().isEmpty()) {
                List<Map<String, Object>> anchors = new ArrayList<>();
                for (RailRouteAnchor anchor : route.anchors()) {
                    Map<String, Object> anchorMap = new LinkedHashMap<>();
                    anchorMap.put("world", anchor.worldName());
                    anchorMap.put("x", anchor.x());
                    anchorMap.put("y", anchor.y());
                    anchorMap.put("z", anchor.z());
                    anchors.add(anchorMap);
                }
                routeMap.put("anchors", anchors);
            }

            if (route.bounds() != null) {
                Map<String, Object> boundsMap = new LinkedHashMap<>();
                boundsMap.put("world", route.bounds().worldName());
                boundsMap.put("min", List.of(route.bounds().minX(), route.bounds().minY(), route.bounds().minZ()));
                boundsMap.put("max", List.of(route.bounds().maxX(), route.bounds().maxY(), route.bounds().maxZ()));
                routeMap.put("bounds", boundsMap);
            }

            routesSection.put(route.id(), routeMap);
        }
        root.put("routes", routesSection);

        try (Writer writer = Files.newBufferedWriter(routesFile(), StandardCharsets.UTF_8)) {
            yamlWriter().dump(root, writer);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save routes.yml: " + exception.getMessage(), exception);
        }

        routes = List.copyOf(updatedRoutes);
        routesByComponentId = buildComponentIndex(updatedRoutes);
    }

    private static void ensureDefaultFile(FabricRailwayLogger log) {
        Path file = routesFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                return;
            }

            try (InputStream input = FabricRouteRegistry.class.getClassLoader().getResourceAsStream("routes.yml")) {
                if (input == null) {
                    return;
                }
                Files.copy(input, file);
            }
        } catch (IOException exception) {
            log.warning("Failed to create default routes.yml: " + exception.getMessage());
        }
    }

    private static Yaml yamlWriter() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    private static Map<String, RailRoute> buildComponentIndex(List<RailRoute> routes) {
        Map<String, RailRoute> index = new LinkedHashMap<>();
        for (RailRoute route : routes) {
            for (String componentId : route.componentIds()) {
                index.put(componentId, route);
            }
        }
        return Map.copyOf(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        String value = nullableString(map.get(key));
        return value != null ? value : fallback;
    }

    private static String nullableString(Object value) {
        return value instanceof String string ? string : null;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Number value = numberValue(map.get(key));
        return value != null ? value.intValue() : fallback;
    }

    private static Number numberValue(Object value) {
        return value instanceof Number number ? number : null;
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return List.copyOf(result);
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

    private static RailRouteAnchor representativeAnchor(RailComponent component) {
        if (component.positions().isEmpty()) {
            return new RailRouteAnchor(component.worldName(), component.minX(), component.minY(), component.minZ());
        }

        RailPosition position = component.positions().get(component.positions().size() / 2);
        return RailRouteAnchor.of(position);
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
