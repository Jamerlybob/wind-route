package io.github.jamerlybob.windroute.nav;

import java.util.ArrayList;
import java.util.List;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteStep;

/** Projects ordered Google steps once rather than on every GPS fix. */
public final class TurnGuide {
    public final List<RouteStep> steps;
    public final List<Double> starts = new ArrayList<>();

    public TurnGuide(Route route) {
        steps = route.steps;
        double previous = 0;
        for (RouteStep step : steps) {
            // Step coordinates are route geometry, not noisy GPS. The rider's
            // 15 m tie tolerance would collapse Google's 6 m and 8 m steps.
            double along = RouteProgress.locate(route.points, step.start, previous, 0.01).distanceAlongMeters;
            previous = Math.max(previous, along);
            starts.add(previous);
        }
    }

    public int nextIndex(double progressMeters) {
        // DEPART describes the starting road. Every later step describes a
        // manoeuvre at its start, not the stretch currently being ridden.
        if ((Double.isNaN(progressMeters) || progressMeters <= 30) && !steps.isEmpty()
                && "DEPART".equals(steps.get(0).maneuver)) return 0;
        for (int i = 0; i < starts.size(); i++) {
            // No backward tolerance: several Google steps may be only metres apart.
            if (!"DEPART".equals(steps.get(i).maneuver)
                    && starts.get(i) > progressMeters) return i;
        }
        return -1;
    }

    public double distanceTo(int index, double progressMeters) {
        return index < 0 || "DEPART".equals(steps.get(index).maneuver)
                ? Double.NaN : starts.get(index) - progressMeters;
    }
}
