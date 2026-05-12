package dev.kokomi.bluemaprailway.model;

public enum RailDirection {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    ASCENDING_NORTH(0, 1, -1),
    ASCENDING_EAST(1, 1, 0),
    ASCENDING_SOUTH(0, 1, 1),
    ASCENDING_WEST(-1, 1, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    RailDirection(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public RailPosition apply(RailPosition position) {
        return position.relative(dx, dy, dz);
    }

    public RailPosition applyReverse(RailPosition position) {
        return position.relative(-dx, -dy, -dz);
    }
}
