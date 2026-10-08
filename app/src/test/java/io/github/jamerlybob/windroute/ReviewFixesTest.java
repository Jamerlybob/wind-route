package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import io.github.jamerlybob.windroute.nav.DurationFormatter;
import io.github.jamerlybob.windroute.nav.CameraFollow;
import io.github.jamerlybob.windroute.nav.RideDistanceFormatter;
import io.github.jamerlybob.windroute.nav.TurnGuide;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteStep;
import io.github.jamerlybob.windroute.settings.Settings;

/** Regression cases from the actual riding-screen review. */
public final class ReviewFixesTest {
    private static final String[] FORMATS = {"%d m", "%.1f mi", "%d ft", "%.1f km"};

    private TurnGuide guide() {
        double[] meters = {0, 390, 396, 417, 425, 1000};
        List<GeoPoint> points = new ArrayList<>();
        List<RouteStep> steps = new ArrayList<>();
        for (double distance : meters) points.add(new GeoPoint(0, distance / 111194.92664455874));
        for (int i = 0; i < points.size() - 1; i++) steps.add(new RouteStep(
                (int) (meters[i + 1] - meters[i]), i == 0 ? "DEPART" : "TURN_RIGHT",
                "Step " + i, points.get(i), points.get(i + 1)));
        return new TurnGuide(new Route(points, 1000, 300, Collections.emptyList(), Route.Source.GOOGLE, steps));
    }

    @Test public void departHasNoDistanceAndOnlyAppearsNearStart() {
        TurnGuide guide = guide();
        assertEquals(0, guide.nextIndex(Double.NaN));
        assertEquals(0, guide.nextIndex(0));
        assertEquals(0, guide.nextIndex(30));
        assertTrue(Double.isNaN(guide.distanceTo(0, 0)));
        assertEquals(1, guide.nextIndex(31));
        assertEquals(359, guide.distanceTo(1, 31), 0.01);
    }

    @Test public void sixTwentyOneAndEightMeterStepsNeverFlashBehindRider() {
        TurnGuide guide = guide();
        assertEquals(1, guide.nextIndex(389));
        assertEquals(2, guide.nextIndex(391));
        assertEquals(3, guide.nextIndex(397));
        assertEquals(4, guide.nextIndex(418));
        assertEquals(-1, guide.nextIndex(426));
        // At a manoeuvre's exact projected start it has already been reached.
        assertEquals(3, guide.nextIndex(guide.starts.get(2)));
        assertTrue(Double.isNaN(guide.distanceTo(-1, 1000)));
    }

    @Test public void metricRideDistancesNeverRoundToZeroKilometers() {
        assertEquals("0 m", metric(0));
        assertEquals("50 m", metric(6));
        assertEquals("350 m", metric(326));
        assertEquals("1000 m", metric(999));
        assertEquals("1.0 km", metric(1000));
        assertEquals("350 metres", CuePlannerTest.words(Settings.DistanceUnit.KILOMETERS).distance(326));
    }

    @Test public void imperialUsesFeetUntilPointTwoMilesIncludingSpeech() {
        assertEquals("1000 ft", imperial(300));
        assertEquals("1000 feet", CuePlannerTest.words(Settings.DistanceUnit.MILES).distance(300));
        assertEquals("1050 ft", imperial(0.1999 * 1609.344));
        assertEquals("0.2 mi", imperial(0.2 * 1609.344));
        assertEquals("0.8 mi", imperial(1280));
    }

    @Test public void addedAndSavedTimeUseHoursFromSixtyMinutes() {
        for (int sign : Arrays.asList(1, -1)) {
            assertEquals("59 min", duration(sign * 59));
            assertEquals("1 h 0 min", duration(sign * 60));
            assertEquals("3 h 4 min", duration(sign * 184));
            assertEquals("3 h 5 min", duration(sign * 184.6));
        }
        assertEquals("1 h 0 min", duration(59.5));
    }

    private String duration(double minutes) { return DurationFormatter.format(minutes, "%d min", "%d h %d min"); }
    @Test public void cameraIgnoresJitterAndNeverRestartsAnimation() {
        GeoPoint start = new GeoPoint(0, 0);
        GeoPoint jitter = new GeoPoint(0, 0.00001);
        GeoPoint moved = new GeoPoint(0, 0.00004);
        assertTrue(CameraFollow.shouldMove(null, 0, start, 0, false));
        assertFalse(CameraFollow.shouldMove(start, 359, jitter, 1, false));
        assertTrue(CameraFollow.shouldMove(start, 0, moved, 0, false));
        assertTrue(CameraFollow.shouldMove(start, 0, start, 6, false));
        assertFalse(CameraFollow.shouldMove(start, 0, moved, 90, true));
    }
    private String metric(double meters) { return RideDistanceFormatter.format(meters, Settings.DistanceUnit.KILOMETERS, FORMATS); }
    private String imperial(double meters) { return RideDistanceFormatter.format(meters, Settings.DistanceUnit.MILES, FORMATS); }
}
