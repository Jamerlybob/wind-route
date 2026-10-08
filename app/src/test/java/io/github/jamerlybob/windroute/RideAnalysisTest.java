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
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** The whole screen must use one rider-duration and calm-threshold snapshot. */
public class RideAnalysisTest {
    private static final long START = 1_800_000_000L;

    @Test
    public void riderSettingsReachWindWeatherAndDepartureScores() {
        GeoPoint start = new GeoPoint(0, 0);
        GeoPoint finish = new GeoPoint(0.01, 0);
        double distance = GeoMath.distanceMeters(start, finish);
        Route route = new Route(Arrays.asList(start, finish), distance, 900,
                Collections.emptyList());
        WindForecast forecast = new WindForecast(
                new long[]{START, START + 3600}, new double[]{10, 10},
                new double[]{0, 0}, new double[]{15, 15},
                new double[]{14, 14}, new double[]{0, 0}, new double[]{0, 0});
        Settings settings = new Settings(Settings.DistanceUnit.KILOMETERS,
                Settings.WindSpeedUnit.KMH, 20, Settings.Theme.SYSTEM, 15);

        RideAnalysis result = RideAnalysis.build(route, Arrays.asList(0, 1),
                Arrays.asList(forecast, forecast), START, settings, null);

        long expectedDuration = UnitFormatter.ridingDurationSeconds(distance, 900, 20);
        assertEquals(expectedDuration, result.route.durationSeconds);
        assertEquals(START + expectedDuration,
                result.weather.points.get(1).arrivalEpochSeconds);
        assertEquals(WindEffect.CALM, result.wind.stretches.get(0).effect);
        assertEquals(1.0, result.departures.get(0).calmShare, 0.001);
    }

    @Test
    public void coverageIncludesTheEndOfTheFinalForecastHour() {
        WindForecast forecast = new WindForecast(new long[]{START, START + 3600},
                new double[]{0, 0}, new double[]{0, 0}, new double[]{0, 0});
        assertTrue(RideAnalysis.forecastCovers(Collections.singletonList(forecast),
                START + 3600, 3600));
        assertFalse(RideAnalysis.forecastCovers(Collections.singletonList(forecast),
                START + 3600, 3601));
    }

    @Test
    public void newDisplayUnitsConvertWithoutChangingStoredMetricValues() {
        assertEquals(68, UnitFormatter.temperature(20,
                Settings.TemperatureUnit.FAHRENHEIT), 0.001);
        assertEquals(328.084, UnitFormatter.elevation(100,
                Settings.ElevationUnit.FEET), 0.001);
    }
}
