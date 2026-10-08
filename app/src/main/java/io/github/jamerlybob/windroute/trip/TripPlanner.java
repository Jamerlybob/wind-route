package io.github.jamerlybob.windroute.trip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;

/** Pure route splitting and day slicing used by the trip screen. */
public final class TripPlanner {
    private TripPlanner() {
    }

    public static List<DaySplitter.Day> byDays(Route route, ElevationProfile elevation,
                                                int days, boolean countClimbing) {
        if (days < 1) throw new IllegalArgumentException("A trip needs at least one day.");
        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        double total = cumulative[cumulative.length - 1];
        double target = total / days;
        return countClimbing && elevation != null
                ? DaySplitter.byEffort(cumulative, elevation, (total
                + DaySplitter.EFFORT_METERS_PER_ASCENT_METER * elevation.totalAscentMeters) / days)
                : DaySplitter.byDistance(cumulative, elevation, target);
    }

    public static Route routeForDay(Route route, DaySplitter.Day day) {
        List<GeoPoint> points = new ArrayList<>(route.points.subList(
                day.startPointIndex, day.endPointIndex + 1));
        long duration = route.distanceMeters <= 0 ? 0
                : Math.round(route.durationSeconds * day.distanceMeters / route.distanceMeters);
        return new Route(points, day.distanceMeters, duration,
                Collections.emptyList(), route.source);
    }

    public static double[] boundaries(List<DaySplitter.Day> days) {
        double[] result = new double[Math.max(0, days.size() - 1)];
        double total = 0;
        for (int i = 0; i < result.length; i++) {
            total += days.get(i).distanceMeters;
            result[i] = total;
        }
        return result;
    }

    public static double[] nudge(double[] boundaries, int boundary, double deltaMeters,
                                 double routeLength) {
        double[] result = boundaries.clone();
        double lower = boundary == 0 ? 1 : result[boundary - 1] + 1;
        double upper = boundary == result.length - 1
                ? routeLength - 1 : result[boundary + 1] - 1;
        result[boundary] = Math.max(lower, Math.min(upper, result[boundary] + deltaMeters));
        return result;
    }
}
