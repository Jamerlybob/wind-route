package io.github.jamerlybob.windroute.route;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * The same road geometry ridden from finish to start.
     *
     * <p>This is deliberately not another routing request. It lets the app give
     * a useful return-trip comparison without spending the user's Routes quota.
     */
    public Route reversed() {
        List<GeoPoint> reversedPoints = new ArrayList<>(points);
        Collections.reverse(reversedPoints);
        return new Route(reversedPoints, distanceMeters, durationSeconds,
                new ArrayList<>(warnings));
    }
}
