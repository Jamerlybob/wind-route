package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.trip.DaySplitter;

/** Splitting bikepacking routes by distance, climbing effort or hand. */
public class DaySplitterTest {

    @Test
    public void ordinaryDistanceSplitKeepsAUsefulLastDay() {
        double[] distance = kilometres(24);
        List<DaySplitter.Day> days = DaySplitter.byDistance(distance, 10_000);
        assertEquals(3, days.size());
        assertDay(days.get(0), 0, 10, 10_000);
        assertDay(days.get(1), 10, 20, 10_000);
        assertDay(days.get(2), 20, 24, 4_000);
    }

    @Test
    public void tinyLastDayIsSharedAcrossEarlierDays() {
        List<DaySplitter.Day> days = DaySplitter.byDistance(kilometres(23), 10_000);
        assertEquals(2, days.size());
        assertDay(days.get(0), 0, 11, 11_000);
        assertDay(days.get(1), 11, 23, 12_000);
    }

    @Test
    public void climbingEffortMovesTheSplitEarlier() {
        double[] distance = kilometres(10);
        double[] height = {0, 10, 20, 30, 40, 50, 50, 50, 50, 50, 50};
        ElevationProfile profile = new ElevationProfile(distance, height);
        List<DaySplitter.Day> days = DaySplitter.byEffort(distance, profile, 5_000);
        // The remaining 500 effort-metres would be less than one third of a
        // target day, so it is shared across the two useful days.
        assertEquals(2, days.size());
        assertEquals(5, days.get(0).endPointIndex);
        assertEquals(46.67, days.get(0).ascentMeters, 0.01);
    }

    @Test
    public void handPickedDistancesMapToRoutePoints() {
        List<DaySplitter.Day> days = DaySplitter.atDistances(kilometres(10), null,
                new double[]{2_200, 7_700});
        assertEquals(3, days.size());
        assertEquals(2, days.get(0).endPointIndex);
        assertEquals(8, days.get(1).endPointIndex);
        assertEquals(8, days.get(2).startPointIndex);
    }

    @Test
    public void invalidTargetsAndHandSplitsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DaySplitter.byDistance(kilometres(2), 0));
        assertThrows(IllegalArgumentException.class,
                () -> DaySplitter.atDistances(kilometres(2), null,
                        new double[]{1_500, 1_000}));
    }

    private static double[] kilometres(int count) {
        double[] values = new double[count + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = i * 1_000;
        }
        return values;
    }

    private static void assertDay(DaySplitter.Day day, int start, int end, double distance) {
        assertEquals(start, day.startPointIndex);
        assertEquals(end, day.endPointIndex);
        assertEquals(distance, day.distanceMeters, 0);
    }
}
