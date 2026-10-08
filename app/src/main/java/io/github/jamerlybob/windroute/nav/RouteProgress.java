package io.github.jamerlybob.windroute.nav;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** Locates a rider on, or near, a route polyline. */
public final class RouteProgress {

    public static final double OFF_ROUTE_THRESHOLD_METERS = 40.0;
    public static final double HINT_DISTANCE_TOLERANCE_METERS = 15.0;
    public static final double HINT_BACKTRACK_TOLERANCE_METERS = 30.0;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public final GeoPoint nearestPoint;
    /** Index of the segment containing nearestPoint. */
    public final int segmentIndex;
    public final double distanceAlongMeters;
    public final double distanceRemainingMeters;
    public final double distanceOffRouteMeters;
    public final boolean offRoute;

    private RouteProgress(Candidate candidate, double totalMeters) {
        this.nearestPoint = candidate.point;
        this.segmentIndex = candidate.segmentIndex;
        this.distanceAlongMeters = candidate.alongMeters;
        this.distanceRemainingMeters = Math.max(0, totalMeters - candidate.alongMeters);
        this.distanceOffRouteMeters = candidate.offMeters;
        this.offRoute = candidate.offMeters > OFF_ROUTE_THRESHOLD_METERS;
    }

    public static RouteProgress locate(List<GeoPoint> route, GeoPoint position) {
        return locate(route, position, Double.NaN);
    }

    public static RouteProgress locate(List<GeoPoint> route, GeoPoint position,
                                       double previousDistanceAlongMeters) {
        if (route.isEmpty()) {
            throw new IllegalArgumentException("A route needs at least one point.");
        }
        if (route.size() == 1) {
            Candidate only = new Candidate(route.get(0), 0, 0,
                    GeoMath.distanceMeters(position, route.get(0)));
            return new RouteProgress(only, 0);
        }

        double[] cumulative = GeoMath.cumulativeMeters(route);
        List<Candidate> candidates = new ArrayList<>();
        Candidate nearest = null;
        for (int i = 0; i < route.size() - 1; i++) {
            Candidate candidate = project(route.get(i), route.get(i + 1), position,
                    i, cumulative[i], cumulative[i + 1] - cumulative[i]);
            candidates.add(candidate);
            if (nearest == null || candidate.offMeters < nearest.offMeters) {
                nearest = candidate;
            }
        }

        if (Double.isFinite(previousDistanceAlongMeters)) {
            Candidate forward = null;
            Candidate slightBacktrack = null;
            for (Candidate candidate : candidates) {
                boolean geometricallyClose = candidate.offMeters
                        <= nearest.offMeters + HINT_DISTANCE_TOLERANCE_METERS;
                if (!geometricallyClose) {
                    continue;
                }
                if (candidate.alongMeters >= previousDistanceAlongMeters
                        && (forward == null || candidate.alongMeters < forward.alongMeters)) {
                    forward = candidate;
                } else if (candidate.alongMeters
                        >= previousDistanceAlongMeters - HINT_BACKTRACK_TOLERANCE_METERS
                        && (slightBacktrack == null
                        || candidate.alongMeters > slightBacktrack.alongMeters)) {
                    slightBacktrack = candidate;
                }
            }
            // At a crossing two segments can be equally close. Progress breaks
            // the tie in favour of the segment at, or just after, the last fix.
            if (forward != null) {
                nearest = forward;
            } else if (slightBacktrack != null) {
                nearest = slightBacktrack;
            }
        }
        return new RouteProgress(nearest, cumulative[cumulative.length - 1]);
    }

    private static Candidate project(GeoPoint start, GeoPoint end, GeoPoint position,
                                     int segmentIndex, double segmentStartMeters,
                                     double segmentLengthMeters) {
        // Over one route segment the equirectangular projection treats Earth as
        // flat. Its error is negligible over tens of metres, but it must not be
        // used as a replacement for spherical maths over hundreds of kilometres.
        double referenceLat = Math.toRadians(position.lat);
        double startX = Math.toRadians(start.lng - position.lng)
                * Math.cos(referenceLat) * EARTH_RADIUS_M;
        double startY = Math.toRadians(start.lat - position.lat) * EARTH_RADIUS_M;
        double endX = Math.toRadians(end.lng - position.lng)
                * Math.cos(referenceLat) * EARTH_RADIUS_M;
        double endY = Math.toRadians(end.lat - position.lat) * EARTH_RADIUS_M;
        double dx = endX - startX;
        double dy = endY - startY;
        double squaredLength = dx * dx + dy * dy;
        double fraction = squaredLength > 0
                ? -(startX * dx + startY * dy) / squaredLength : 0;
        fraction = Math.max(0, Math.min(1, fraction));
        double nearestX = startX + fraction * dx;
        double nearestY = startY + fraction * dy;
        GeoPoint nearestPoint = new GeoPoint(
                start.lat + fraction * (end.lat - start.lat),
                start.lng + fraction * (end.lng - start.lng));
        return new Candidate(nearestPoint, segmentIndex,
                segmentStartMeters + fraction * segmentLengthMeters,
                Math.hypot(nearestX, nearestY));
    }

    private static final class Candidate {
        final GeoPoint point;
        final int segmentIndex;
        final double alongMeters;
        final double offMeters;

        Candidate(GeoPoint point, int segmentIndex, double alongMeters, double offMeters) {
            this.point = point;
            this.segmentIndex = segmentIndex;
            this.alongMeters = alongMeters;
            this.offMeters = offMeters;
        }
    }
}
