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
import io.github.jamerlybob.windroute.wind.DepartureScorer;

/** The departure score exposes every number that contributes to the winner. */
public class DepartureScorerTest {

    private static final long START = 1_800_000_000L;

    @Test
    public void rainCanMakeATailwindHourWorseThanADryHeadwindHour() {
        Route route = northRoute(600);
        WindForecast forecast = forecast(4,
                new double[]{10, 10, 10, 10},
                new double[]{0, 180, 180, 180},
                new double[]{20, 20, 53, 20},
                new double[]{0, 6, 0, 0},
                new double[]{0, 100, 0, 0});

        List<DepartureScorer.Entry> entries = DepartureScorer.score(route,
                Collections.singletonList(0), Collections.singletonList(forecast),
                START, 3);

        assertEquals(3, entries.size());
        assertEquals(10, entries.get(0).averageHeadwindKmh, 0.01);
        assertEquals(-10, entries.get(1).averageHeadwindKmh, 0.01);
        assertEquals(23, entries.get(1).rainPenalty, 0.01);
        assertTrue(entries.get(1).score > entries.get(0).score);
        assertEquals(2, entries.get(2).gustPenalty, 0.01);
        assertEquals(1.0, entries.get(0).headwindShare, 0);
        assertEquals(1.0, entries.get(1).tailwindShare, 0);
    }

    @Test
    public void departuresWhoseRideRunsPastTheForecastAreLeftOut() {
        Route twoHourRoute = northRoute(7200);
        WindForecast forecast = forecast(3,
                new double[]{10, 10, 10}, new double[]{0, 0, 0},
                new double[]{20, 20, 20}, new double[]{0, 0, 0},
                new double[]{0, 0, 0});

        List<DepartureScorer.Entry> entries = DepartureScorer.score(twoHourRoute,
                Collections.singletonList(0), Collections.singletonList(forecast),
                START, 4);

        // The final forecast timestamp starts an hour, so arrival exactly at
        // START + 3 hours is still covered. Anything later is not.
        assertEquals(2, entries.size());
        assertEquals(START + 3600, entries.get(1).departureEpochSeconds);
    }

    private static Route northRoute(long duration) {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            points.add(new GeoPoint(i * 0.001, 0));
        }
        double[] distance = GeoMath.cumulativeMeters(points);
        return new Route(points, distance[distance.length - 1], duration,
                Collections.<String>emptyList());
    }

    private static WindForecast forecast(int hours, double[] speed, double[] direction,
                                         double[] gust, double[] rain,
                                         double[] probability) {
        long[] times = new long[hours];
        double[] temperature = new double[hours];
        for (int i = 0; i < hours; i++) {
            times[i] = START + i * 3600;
            temperature[i] = 15;
        }
        return new WindForecast(times, speed, direction, gust,
                temperature, rain, probability);
    }
}
