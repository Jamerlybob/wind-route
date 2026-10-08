package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.weather.OpenMeteoClient;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** The richer Open-Meteo request and its unknown-value rules. */
public class WeatherForecastTest {

    @Test
    public void requestIncludesEightDaysOfTemperatureAndRain() {
        String url = OpenMeteoClient.buildUrl(
                Collections.singletonList(new GeoPoint(-36.85, 174.76)));
        assertTrue(url.contains("temperature_2m"));
        assertTrue(url.contains("precipitation,"));
        assertTrue(url.contains("precipitation_probability"));
        assertTrue(url.contains("forecast_days=8"));
        assertTrue(url.contains("timeformat=unixtime&timezone=GMT"));
    }

    @Test
    public void replyCarriesWeatherAndKeepsMissingValuesUnknown() throws IOException {
        WindForecast forecast = OpenMeteoClient.parse("{\"hourly\":{"
                + "\"time\":[1000,4600],"
                + "\"wind_speed_10m\":[10,null],"
                + "\"wind_direction_10m\":[90,null],"
                + "\"wind_gusts_10m\":[20,null],"
                + "\"temperature_2m\":[12.5,null],"
                + "\"precipitation\":[0,null],"
                + "\"precipitation_probability\":[5,null]}} ").get(0);

        assertEquals(12.5, forecast.temperatureC[0], 0);
        assertEquals(0.0, forecast.precipitationMm[0], 0);
        assertEquals(5.0, forecast.precipitationProbabilityPercent[0], 0);
        assertEquals(0.0, forecast.speedKmh[1], 0);
        assertTrue(Double.isNaN(forecast.temperatureC[1]));
        assertTrue(Double.isNaN(forecast.precipitationMm[1]));
        assertTrue(Double.isNaN(forecast.precipitationProbabilityPercent[1]));
    }

    @Test
    public void windOnlyConstructorMarksNewWeatherUnknown() {
        WindForecast forecast = new WindForecast(new long[]{1000}, new double[]{10},
                new double[]{90}, new double[]{20});
        assertTrue(Double.isNaN(forecast.temperatureC[0]));
        assertTrue(Double.isNaN(forecast.precipitationMm[0]));
        assertTrue(Double.isNaN(forecast.precipitationProbabilityPercent[0]));
    }
}
