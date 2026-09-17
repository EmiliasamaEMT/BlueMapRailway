package io.github.emiliasamaemt.bluemaprailway.edit;

import io.github.emiliasamaemt.bluemaprailway.model.RailScanResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class RailEditRegistry {

    private final List<RailEditMask> masks;
    private final List<RailEditHideRule> hiddenLines;

    private RailEditRegistry(List<RailEditMask> masks, List<RailEditHideRule> hiddenLines) {
        this.masks = List.copyOf(masks);
        this.hiddenLines = List.copyOf(hiddenLines);
    }

    public static RailEditRegistry load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "edits.yml");
        ensureDefaultFile(file, plugin);

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection masksSection = configuration.getConfigurationSection("masks");
        List<RailEditMask> masks = masksSection == null
                ? List.of()
                : masksSection.getKeys(false).stream()
                .map(maskId -> readMask(masksSection, maskId))
                .filter(mask -> mask != null)
                .toList();
        ConfigurationSection hiddenLinesSection = configuration.getConfigurationSection("hidden-lines");
        List<RailEditHideRule> hiddenLines = hiddenLinesSection == null
                ? List.of()
                : hiddenLinesSection.getKeys(false).stream()
                .map(ruleId -> readHideRule(hiddenLinesSection, ruleId))
                .filter(rule -> rule != null)
                .toList();
        return new RailEditRegistry(masks, hiddenLines);
    }

    public List<RailEditMask> masks() {
        return masks;
    }

    public List<RailEditHideRule> hiddenLines() {
        return hiddenLines;
    }

    public int maskCount() {
        return masks.size();
    }

    public int hiddenLineCount() {
        return hiddenLines.size();
    }

    public RailScanResult apply(RailScanResult result) {
        return RailEditProcessor.apply(result, masks, hiddenLines);
    }

    private static RailEditMask readMask(ConfigurationSection section, String maskId) {
        String path = maskId + ".";
        String name = section.getString(path + "name", maskId);
        String world = section.getString(path + "world", "world");
        boolean enabled = section.getBoolean(path + "enabled", true);
        List<Integer> min = section.getIntegerList(path + "area.min");
        List<Integer> max = section.getIntegerList(path + "area.max");
        if (min.size() < 3 || max.size() < 3) {
            return null;
        }

        return new RailEditMask(
                maskId,
                name,
                world,
                enabled,
                min.get(0),
                min.get(1),
                min.get(2),
                max.get(0),
                max.get(1),
                max.get(2)
        );
    }

    private static RailEditHideRule readHideRule(ConfigurationSection section, String ruleId) {
        String path = ruleId + ".";
        String name = section.getString(path + "name", ruleId);
        boolean enabled = section.getBoolean(path + "enabled", true);
        Set<String> routeIds = new LinkedHashSet<>(section.getStringList(path + "route-ids"));
        Set<String> componentIds = new LinkedHashSet<>(section.getStringList(path + "component-ids"));
        if (routeIds.isEmpty() && componentIds.isEmpty()) {
            return null;
        }

        return new RailEditHideRule(ruleId, name, enabled, routeIds, componentIds);
    }

    private static void ensureDefaultFile(File file, JavaPlugin plugin) {
        if (file.exists()) {
            return;
        }

        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), defaultEditsFile(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "无法创建 edits.yml: " + exception.getMessage(), exception);
        }
    }

    private static String defaultEditsFile() {
        return """
                version: 1

                masks:
                  # machine-rail:
                  #   name: "机房误识别铁轨"
                  #   world: "world"
                  #   enabled: true
                  #   area:
                  #     type: box
                  #     min: [120, 0, -30]
                  #     max: [170, 320, 20]

                hidden-lines:
                  # hide-main-line:
                  #   name: "隐藏主线"
                  #   enabled: true
                  #   route-ids:
                  #     - "main-line"
                  #   component-ids: []
                """;
    }

}
