package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.places.PlaceNameFormatter;
import io.github.jamerlybob.windroute.route.CyclingWarnings;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteJson;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitFormatter;

public class DailyUseLogicTest {
    @Test
    public void addressLineWinsOverFallbackParts() {
        assertEquals("22 Queen Street, Auckland 1010, New Zealand",
                PlaceNameFormatter.format(
                        Collections.singletonList("22 Queen Street, Auckland 1010, New Zealand"),
                        "22 Queen Street", "Auckland", "Auckland", "New Zealand"));
    }

    @Test
    public void fallbackAddressOmitsBlankAndDuplicateParts() {
        assertEquals("Mission Bay, Auckland, New Zealand",
                PlaceNameFormatter.format(Collections.emptyList(), "Mission Bay",
                        "Auckland", "auckland", "New Zealand"));
    }

    @Test
    public void routeJsonRoundTripsEveryField() throws JSONException {
        Route route = new Route(Arrays.asList(new GeoPoint(-36.85, 174.76),
                new GeoPoint(-36.86, 174.77)), 1234.5, 456,
                Collections.singletonList("Take care."));
        Route restored = RouteJson.read(RouteJson.write(route));
        assertEquals(route.distanceMeters, restored.distanceMeters, 0);
        assertEquals(route.durationSeconds, restored.durationSeconds);
        assertEquals(route.warnings, restored.warnings);
        assertEquals(route.points.get(1).lat, restored.points.get(1).lat, 0);
        assertEquals(route.points.get(1).lng, restored.points.get(1).lng, 0);
    }

    @Test
    public void routeJsonPreservesImportedSource() throws JSONException {
        Route imported = new Route(Arrays.asList(new GeoPoint(1, 2), new GeoPoint(3, 4)),
                100, 20, Collections.emptyList(), Route.Source.GPX);
        assertEquals(Route.Source.GPX, RouteJson.read(RouteJson.write(imported)).source);
    }

    @Test(expected = JSONException.class)
    public void unreadableSavedRouteIsRejected() throws JSONException {
        RouteJson.read("{\"points\":[]}");
    }

    @Test
    public void reversingRouteDoesNotChangeOriginal() {
        Route route = new Route(Arrays.asList(new GeoPoint(1, 2), new GeoPoint(3, 4)),
                10, 20, Collections.emptyList());
        Route reversed = route.reversed();
        assertEquals(1, route.points.get(0).lat, 0);
        assertEquals(3, reversed.points.get(0).lat, 0);
        assertEquals(route.distanceMeters, reversed.distanceMeters, 0);
    }

    @Test
    public void unitFormattingConvertsDistanceAndAllWindUnits() {
        assertEquals("1.0 km", UnitFormatter.distance(1000,
                Settings.DistanceUnit.KILOMETERS, "%.1f km", Locale.US));
        assertEquals("1.0 mi", UnitFormatter.distance(1609.344,
                Settings.DistanceUnit.MILES, "%.1f mi", Locale.US));
        assertEquals("10 mph", UnitFormatter.speed(16.09344,
                Settings.WindSpeedUnit.MPH, "%.0f mph", Locale.US));
        assertEquals("10.0 m/s", UnitFormatter.speed(36,
                Settings.WindSpeedUnit.METERS_PER_SECOND, "%.1f m/s", Locale.US));
        assertEquals("10 kn", UnitFormatter.speed(18.52,
                Settings.WindSpeedUnit.KNOTS, "%.0f kn", Locale.US));
    }

    @Test
    public void usualSpeedReplacesGoogleDuration() {
        assertEquals(2700, UnitFormatter.ridingDurationSeconds(15_000, 999, 20));
        assertEquals(999, UnitFormatter.ridingDurationSeconds(15_000, 999,
                Settings.USE_GOOGLE_RIDING_SPEED));
    }

    @Test
    public void cyclingNoticeIsAlwaysFirstAndNeverDuplicated() {
        String fixed = "Cycling routes are in beta.";
        List<String> combined = CyclingWarnings.combine(fixed,
                Arrays.asList(" cycling   routes are in BETA. ", "Use caution."));
        assertEquals(Arrays.asList(fixed, "Use caution."), combined);
    }
}
