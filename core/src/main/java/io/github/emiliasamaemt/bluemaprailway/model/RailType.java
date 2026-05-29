package io.github.emiliasamaemt.bluemaprailway.model;

public enum RailType {
    RAIL("rail"),
    POWERED_RAIL("powered-rail"),
    DETECTOR_RAIL("detector-rail"),
    ACTIVATOR_RAIL("activator-rail");

    private final String configKey;

    RailType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
