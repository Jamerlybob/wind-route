package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.trip.DaySplitter;
import io.github.jamerlybob.windroute.trip.TripPlanner;

public class TripPlannerTest {
    @Test
    public void splitsRequestedDaysAndRetainsRequiredGoogleWarnings() {
        Route route = new Route(Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0.01),
                new GeoPoint(0, 0.02), new GeoPoint(0, 0.03)), 3336, 600,
                Collections.singletonList("warning"));
        List<DaySplitter.Day> days = TripPlanner.byDays(route, null, 3, false);
        assertEquals(3, days.size());
        Route second = TripPlanner.routeForDay(route, days.get(1));
        assertEquals(2, second.points.size());
        assertEquals(Collections.singletonList("warning"), second.warnings);
    }

    @Test
    public void nudgeCannotCrossNeighbouringBoundaries() {
        assertEquals(19999, TripPlanner.nudge(new double[]{10000, 20000}, 0,
                50000, 30000)[0], 0);
    }
}
