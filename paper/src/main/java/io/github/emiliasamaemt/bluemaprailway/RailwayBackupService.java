package io.github.emiliasamaemt.bluemaprailway;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RailwayBackupService {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long MINUTE_TICKS = 20L * 60L;
    private static final long HOUR_TICKS = MINUTE_TICKS * 60L;

    private final JavaPlugin plugin;
    private final PluginLog log;
    private final Object backupLock = new Object();
    private BukkitTask startupCheckTask;
    private BukkitTask periodicCheckTask;
    private boolean backupRunning;

    public RailwayBackupService(JavaPlugin plugin, PluginLog log) {
        this.plugin = plugin;
        this.log = log;
    }

    public synchronized void start() {
        stop();

        if (!plugin.getConfig().getBoolean("backup.enabled", true)) {
            return;
        }

        long checkPeriodTicks = checkPeriodTicks();
        startupCheckTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::runScheduledCheck, 200L);
        periodicCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::runScheduledCheck,
                checkPeriodTicks,
                checkPeriodTicks
        );
        log.info("Railway data backup scheduled every " + configuredIntervalHours() + " hours.");
    }

    public synchronized void reload() {
        start();
    }

    public String createBackupNow() {
        synchronized (backupLock) {
            if (backupRunning) {
                return "已有备份任务正在执行，请稍后再试。";
            }
            backupRunning = true;
        }

        try {
            BackupResult result = createBackup();
            if (result == null) {
                return "当前没有可备份的线路数据文件。";
            }
            if (result.failed()) {
                return "创建备份失败: " + result.errorMessage();
            }
            return "已创建备份: " + result.relativePath();
        } finally {
            synchronized (backupLock) {
                backupRunning = false;
            }
        }
    }

    public synchronized void stop() {
        if (startupCheckTask != null) {
            startupCheckTask.cancel();
            startupCheckTask = null;
        }

        if (periodicCheckTask != null) {
            periodicCheckTask.cancel();
            periodicCheckTask = null;
        }
    }

    private void runScheduledCheck() {
        synchronized (backupLock) {
            if (backupRunning) {
                return;
            }
            backupRunning = true;
        }

        try {
            backupIfDue();
        } finally {
            synchronized (backupLock) {
                backupRunning = false;
            }
        }
    }

    private void backupIfDue() {
        if (!plugin.getConfig().getBoolean("backup.enabled", true)) {
            return;
        }

        Instant latestBackup = latestBackupTime();
        Duration interval = Duration.ofHours(configuredIntervalHours());
        if (latestBackup != null && Duration.between(latestBackup, Instant.now()).compareTo(interval) < 0) {
            return;
        }

        createBackup();
    }

    private BackupResult createBackup() {
        try {
            Path backupDirectory = backupDirectory();
            Files.createDirectories(backupDirectory);

            List<Path> sources = sourceFiles();
            if (sources.isEmpty()) {
                return null;
            }

            Path target = backupDirectory.resolve("railway-backup-" +
                    FILE_NAME_FORMATTER.format(LocalDateTime.now()) + ".zip");
            try (OutputStream output = Files.newOutputStream(target);
                 ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Path source : sources) {
                    ZipEntry entry = new ZipEntry(plugin.getDataFolder().toPath().relativize(source)
                            .toString()
                            .replace('\\', '/'));
                    zip.putNextEntry(entry);
                    try (InputStream input = Files.newInputStream(source)) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }

            trimOldBackups(backupDirectory);
            String relativePath = plugin.getDataFolder().toPath().relativize(target).toString().replace('\\', '/');
            log.info("Created railway data backup: " + relativePath);
            return BackupResult.success(relativePath);
        } catch (IOException exception) {
            log.warning("Failed to create railway data backup: " + exception.getMessage(), exception);
            return BackupResult.failure(exception.getMessage());
        }
    }

    private List<Path> sourceFiles() {
        List<Path> sources = new ArrayList<>();
        addIfRegularFile(sources, plugin.getDataFolder().toPath().resolve("routes.yml"));
        addIfRegularFile(sources, plugin.getDataFolder().toPath().resolve("stations.yml"));
        addIfRegularFile(sources, plugin.getDataFolder().toPath().resolve("edits.yml"));

        if (plugin.getConfig().getBoolean("backup.include-config", true)) {
            addIfRegularFile(sources, plugin.getDataFolder().toPath().resolve("config.yml"));
        }

        return sources;
    }

    private void addIfRegularFile(List<Path> sources, Path file) {
        if (Files.isRegularFile(file)) {
            sources.add(file);
        }
    }

    private Instant latestBackupTime() {
        Path directory = backupDirectory();
        if (!Files.isDirectory(directory)) {
            return null;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .map(this::lastModifiedTime)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .map(FileTime::toInstant)
                    .orElse(null);
        } catch (IOException exception) {
            log.warning("Failed to inspect railway backup directory: " + exception.getMessage(), exception);
            return null;
        }
    }

    private FileTime lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            log.warning("Failed to read backup timestamp: " + path.getFileName(), exception);
            return null;
        }
    }

    private void trimOldBackups(Path directory) throws IOException {
        int maxFiles = plugin.getConfig().getInt("backup.max-files", 0);
        if (maxFiles <= 0) {
            return;
        }

        List<Path> backups;
        try (Stream<Path> stream = Files.list(directory)) {
            backups = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(this::lastModifiedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }

        for (int index = maxFiles; index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index));
        }
    }

    private Path backupDirectory() {
        String configured = plugin.getConfig().getString("backup.directory", "backups");
        return plugin.getDataFolder().toPath().resolve(configured == null || configured.isBlank() ? "backups" : configured);
    }

    private int configuredIntervalHours() {
        return Math.max(1, plugin.getConfig().getInt("backup.interval-hours", 24));
    }

    private long checkPeriodTicks() {
        long intervalTicks = configuredIntervalHours() * HOUR_TICKS;
        return Math.max(MINUTE_TICKS, Math.min(intervalTicks, HOUR_TICKS));
    }

    private record BackupResult(String relativePath, String errorMessage) {

        private static BackupResult success(String relativePath) {
            return new BackupResult(relativePath, "");
        }

        private static BackupResult failure(String errorMessage) {
            return new BackupResult("", errorMessage == null ? "未知错误" : errorMessage);
        }

        private boolean failed() {
            return !errorMessage.isBlank();
        }
    }
}
