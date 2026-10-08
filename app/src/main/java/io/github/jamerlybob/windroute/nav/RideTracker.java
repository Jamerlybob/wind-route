package io.github.jamerlybob.windroute.nav;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;

/** Keeps GPS debounce and arrival rules testable without an Android Service. */
public final class RideTracker {
    public static final long OFF_ROUTE_SECONDS = 10;
    public static final int OFF_ROUTE_FIXES = 3;
    public static final double ARRIVAL_METERS = 30;
    private long firstOffRouteSeconds;
    private int offRouteFixes;
    private boolean offRoute;
    private double previousProgress = Double.NaN;
    private GeoPoint previousPosition;
    public double riddenMeters;
    public RouteProgress progress;
    public boolean arrived;

    /** True only on the transition into sustained off-route, so speech fires once. */
    public boolean update(Route route, GeoPoint position, long elapsedSeconds) {
        progress = RouteProgress.locate(route.points, position, previousProgress);
        previousProgress = progress.distanceAlongMeters;
        if (previousPosition != null) {
            double change = GeoMath.distanceMeters(previousPosition, position);
            // Reject implausible GPS jumps; a cyclist cannot cover 100 m in a
            // one-second fix. Small stationary drift is also not ridden distance.
            if (change >= 3 && change <= 100) riddenMeters += change;
        }
        previousPosition = position;
        boolean wasOffRoute = offRoute;
        if (progress.offRoute) {
            if (offRouteFixes++ == 0) firstOffRouteSeconds = elapsedSeconds;
            offRoute = offRouteFixes >= OFF_ROUTE_FIXES
                    && elapsedSeconds - firstOffRouteSeconds >= OFF_ROUTE_SECONDS;
        } else {
            offRouteFixes = 0;
            offRoute = false;
        }
        // Snapping to the final segment alone is insufficient: a rider can be
        // kilometres sideways from the finish while the snapped distance is zero.
        arrived = !progress.offRoute && GeoMath.distanceMeters(position,
                route.points.get(route.points.size() - 1)) <= ARRIVAL_METERS;
        return offRoute && !wasOffRoute;
    }

    public boolean isOffRoute() { return offRoute; }
}
