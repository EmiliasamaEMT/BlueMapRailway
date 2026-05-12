package io.github.emiliasamaemt.bluemaprailway.route;

import java.util.Set;

public record RailRoute(
        String id,
        String name,
        String color,
        int lineWidth,
        Set<String> componentIds
) {
}
