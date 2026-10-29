package io.github.jamerlybob.windroute.trip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteStep;
import io.github.jamerlybob.windroute.nav.TurnGuide;

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
        TurnGuide guide = new TurnGuide(route);
        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        List<RouteStep> steps = new ArrayList<>();
        for (int i = 0; i < guide.steps.size(); i++) {
            double along = guide.starts.get(i);
            if (along >= cumulative[day.startPointIndex]
                    && along < cumulative[day.endPointIndex]) steps.add(guide.steps.get(i));
        }
        // Slicing a Google route does not remove its required warnings. GPX
        // remains step-free; only Google's original instructions are retained.
        return new Route(points, day.distanceMeters, duration,
                route.warnings, route.source, steps);
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

    /** Snap in order so nearby campsites cannot invert or collapse two days. */
    public static double[] snapBoundaries(Route route, double[] boundaries,
                                          List<io.github.jamerlybob.windroute.poi.PoiAlongRoute> places) {
        double[] distances = GeoMath.cumulativeMeters(route.points);
        double total = distances[distances.length - 1];
        double[] snapped = boundaries.clone();
        for (int i = 0; i < snapped.length; i++) {
            double target = StopPicker.snap(boundaries[i], total, places);
            double lower = i == 0 ? 1 : snapped[i - 1] + 1;
            double upper = i == snapped.length - 1 ? total - 1 : boundaries[i + 1] - 1;
            snapped[i] = Math.max(lower, Math.min(upper, target));
        }
        return snapped;
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
