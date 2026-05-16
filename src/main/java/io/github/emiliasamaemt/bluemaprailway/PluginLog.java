package io.github.emiliasamaemt.bluemaprailway;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

public final class PluginLog {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;

    public PluginLog(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void info(String message) {
        write("INFO", message, null);
        if (plugin.getConfig().getBoolean("logging.console-info", false)) {
            plugin.getLogger().info(message);
        }
    }

    public void warning(String message) {
        write("WARN", message, null);
        plugin.getLogger().warning(message);
    }

    public void warning(String message, Throwable throwable) {
        write("WARN", message, throwable);
        plugin.getLogger().log(Level.WARNING, message, throwable);
    }

    public String tail(int requestedLines) {
        Path file = logFile();
        if (!Files.isRegularFile(file)) {
            return "BlueMapRailway log file does not exist yet: " + file;
        }

        int lines = Math.max(1, Math.min(requestedLines, 50));
        try {
            List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int fromIndex = Math.max(0, allLines.size() - lines);
            return String.join("\n", allLines.subList(fromIndex, allLines.size()));
        } catch (IOException exception) {
            return "Failed to read BlueMapRailway log: " + exception.getMessage();
        }
    }

    private synchronized void write(String level, String message, Throwable throwable) {
        if (!plugin.getConfig().getBoolean("logging.file.enabled", true)) {
            return;
        }

        try {
            Path file = logFile();
            Files.createDirectories(file.getParent());
            StringBuilder builder = new StringBuilder();
            builder.append('[')
                    .append(LocalDateTime.now().format(FORMATTER))
                    .append("] [")
                    .append(level)
                    .append("] ")
                    .append(message)
                    .append(System.lineSeparator());
            if (throwable != null) {
                builder.append(throwable).append(System.lineSeparator());
            }
            Files.writeString(
                    file,
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to write BlueMapRailway log: " + exception.getMessage(), exception);
        }
    }

    private Path logFile() {
        String configured = plugin.getConfig().getString("logging.file.path", "logs/latest.log");
        return plugin.getDataFolder().toPath().resolve(configured == null || configured.isBlank() ? "logs/latest.log" : configured);
    }
}
