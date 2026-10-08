package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.DirectionComparison;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Whole routes: is each stretch given the right wind at the right time? */
public class RouteWindTest {

    private static final long DEPART = 1_800_000_000L;
    private static final long HOUR = 3600;

    /** Points every ~111 m from a start, stepping by the given degrees. */
    private static List<GeoPoint> line(GeoPoint start, double dLat, double dLng, int steps) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            points.add(new GeoPoint(start.lat + i * dLat, start.lng + i * dLng));
        }
        return points;
    }

    private static Route routeOf(List<GeoPoint> points, long durationSeconds) {
        double[] cumulative = GeoMath.cumulativeMeters(points);
        return new Route(points, cumulative[cumulative.length - 1], durationSeconds,
                Collections.<String>emptyList());
    }

    /** The same wind for every hour of a three hour forecast. */
    private static WindForecast steady(double speed, double from) {
        return hourly(new double[]{speed, speed, speed}, new double[]{from, from, from});
    }

    private static WindForecast hourly(double[] speeds, double[] from) {
        long[] times = new long[speeds.length];
        double[] gusts = new double[speeds.length];
        for (int i = 0; i < times.length; i++) {
            times[i] = DEPART + i * HOUR;
            gusts[i] = speeds[i] * 1.5;
        }
        return new WindForecast(times, speeds, from, gusts);
    }

    private static RouteWind analyze(Route route, WindForecast... forecasts) {
        List<Integer> samples = forecasts.length == 1
                ? Collections.singletonList(0)
                : Arrays.asList(0, route.points.size() - 1);
        return RouteWind.analyze(route, samples, Arrays.asList(forecasts), DEPART);
    }

    @Test
    public void ridingNorthIntoANortherlyIsHeadwindAllTheWay() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 40), 600);
        RouteWind wind = analyze(north, steady(20, 0));
        assertEquals(1.0, wind.share(WindEffect.HEADWIND), 1e-9);
        assertEquals(20.0, wind.averageHeadwindKmh, 0.01);
        assertEquals(30.0, wind.maxGustKmh, 1e-9);
    }

    @Test
    public void theSameRoadRiddenTheOtherWayIsTailwind() {
        Route south = routeOf(line(new GeoPoint(0.04, 0), -0.001, 0, 40), 600);
        RouteWind wind = analyze(south, steady(20, 0));
        assertEquals(1.0, wind.share(WindEffect.TAILWIND), 1e-9);
        assertEquals(-20.0, wind.averageHeadwindKmh, 0.01);
    }

    @Test
    public void anOutAndBackCancelsOut() {
        List<GeoPoint> points = line(new GeoPoint(0, 0), 0.001, 0, 40);
        points.addAll(line(new GeoPoint(0.04, 0), -0.001, 0, 40).subList(1, 41));
        RouteWind wind = analyze(routeOf(points, 1200), steady(20, 0));
        assertEquals(0.5, wind.share(WindEffect.HEADWIND), 0.06);
        assertEquals(0.5, wind.share(WindEffect.TAILWIND), 0.06);
        assertEquals(0.0, wind.averageHeadwindKmh, 2.0);
    }

    @Test
    public void aCornerChangesTheVerdict() {
        // North for 4.4 km, then east for 4.4 km, with the wind from the north.
        List<GeoPoint> points = line(new GeoPoint(0, 0), 0.001, 0, 40);
        points.addAll(line(new GeoPoint(0.04, 0), 0, 0.001, 40).subList(1, 41));
        RouteWind wind = analyze(routeOf(points, 1200), steady(20, 0));
        assertEquals(WindEffect.HEADWIND, wind.stretches.get(0).effect);
        assertEquals(WindEffect.CROSSWIND,
                wind.stretches.get(wind.stretches.size() - 1).effect);
        assertEquals(0.5, wind.share(WindEffect.CROSSWIND), 0.06);
    }

    @Test
    public void stretchesCoverTheWholeRouteWithNoGapsOrOverlaps() {
        Route route = routeOf(line(new GeoPoint(0, 0), 0.001, 0.0007, 57), 900);
        RouteWind wind = analyze(route, steady(20, 0));
        int expectedStart = 0;
        for (RouteWind.Stretch stretch : wind.stretches) {
            assertEquals(expectedStart, stretch.fromIndex);
            assertTrue(stretch.toIndex > stretch.fromIndex);
            expectedStart = stretch.toIndex;
        }
        assertEquals(route.points.size() - 1, expectedStart);
        assertEquals(route.distanceMeters, wind.totalMeters, 0.01);
    }

    @Test
    public void lightWindIsCalm() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 40), 600);
        assertEquals(1.0, analyze(north, steady(3, 0)).share(WindEffect.CALM), 1e-9);
    }

    @Test
    public void chosenCalmThresholdOverridesTheDefault() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 40), 600);
        List<Integer> samples = Collections.singletonList(0);
        List<WindForecast> forecasts = Collections.singletonList(steady(8, 0));
        RouteWind wind = RouteWind.analyze(north, samples, forecasts, DEPART,
                north.durationSeconds, 10);
        assertEquals(1.0, wind.share(WindEffect.CALM), 1e-9);
    }

    @Test
    public void chosenRidingDurationChangesForecastTiming() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 400), 600);
        WindForecast swinging = hourly(new double[]{20, 20, 20}, new double[]{0, 180, 180});
        RouteWind wind = RouteWind.analyze(north, Collections.singletonList(0),
                Collections.singletonList(swinging), DEPART, 2 * HOUR, 5);
        assertEquals(0.25, wind.share(WindEffect.HEADWIND), 0.02);
    }

    @Test
    public void reverseAnalysisKeepsForecastsAtTheirPhysicalEnds() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 400), 600);
        List<Integer> samples = Arrays.asList(0, north.points.size() - 1);
        List<WindForecast> forecasts = Arrays.asList(steady(20, 0), steady(20, 180));
        RouteWind reverse = RouteWind.analyzeReversed(north, samples, forecasts,
                DEPART, north.durationSeconds, 5);
        // The reverse ride starts at the original north end, where the
        // southerly is a headwind for a southbound rider.
        assertEquals(WindEffect.HEADWIND, reverse.stretches.get(0).effect);
        assertEquals(WindEffect.TAILWIND,
                reverse.stretches.get(reverse.stretches.size() - 1).effect);
    }

    @Test
    public void reverseComparisonOnlyAppearsForAMeaningfulChange() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 40), 600);
        RouteWind outward = analyze(north, steady(20, 0));
        RouteWind reverse = RouteWind.analyzeReversed(north,
                Collections.singletonList(0), Collections.singletonList(steady(20, 0)),
                DEPART, north.durationSeconds, 5);
        DirectionComparison comparison = DirectionComparison.compare(outward, reverse);
        assertTrue(comparison.meaningful);
        assertEquals(WindEffect.TAILWIND, comparison.effect);

        DirectionComparison unchanged = DirectionComparison.compare(outward, outward);
        assertTrue(!unchanged.meaningful);
    }

    @Test
    public void aLongRideUsesTheForecastForWhenYouGetThere() {
        // A two hour ride north. The wind swings from northerly to southerly
        // after the first hour, so the first half is headwind and the second
        // half tailwind, even though the road never turns.
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 400), 2 * HOUR);
        WindForecast swinging = hourly(new double[]{20, 20, 20}, new double[]{0, 180, 180});
        RouteWind wind = analyze(north, swinging);
        assertEquals(WindEffect.HEADWIND, wind.stretches.get(0).effect);
        assertEquals(WindEffect.TAILWIND,
                wind.stretches.get(wind.stretches.size() - 1).effect);
        // Hour 0 is nearest until 30 minutes in, a quarter of the way.
        assertEquals(0.25, wind.share(WindEffect.HEADWIND), 0.02);
    }

    @Test
    public void leavingLaterShiftsWhichForecastHourApplies() {
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 40), 600);
        WindForecast swinging = hourly(new double[]{20, 20, 20}, new double[]{0, 180, 180});
        List<Integer> samples = Collections.singletonList(0);
        List<WindForecast> forecasts = Collections.singletonList(swinging);
        assertEquals(1.0, RouteWind.analyze(north, samples, forecasts, DEPART)
                .share(WindEffect.HEADWIND), 1e-9);
        assertEquals(1.0, RouteWind.analyze(north, samples, forecasts, DEPART + HOUR)
                .share(WindEffect.TAILWIND), 1e-9);
    }

    @Test
    public void eachStretchUsesTheNearestPlaceWithAForecast() {
        // Northerly at the start of the road, southerly at the far end.
        Route north = routeOf(line(new GeoPoint(0, 0), 0.001, 0, 400), 600);
        RouteWind wind = analyze(north, steady(20, 0), steady(20, 180));
        assertEquals(WindEffect.HEADWIND, wind.stretches.get(0).effect);
        assertEquals(WindEffect.TAILWIND,
                wind.stretches.get(wind.stretches.size() - 1).effect);
        assertEquals(0.5, wind.share(WindEffect.HEADWIND), 0.02);
    }
}
