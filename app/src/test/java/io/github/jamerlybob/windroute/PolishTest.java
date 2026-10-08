package io.github.jamerlybob.windroute;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import io.github.jamerlybob.windroute.gpx.GpxParser;
import io.github.jamerlybob.windroute.poi.OverpassClient;
import io.github.jamerlybob.windroute.weather.RainDisplay;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** Tests distinguish genuine empty/dry data from missing or failed data. */
public class PolishTest {
    @Test public void overpassRuntimeRemarkIsAnErrorEvenWithHttpSuccess() {
        assertThrows(IOException.class, () -> OverpassClient.parse(
                "{\"remark\":\"runtime error: timed out\",\"elements\":[]}"));
    }

    @Test public void genuineEmptyPlacesAreValid() throws Exception {
        assertTrue(OverpassClient.parse("{\"elements\":[]}").isEmpty());
    }

    @Test public void rainfallBelowTheDisplayPrecisionHasNoAmount() {
        assertFalse(RainDisplay.showAmount(0));
        assertFalse(RainDisplay.showAmount(0.049));
        assertTrue(RainDisplay.showAmount(0.05));
        assertFalse(RainDisplay.showAmount(Double.NaN));
    }

    @Test public void gpxUsesTrackNameRatherThanMetadataOrWaypointName() throws Exception {
        GpxParser.Result result = GpxParser.parse("<gpx><metadata><name>Exporter</name></metadata>"
                + "<trk><name>Coastal ride</name><trkseg><trkpt lat=\"0\" lon=\"0\"/>"
                + "<trkpt lat=\"1\" lon=\"1\"/></trkseg></trk></gpx>");
        assertEquals("Coastal ride", result.name);
        assertEquals("", GpxParser.parse("<gpx><rte><rtept lat=\"0\" lon=\"0\"/>"
                + "<rtept lat=\"1\" lon=\"1\"/></rte></gpx>").name);
    }

    @Test public void pastAndBeyondForecastAreNeverCovered() {
        WindForecast forecast = new WindForecast(new long[]{1000, 4600},
                new double[]{0, 0}, new double[]{0, 0}, new double[]{0, 0});
        assertFalse(RideAnalysis.forecastCovers(Collections.singletonList(forecast), 999, 10));
        assertFalse(RideAnalysis.forecastCovers(Collections.singletonList(forecast), 8000, 500));
    }
}
