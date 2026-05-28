package io.github.emiliasamaemt.bluemaprailway.fabric;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FabricRailwayBackupService {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FabricRailwayLogger log;
    private final Object backupLock = new Object();
    private ScheduledExecutorService executor;
    private FabricRailwayConfig config;
    private boolean backupRunning;

    public FabricRailwayBackupService(FabricRailwayLogger log) {
        this.log = log;
    }

    public synchronized void start(FabricRailwayConfig config) {
        stop();
        this.config = config;
        if (!config.backup().enabled()) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BlueMapRailway Fabric Backup");
            thread.setDaemon(true);
            return thread;
        });
        executor.schedule(this::runScheduledCheck, 10, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(this::runScheduledCheck, 60, 60, TimeUnit.MINUTES);
        log.info("Railway data backup scheduled every " + configuredIntervalHours() + " hours.");
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized void reload(FabricRailwayConfig config) {
        start(config);
    }

    public String createBackupNow() {
        synchronized (backupLock) {
            if (backupRunning) {
                return "A backup task is already running.";
            }
            backupRunning = true;
        }

        try {
            BackupResult result = createBackup();
            if (result == null) {
                return "No railway data files are available to back up.";
            }
            if (result.failed()) {
                return "Failed to create backup: " + result.errorMessage();
            }
            return "Created backup: " + result.relativePath();
        } finally {
            synchronized (backupLock) {
                backupRunning = false;
            }
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
        if (config == null || !config.backup().enabled()) {
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
                    ZipEntry entry = new ZipEntry(FabricRailwayConfigLoader.dataDirectory().relativize(source)
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
            String relativePath = FabricRailwayConfigLoader.dataDirectory().relativize(target).toString().replace('\\', '/');
            log.info("Created railway data backup: " + relativePath);
            return BackupResult.success(relativePath);
        } catch (IOException exception) {
            log.warning("Failed to create railway data backup: " + exception.getMessage());
            return BackupResult.failure(exception.getMessage());
        }
    }

    private List<Path> sourceFiles() {
        List<Path> sources = new ArrayList<>();
        addIfRegularFile(sources, FabricRailwayConfigLoader.dataDirectory().resolve("routes.yml"));
        addIfRegularFile(sources, FabricRailwayConfigLoader.dataDirectory().resolve("stations.yml"));
        addIfRegularFile(sources, FabricRailwayConfigLoader.dataDirectory().resolve("edits.yml"));
        if (config != null && config.backup().includeConfig()) {
            addIfRegularFile(sources, FabricRailwayConfigLoader.configFile());
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
            log.warning("Failed to inspect railway backup directory: " + exception.getMessage());
            return null;
        }
    }

    private FileTime lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            log.warning("Failed to read backup timestamp: " + path.getFileName());
            return null;
        }
    }

    private void trimOldBackups(Path directory) throws IOException {
        int maxFiles = config == null ? 0 : config.backup().maxFiles();
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
        String configured = config == null ? "backups" : config.backup().directory();
        return FabricRailwayConfigLoader.dataDirectory().resolve(configured == null || configured.isBlank() ? "backups" : configured);
    }

    private int configuredIntervalHours() {
        return config == null ? 24 : Math.max(1, config.backup().intervalHours());
    }

    private record BackupResult(String relativePath, String errorMessage) {
        private static BackupResult success(String relativePath) {
            return new BackupResult(relativePath, "");
        }

        private static BackupResult failure(String errorMessage) {
            return new BackupResult("", errorMessage == null ? "Unknown error" : errorMessage);
        }

        private boolean failed() {
            return !errorMessage.isBlank();
        }
    }
}
