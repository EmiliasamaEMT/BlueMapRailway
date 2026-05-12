# BlueMapRailway

BlueMapRailway is a Paper plugin planned to display vanilla Minecraft rail blocks on BlueMap as a lightweight marker overlay.

Supported rail blocks:

- `RAIL`
- `POWERED_RAIL`
- `DETECTOR_RAIL`
- `ACTIVATOR_RAIL`

## Development Direction

- Wait for `BlueMapAPI` to become available.
- Scan configured server worlds in controlled background batches.
- Build rail topology from Bukkit `Rail.Shape` data.
- Merge continuous rail sections into BlueMap `LineMarker`s.
- Update affected sections when rail blocks are placed, broken, or changed.

## Admin Commands

- `/railmap status`
- `/railmap reload`
- `/railmap rescan`

These commands are intended for server operators. Normal operation should be automatic.
