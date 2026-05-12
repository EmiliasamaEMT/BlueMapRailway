package io.github.emiliasamaemt.bluemaprailway.model;

import com.flowpowered.math.vector.Vector3d;

import java.util.List;

public record RailLine(String worldName, RailType type, boolean powered, List<Vector3d> points) {
}
