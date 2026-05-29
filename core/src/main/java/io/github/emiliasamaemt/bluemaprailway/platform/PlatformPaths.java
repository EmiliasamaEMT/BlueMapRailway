package io.github.emiliasamaemt.bluemaprailway.platform;

import java.nio.file.Path;

public interface PlatformPaths {

    Path dataDirectory();

    Path configFile();

    Path routesFile();

    Path stationsFile();

    Path editsFile();

    Path cacheFile();

    Path logsDirectory();

    Path backupsDirectory();

    Path adminWebDirectory();
}
