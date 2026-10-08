package io.github.jamerlybob.windroute.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A cycling route as returned by the routing service. */
public final class Route {
    public enum Source { GOOGLE, GPX }
    public final List<GeoPoint> points;
    public final double distanceMeters;
    /** Google's own estimate of the riding time, which already allows for hills. */
    public final long durationSeconds;
    /** Notices Google requires apps to show alongside a cycling route. */
    public final List<String> warnings;
    public final Source source;
    /** Empty for GPX imports, which contain geometry but no invented turns. */
    public final List<RouteStep> steps;

    public Route(List<GeoPoint> points, double distanceMeters, long durationSeconds,
                 List<String> warnings) {
        this(points, distanceMeters, durationSeconds, warnings, Source.GOOGLE,
                Collections.emptyList());
    }

    public Route(List<GeoPoint> points, double distanceMeters, long durationSeconds,
                 List<String> warnings, Source source) {
        this(points, distanceMeters, durationSeconds, warnings, source,
                Collections.emptyList());
    }

    public Route(List<GeoPoint> points, double distanceMeters, long durationSeconds,
                 List<String> warnings, Source source, List<RouteStep> steps) {
        this.points = points;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.warnings = warnings;
        this.source = source;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
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
                new ArrayList<>(warnings), source, reversedSteps());
    }

    /**
     * Returns the same route with the duration used by every ride analysis.
     *
     * <p>The geometry and Google's required warnings do not change when a rider
     * chooses a usual speed. Making a new immutable value here prevents one
     * feature from quietly falling back to Google's original duration.
     */
    public Route withDuration(long effectiveDurationSeconds) {
        return new Route(points, distanceMeters, effectiveDurationSeconds, warnings, source, steps);
    }

    private List<RouteStep> reversedSteps() {
        // Google's instructions do not describe the reverse manoeuvres. An
        // empty list is safer than speaking confidently wrong directions.
        return Collections.emptyList();
    }
}
