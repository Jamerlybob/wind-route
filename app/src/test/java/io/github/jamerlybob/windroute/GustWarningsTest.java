package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.GustWarnings;
import io.github.jamerlybob.windroute.wind.RouteWind;

/** Gust warnings keep only crosswinds and merge adjacent stretches. */
public class GustWarningsTest {

    private static final long DEPART = 1_800_000_000L;

    @Test
    public void neighbouringGustyCrosswindsBecomeOneRightSideWarning() {
        Route route = northRoute();
        WindForecast eastWind = steady(20, 90, 55);
        RouteWind wind = analyze(route, eastWind);

        List<GustWarnings.Warning> warnings = GustWarnings.find(wind, route,
                Collections.singletonList(0), Collections.singletonList(eastWind), DEPART);

        assertEquals(1, warnings.size());
        assertEquals(GustWarnings.Side.RIGHT, warnings.get(0).side);
        assertEquals(0, warnings.get(0).startMeters, 0.01);
        assertEquals(route.distanceMeters, warnings.get(0).lengthMeters, 0.01);
        assertEquals(55, warnings.get(0).maxGustKmh, 0);
    }

    @Test
    public void westWindWarnsFromTheLeftAndThresholdCanSuppressIt() {
        Route route = northRoute();
        WindForecast westWind = steady(20, 270, 50);
        RouteWind wind = analyze(route, westWind);

        assertEquals(GustWarnings.Side.LEFT, GustWarnings.find(wind, route,
                Collections.singletonList(0), Collections.singletonList(westWind),
                DEPART).get(0).side);
        assertEquals(0, GustWarnings.find(wind, route,
                Collections.singletonList(0), Collections.singletonList(westWind),
                DEPART, 50).size());
    }

    private static Route northRoute() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            points.add(new GeoPoint(i * 0.001, 0));
        }
        double[] distance = GeoMath.cumulativeMeters(points);
        return new Route(points, distance[distance.length - 1], 600,
                Collections.<String>emptyList());
    }

    private static WindForecast steady(double speed, double direction, double gust) {
        return new WindForecast(new long[]{DEPART, DEPART + 3600},
                new double[]{speed, speed}, new double[]{direction, direction},
                new double[]{gust, gust});
    }

    private static RouteWind analyze(Route route, WindForecast forecast) {
        return RouteWind.analyze(route, Collections.singletonList(0),
                Collections.singletonList(forecast), DEPART);
    }
}
