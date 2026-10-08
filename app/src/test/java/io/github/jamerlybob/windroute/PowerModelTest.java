package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.PowerModel;
import io.github.jamerlybob.windroute.wind.RouteWind;

/** Hand-checkable consequences of holding the same cycling power in wind. */
public class PowerModelTest {

    private static final long DEPART = 1_800_000_000L;

    @Test
    public void noWindHasNoTimeCost() {
        Route route = route(northLine(40), 900);
        PowerModel.Result result = PowerModel.estimate(route, wind(route, 0, 0));
        assertEquals(15, result.stillAirMinutes, 0);
        assertEquals(result.stillAirMinutes, result.windyMinutes, 1e-6);
        assertEquals(0, result.differenceMinutes, 1e-6);
    }

    @Test
    public void headwindCostsMoreThanTheSameTailwindSaves() {
        Route north = route(northLine(40), 900);
        PowerModel.Result headwind = PowerModel.estimate(north, wind(north, 18, 0));
        PowerModel.Result tailwind = PowerModel.estimate(north, wind(north, 18, 180));

        assertTrue(headwind.differenceMinutes > 0);
        assertTrue(tailwind.differenceMinutes < 0);
        // Aerodynamic power grows with the square of air speed. Losing speed
        // into the wind therefore costs more time than the tailwind gives back.
        assertTrue(headwind.differenceMinutes > -tailwind.differenceMinutes);
    }

    @Test
    public void steadyWindMakesAnOutAndBackSlowerThanStillAir() {
        List<GeoPoint> points = northLine(40);
        for (int i = 39; i >= 0; i--) {
            points.add(new GeoPoint(i * 0.001, 0));
        }
        Route outAndBack = route(points, 1800);
        PowerModel.Result result = PowerModel.estimate(
                outAndBack, wind(outAndBack, 18, 0));
        assertTrue(result.windyMinutes > result.stillAirMinutes);
    }

    @Test
    public void zeroDurationReturnsZeroRatherThanDividingByZero() {
        Route route = route(northLine(10), 0);
        PowerModel.Result result = PowerModel.estimate(route, wind(route, 20, 0));
        assertEquals(0, result.stillAirMinutes, 0);
        assertEquals(0, result.windyMinutes, 0);
        assertEquals(0, result.differenceMinutes, 0);
    }

    @Test
    public void extremeTailwindIsCappedAtAReadableRoadSpeed() {
        Route route = route(northLine(40), 900);
        PowerModel.Result result = PowerModel.estimate(route, wind(route, 200, 180));
        double fastestAllowedMinutes = route.distanceMeters
                / PowerModel.MAX_SPEED_M_S / 60.0;
        assertEquals(fastestAllowedMinutes, result.windyMinutes, 1e-6);
    }

    private static List<GeoPoint> northLine(int steps) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            points.add(new GeoPoint(i * 0.001, 0));
        }
        return points;
    }

    private static Route route(List<GeoPoint> points, long duration) {
        double[] distance = GeoMath.cumulativeMeters(points);
        return new Route(points, distance[distance.length - 1], duration,
                Collections.<String>emptyList());
    }

    private static RouteWind wind(Route route, double speed, double direction) {
        WindForecast forecast = new WindForecast(
                new long[]{DEPART, DEPART + 3600},
                new double[]{speed, speed}, new double[]{direction, direction},
                new double[]{speed, speed});
        return RouteWind.analyze(route, Collections.singletonList(0),
                Collections.singletonList(forecast), DEPART);
    }
}
