package io.github.emiliasamaemt.bluemaprailway.platform;

public interface PlatformLogger {

    void info(String message);

    void warning(String message);

    void error(String message, Throwable throwable);
}
