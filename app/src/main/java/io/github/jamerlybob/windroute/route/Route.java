package io.github.jamerlybob.windroute.route;

import java.util.List;

/** A cycling route as returned by the routing service. */
public final class Route {
    public final List<GeoPoint> points;
    public final double distanceMeters;
    /** Google's own estimate of the riding time, which already allows for hills. */
    public final long durationSeconds;
    /** Notices Google requires apps to show alongside a cycling route. */
    public final List<String> warnings;

    public Route(List<GeoPoint> points, double distanceMeters, long durationSeconds,
                 List<String> warnings) {
        this.points = points;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.warnings = warnings;
    }
}
