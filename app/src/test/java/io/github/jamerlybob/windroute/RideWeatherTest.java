package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.RideWeather;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** Weather is picked for the time the rider reaches each sample. */
public class RideWeatherTest {

    private static final long DEPART = 1_800_000_000L;

    @Test
    public void arrivalTimesSelectWeatherAlongTheRide() {
        Route route = route();
        WindForecast start = forecast(new double[]{10, 11, 12},
                new double[]{0, 0, 0}, new double[]{10, 10, 10});
        WindForecast middle = forecast(new double[]{20, 21, 22},
                new double[]{0, 2.5, 0}, new double[]{10, 70, 10});
        WindForecast finish = forecast(new double[]{30, 31, 32},
                new double[]{0, 0, 1}, new double[]{10, 10, 40});

        RideWeather weather = RideWeather.analyze(route, Arrays.asList(0, 1, 2),
                Arrays.asList(start, middle, finish), DEPART);

        assertEquals(DEPART, weather.points.get(0).arrivalEpochSeconds);
        assertEquals(DEPART + 3600, weather.points.get(1).arrivalEpochSeconds);
        assertEquals(DEPART + 7200, weather.points.get(2).arrivalEpochSeconds);
        assertEquals(10, weather.points.get(0).temperatureC, 0);
        assertEquals(21, weather.points.get(1).temperatureC, 0);
        assertEquals(32, weather.points.get(2).temperatureC, 0);
        assertEquals(10, weather.coldestC, 0);
        assertEquals(32, weather.warmestC, 0);
        assertEquals(2.5, weather.wettestRainMm, 0);
        assertEquals(DEPART + 3600, weather.wettestHourEpochSeconds);
        assertEquals(70, weather.chanceOfAnyRainPercent, 0);
    }

    @Test
    public void windOnlyForecastDoesNotPretendUnknownWeatherIsZero() {
        WindForecast windOnly = new WindForecast(new long[]{DEPART},
                new double[]{10}, new double[]{90}, new double[]{20});
        RideWeather weather = RideWeather.analyze(route(), Collections.singletonList(0),
                Collections.singletonList(windOnly), DEPART);
        assertFalse(weather.hasRainData());
        assertTrue(Double.isNaN(weather.coldestC));
        assertTrue(Double.isNaN(weather.wettestRainMm));
        assertEquals(-1, weather.wettestHourEpochSeconds);
    }

    private static Route route() {
        java.util.List<GeoPoint> points = Arrays.asList(
                new GeoPoint(0, 0), new GeoPoint(0.01, 0), new GeoPoint(0.02, 0));
        double[] distance = GeoMath.cumulativeMeters(points);
        return new Route(points, distance[2], 7200, Collections.<String>emptyList());
    }

    private static WindForecast forecast(double[] temperature, double[] rain,
                                         double[] probability) {
        long[] times = new long[]{DEPART, DEPART + 3600, DEPART + 7200};
        double[] wind = new double[]{10, 10, 10};
        double[] direction = new double[]{90, 90, 90};
        double[] gust = new double[]{20, 20, 20};
        return new WindForecast(times, wind, direction, gust,
                temperature, rain, probability);
    }
}
