package io.github.jamerlybob.windroute;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import io.github.jamerlybob.windroute.nav.*;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Small made-up roads expose GPS, persistence and cue failures deterministically. */
public class NavigationTest {
    private static final long NOW = 1_800_000_000L;

    static Route road() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 100; i++) points.add(new GeoPoint(i * 0.0009, 0));
        double[] distances = GeoMath.cumulativeMeters(points);
        return new Route(points, distances[distances.length - 1], 3600,
                Collections.emptyList(), Route.Source.GPX);
    }

    private static WindForecast weather(double direction, double gust) {
        return new WindForecast(new long[]{NOW, NOW + 3600, NOW + 7200},
                new double[]{20, 20, 20}, new double[]{direction, direction, direction},
                new double[]{gust, gust, gust}, new double[]{15, 15, 15},
                new double[]{0, 0, 0}, new double[]{0, 0, 0});
    }

    private static RideAnalysis analysis(Route route, double direction, double gust,
                                         ElevationProfile elevation) {
        return RideAnalysis.build(route, Arrays.asList(0, 100),
                Arrays.asList(weather(direction, gust), weather(direction, gust)),
                NOW, Settings.defaults(), elevation);
    }

    @Test public void offRouteNeedsBothTimeAndFixCountAndResetsOnReturn() {
        Route route = road();
        RideTracker tracker = new RideTracker();
        GeoPoint off = new GeoPoint(0.001, 0.002);
        assertFalse(tracker.update(route, off, 0));
        assertFalse(tracker.update(route, off, 11));
        assertTrue(tracker.update(route, off, 12));
        assertFalse(tracker.update(route, off, 15));
        assertFalse(tracker.update(route, route.points.get(1), 16));
        assertFalse(tracker.isOffRoute());
        assertFalse(tracker.update(route, off, 17));
        assertFalse(tracker.update(route, off, 18));
        assertTrue(tracker.update(route, off, 28));
    }

    @Test public void sidewaysFromFinishIsNotArrival() {
        Route route = road();
        RideTracker tracker = new RideTracker();
        tracker.update(route, new GeoPoint(0.09, 0.005), 0);
        assertEquals(0, tracker.progress.distanceRemainingMeters, 0.01);
        assertFalse(tracker.arrived);
        tracker.update(route, new GeoPoint(0.0899, 0), 1);
        assertTrue(tracker.arrived);
    }

    @Test public void liveWindArrowUsesFromDirectionAndActualHeading() {
        Route route = road();
        RideWind wind = RideWind.at(route, Arrays.asList(0, 100),
                Arrays.asList(weather(270, 60), weather(270, 60)), 1000, 0, NOW, 5);
        assertEquals(WindEffect.CROSSWIND, wind.effect);
        assertEquals(90, wind.arrowDegrees, 0.01); // wind from left travels right
        assertNull(RideWind.at(route, Arrays.asList(0, 100),
                Arrays.asList(weather(0, 30), weather(0, 30)), 0, 0, NOW - 1, 5));
    }

    @Test public void syntheticRideHasExactClimbWindAndTopSequence() {
        Route route = road();
        ElevationProfile elevation = new ElevationProfile(
                new double[]{0, 1000, 1400, 1800, 2200, 2500, 10000},
                new double[]{0, 0, 24, 48, 72, 30, 30});
        RideAnalysis ride = analysis(route, 0, 25, elevation);
        List<CuePlanner.Event> events = CuePlanFactory.build(ride, Settings.defaults(),
                CuePlannerTest.words(Settings.DistanceUnit.KILOMETERS));
        CuePlanner planner = new CuePlanner(events);
        assertEquals("Headwind for the next 10.0 kilometres.", planner.next(0, 0));
        assertNull(planner.next(500, 9));
        // The shared three-sample elevation smoother trims the synthetic crest.
        assertEquals("Climb in 400 metres. 800 metres at 5 percent. Into a headwind.",
                planner.next(600, 20));
        assertNull(planner.next(1700, 40));
        assertEquals("Top of the climb.", planner.next(1800, 60));
        assertNull(planner.next(2500, 80));
    }

    @Test public void tailwindToFinishAndGustSideAreBuiltFromForecast() {
        Route route = road();
        CuePlanner tailwind = new CuePlanner(CuePlanFactory.build(analysis(route, 180, 25, null),
                Settings.defaults(), CuePlannerTest.words(Settings.DistanceUnit.KILOMETERS)));
        assertEquals("Tailwind from here to the finish.", tailwind.next(0, 0));
        List<CuePlanner.Event> gusts = CuePlanFactory.build(analysis(route, 270, 60, null),
                Settings.defaults(), CuePlannerTest.words(Settings.DistanceUnit.KILOMETERS));
        boolean found = false;
        for (CuePlanner.Event event : gusts) {
            if (event.kind == CuePlanner.Kind.GUST
                    && event.text.startsWith("Strong crosswind from the left")) found = true;
        }
        assertTrue(found);
    }

    @Test public void lessTalkRemovesWindAndOrdinaryClimbs() {
        Settings quiet = new Settings(Settings.DistanceUnit.KILOMETERS, Settings.WindSpeedUnit.KMH,
                0, Settings.Theme.SYSTEM, 5, Settings.TemperatureUnit.CELSIUS,
                Settings.ElevationUnit.METERS, true, true, true, true, 300, true);
        assertTrue(CuePlanFactory.build(analysis(road(), 180, 25, null), quiet,
                CuePlannerTest.words(Settings.DistanceUnit.KILOMETERS)).isEmpty());
    }

    @Test public void routeStepsRoundTripAndOldRoutesRemainReadable() throws Exception {
        Route original = road();
        RouteStep step = new RouteStep(100, "TURN_LEFT", "Turn left onto Queen Street",
                original.points.get(10), original.points.get(20));
        Route route = new Route(original.points, original.distanceMeters, 3600,
                Collections.emptyList(), Route.Source.GOOGLE, Collections.singletonList(step));
        Route restored = RouteJson.read(RouteJson.write(route));
        assertEquals("TURN_LEFT", restored.steps.get(0).maneuver);
        assertEquals(step.instruction, restored.steps.get(0).instruction);
        assertEquals(step.start.lat, restored.steps.get(0).start.lat, 0);
        assertEquals(1, route.withDuration(100).steps.size());
        assertTrue(route.reversed().steps.isEmpty());
        org.json.JSONObject old = new org.json.JSONObject(RouteJson.write(route));
        old.remove("steps");
        assertTrue(RouteJson.read(old.toString()).steps.isEmpty());
    }

    @Test public void googleParsesEveryLegsStepsIncludingDefaultZeroCoordinates() throws Exception {
        String step = "{\"distanceMeters\":100,\"navigationInstruction\":{\"maneuver\":\"TURN_LEFT\","
                + "\"instructions\":\"Turn left\"},\"startLocation\":{\"latLng\":{}},"
                + "\"endLocation\":{\"latLng\":{\"latitude\":1,\"longitude\":2}}}";
        Route route = RoutesClient.parse("{\"routes\":[{\"polyline\":{\"encodedPolyline\":"
                + "\"_p~iF~ps|U_ulLnnqC_mqNvxq`@\"},\"legs\":[{\"steps\":[" + step
                + "]},{\"steps\":[" + step + "]}]}]}");
        assertEquals(2, route.steps.size());
        assertEquals(0, route.steps.get(0).start.lat, 0);
        assertEquals("Turn left", route.steps.get(1).instruction);
    }
}
