package io.github.emiliasamaemt.bluemaprailway.fabric;

import org.slf4j.Logger;

public final class FabricRailwayLogger {

    private final Logger logger;

    public FabricRailwayLogger(Logger logger) {
        this.logger = logger;
    }

    public void info(String message) {
        logger.info("[BlueMapRailway] {}", message);
    }

    public void warning(String message) {
        logger.warn("[BlueMapRailway] {}", message);
    }

    public void error(String message, Throwable throwable) {
        logger.error("[BlueMapRailway] " + message, throwable);
    }
}
