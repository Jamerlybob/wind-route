package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RoutesClient;
import io.github.jamerlybob.windroute.route.RouteWaypoint;
import io.github.jamerlybob.windroute.weather.OpenMeteoClient;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** The two web services, tested against saved replies. No network involved. */
public class ClientsTest {

    // ---- Google Routes ---------------------------------------------------------------

    @Test
    public void routeReplyIsParsed() throws IOException {
        Route route = RoutesClient.parse("{\"routes\":[{"
                + "\"distanceMeters\":12345,\"duration\":\"2710s\","
                + "\"polyline\":{\"encodedPolyline\":\"_p~iF~ps|U_ulLnnqC_mqNvxq`@\"},"
                + "\"warnings\":[\"Bicycling directions are in beta.\"]}]}");
        assertEquals(3, route.points.size());
        assertEquals(12345, route.distanceMeters, 0);
        assertEquals(2710, route.durationSeconds);
        assertEquals(Arrays.asList("Bicycling directions are in beta."), route.warnings);
    }

    @Test
    public void anEmptyReplyMeansNoRoute() {
        IOException error = assertThrows(IOException.class,
                () -> RoutesClient.parse("{}"));
        assertTrue(error.getMessage().contains("No cycling route"));
    }

    @Test
    public void googlesOwnErrorMessageIsPassedOn() {
        IOException error = assertThrows(IOException.class, () -> RoutesClient.parse(
                "{\"error\":{\"code\":403,\"message\":\"API key not valid.\"}}"));
        assertEquals("API key not valid.", error.getMessage());
    }

    @Test
    public void garbageIsAReadableError() {
        assertThrows(IOException.class, () -> RoutesClient.parse("<html>502</html>"));
    }

    @Test
    public void requestAsksForABicycleRouteBetweenTwoAddresses() {
        String body = RoutesClient.requestBody("Mission Bay", "Mt \"Eden\"");
        assertTrue(body.contains("\"travelMode\":\"BICYCLE\""));
        assertTrue(body.contains("\"address\":\"Mission Bay\""));
        assertTrue(body.contains("Mt \\\"Eden\\\""));    // quotes in an address are escaped
    }

    @Test
    public void requestCanMixCurrentCoordinatesAndAnAddress() {
        String body = RoutesClient.requestBody(
                RouteWaypoint.coordinates(new GeoPoint(-36.85, 174.76)),
                RouteWaypoint.address("Mt Eden"));
        assertTrue(body.contains("\"latitude\":-36.85"));
        assertTrue(body.contains("\"longitude\":174.76"));
        assertTrue(body.contains("\"destination\":{\"address\":\"Mt Eden\"}"));
    }

    @Test
    public void durationsAreSecondsWithAnS() {
        assertEquals(1234, RoutesClient.parseSeconds("1234s"));
        assertEquals(2, RoutesClient.parseSeconds("1.6s"));
        assertEquals(0, RoutesClient.parseSeconds("soon"));
    }

    // ---- Open-Meteo ------------------------------------------------------------------

    private static final String ONE_PLACE = "{\"latitude\":-36.85,\"hourly\":{"
            + "\"time\":[1791417600,1791421200,1791424800],"
            + "\"wind_speed_10m\":[7.2,7.7,null],"
            + "\"wind_direction_10m\":[233,233,217],"
            + "\"wind_gusts_10m\":[19.1,14.8,18.0]}}";

    @Test
    public void severalPlacesComeBackAsAnArray() throws IOException {
        List<WindForecast> forecasts =
                OpenMeteoClient.parse("[" + ONE_PLACE + "," + ONE_PLACE + "]");
        assertEquals(2, forecasts.size());
        assertArrayEquals(new long[]{1791417600L, 1791421200L, 1791424800L},
                forecasts.get(0).epochSeconds);
        assertEquals(233, forecasts.get(1).directionDeg[1], 0);
        assertEquals(19.1, forecasts.get(0).gustKmh[0], 0);
    }

    @Test
    public void onePlaceComesBackAsABareObject() throws IOException {
        assertEquals(1, OpenMeteoClient.parse(ONE_PLACE).size());
    }

    @Test
    public void aMissingHourIsNoWindRatherThanACrash() throws IOException {
        assertEquals(0.0, OpenMeteoClient.parse(ONE_PLACE).get(0).speedKmh[2], 0);
    }

    @Test
    public void weatherErrorsCarryTheServicesReason() {
        IOException error = assertThrows(IOException.class, () -> OpenMeteoClient.parse(
                "{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90.\"}"));
        assertEquals("Latitude must be in range of -90 to 90.", error.getMessage());
    }

    @Test
    public void urlListsEveryPlaceWithDotsWhateverThePhonesLanguage() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);    // where 52.5 is written "52,5"
        try {
            String url = OpenMeteoClient.buildUrl(Arrays.asList(
                    new GeoPoint(52.52, 13.405), new GeoPoint(-36.85, 174.76)));
            assertTrue(url.contains("latitude=52.5200,-36.8500"));
            assertTrue(url.contains("longitude=13.4050,174.7600"));
            assertTrue(url.contains("wind_direction_10m"));
            assertTrue(url.contains("timeformat=unixtime"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void forecastHourIsTheNearestOne() throws IOException {
        WindForecast forecast = OpenMeteoClient.parse(ONE_PLACE).get(0);
        assertEquals(0, forecast.indexAt(1791417600L + 1799));   // 29m59s past: still hour 0
        assertEquals(1, forecast.indexAt(1791417600L + 1801));   // past the half hour
        assertEquals(0, forecast.indexAt(0));                    // long before the forecast
        assertEquals(2, forecast.indexAt(Long.MAX_VALUE / 2));   // long after it
    }
}
