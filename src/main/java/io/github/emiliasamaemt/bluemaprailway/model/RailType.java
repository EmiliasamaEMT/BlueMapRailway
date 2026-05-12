package io.github.emiliasamaemt.bluemaprailway.model;

import org.bukkit.Material;

import java.util.Optional;

public enum RailType {
    RAIL(Material.RAIL, "rail"),
    POWERED_RAIL(Material.POWERED_RAIL, "powered-rail"),
    DETECTOR_RAIL(Material.DETECTOR_RAIL, "detector-rail"),
    ACTIVATOR_RAIL(Material.ACTIVATOR_RAIL, "activator-rail");

    private final Material material;
    private final String configKey;

    RailType(Material material, String configKey) {
        this.material = material;
        this.configKey = configKey;
    }

    public Material material() {
        return material;
    }

    public String configKey() {
        return configKey;
    }

    public static Optional<RailType> fromMaterial(Material material) {
        for (RailType type : values()) {
            if (type.material == material) {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }
}
