package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.jamerlybob.windroute.trip.TripStore;
import io.github.jamerlybob.windroute.weather.DaylightForecast;
import io.github.jamerlybob.windroute.weather.WindForecast;

public class TripStoreTest {
    @Test
    public void forecastCacheRoundTripsUnknownValuesAndDaylight() throws JSONException {
        WindForecast forecast = new WindForecast(new long[]{100, 200},
                new double[]{10, 11}, new double[]{90, 100}, new double[]{20, 21},
                new double[]{12, Double.NaN}, new double[]{0, 1},
                new double[]{5, 60});
        RouteForecastLoader.Result day = new RouteForecastLoader.Result(
                Collections.singletonList(3), Collections.singletonList(forecast));
        String json = TripStore.encodeForecastCache(Collections.singletonList(day),
                Collections.singletonList(Arrays.asList(
                        new DaylightForecast(1, 2, 3))));

        TripStore.ForecastCache restored = TripStore.decodeForecastCache(json);
        assertEquals(3, (int) restored.forecasts.get(0).sampleIndexes.get(0));
        assertTrue(Double.isNaN(restored.forecasts.get(0).forecasts.get(0).temperatureC[1]));
        assertEquals(3, restored.daylight.get(0).get(0).sunsetEpochSeconds);
    }
}
