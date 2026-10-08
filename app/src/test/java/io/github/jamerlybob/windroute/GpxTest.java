package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import io.github.jamerlybob.windroute.gpx.GpxParser;
import io.github.jamerlybob.windroute.gpx.GpxWriter;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** GPX 1.0/1.1 import, safe XML handling and portable export. */
public class GpxTest {

    @Test
    public void parsesTrackElevationsWaypointsAndIgnoresExtensions() throws IOException {
        String gpx = "<?xml version=\"1.0\"?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\">"
                + "<wpt lat=\"-36.8500\" lon=\"174.7600\"><ele>12</ele>"
                + "<name>Water</name></wpt><trk><trkseg>"
                + "<trkpt lat=\"-36.85\" lon=\"174.76\"><ele>5.5</ele>"
                + "<extensions><speed>7</speed></extensions></trkpt>"
                + "<trkpt lat=\"-36.84\" lon=\"174.77\"/>"
                + "</trkseg></trk></gpx>";
        GpxParser.Result result = GpxParser.parse(gpx);
        assertEquals(2, result.points.size());
        assertEquals(5.5, result.elevations.get(0), 0);
        assertTrue(Double.isNaN(result.elevations.get(1)));
        assertEquals("Water", result.waypoints.get(0).name);
        assertEquals(12, result.waypoints.get(0).elevationMeters, 0);
    }

    @Test
    public void parsesAGpx10Route() throws IOException {
        String gpx = "<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">"
                + "<rte><rtept lat=\"1\" lon=\"2\"/><rtept lat=\"3\" lon=\"4\"/>"
                + "</rte></gpx>";
        assertEquals(2, GpxParser.parse(gpx).points.size());
    }

    @Test
    public void writeParseRoundTripEscapesTheName() throws IOException {
        String xml = GpxWriter.write("Food & water <day 1>", Arrays.asList(
                new GeoPoint(-36.85, 174.76), new GeoPoint(-36.84, 174.77)),
                Arrays.asList(4.0, 9.5));
        assertTrue(xml.contains("Food &amp; water &lt;day 1&gt;"));
        GpxParser.Result reparsed = GpxParser.parse(xml);
        assertEquals(2, reparsed.points.size());
        assertEquals(-36.85, reparsed.points.get(0).lat, 1e-7);
        assertEquals(9.5, reparsed.elevations.get(1), 0);
    }

    @Test
    public void malformedAndEntityFilesHaveReadableErrors() {
        IOException malformed = assertThrows(IOException.class,
                () -> GpxParser.parse("<gpx><trk>"));
        assertTrue(malformed.getMessage().contains("GPX"));

        String entity = "<!DOCTYPE gpx [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<gpx><trk><trkseg><trkpt lat=\"0\" lon=\"0\">"
                + "<name>&xxe;</name></trkpt></trkseg></trk></gpx>";
        assertThrows(IOException.class, () -> GpxParser.parse(entity));
    }

    @Test
    public void aFileWithoutARouteExplainsTheProblem() {
        IOException error = assertThrows(IOException.class,
                () -> GpxParser.parse("<gpx><wpt lat=\"0\" lon=\"0\"/></gpx>"));
        assertTrue(error.getMessage().contains("track or route"));
    }
}
