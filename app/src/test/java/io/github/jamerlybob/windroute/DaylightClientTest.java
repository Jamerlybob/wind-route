package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.weather.DaylightClient;
import io.github.jamerlybob.windroute.weather.DaylightForecast;

public class DaylightClientTest {
    @Test
    public void urlRequestsEightDailyLocalDaysAsUnixTime() {
        String url = DaylightClient.buildUrl(new GeoPoint(-36.85, 174.76));
        assertTrue(url.contains("daily=sunrise,sunset"));
        assertTrue(url.contains("timezone=auto"));
        assertTrue(url.contains("timeformat=unixtime"));
        assertTrue(url.contains("forecast_days=8"));
    }

    @Test
    public void parseAppliesOffsetToDayButNotSunEvents() throws IOException {
        List<DaylightForecast> days = DaylightClient.parse("{\"utc_offset_seconds\":43200,"
                + "\"daily\":{\"time\":[100],\"sunrise\":[200],\"sunset\":[300]}}");
        assertEquals(43300, days.get(0).dayEpochSeconds);
        assertEquals(200, days.get(0).sunriseEpochSeconds);
        assertEquals(300, days.get(0).sunsetEpochSeconds);
    }
}
