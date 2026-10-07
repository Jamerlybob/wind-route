package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.PolylineDecoder;

/** Polyline decoding, distances, bearings and sampling. */
public class RouteTest {

    @Test
    public void decodesTheExampleFromGooglesDocumentation() {
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        List<GeoPoint> points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertEquals(3, points.size());
        assertPoint(38.5, -120.2, points.get(0));
        assertPoint(40.7, -120.95, points.get(1));
        assertPoint(43.252, -126.453, points.get(2));
    }

    @Test
    public void decodesAnEmptyPolyline() {
        assertTrue(PolylineDecoder.decode("").isEmpty());
    }

    @Test
    public void decodesNegativeAndZeroSteps() {
        // A single point at the origin, then one at (-0.00001, 0.00001).
        List<GeoPoint> points = PolylineDecoder.decode("??@A");
        assertPoint(0, 0, points.get(0));
        assertPoint(-0.00001, 0.00001, points.get(1));
    }

    @Test
    public void oneDegreeOfLatitudeIsAbout111Km() {
        double meters = GeoMath.distanceMeters(new GeoPoint(0, 0), new GeoPoint(1, 0));
        assertEquals(111_195, meters, 50);
    }

    @Test
    public void aDegreeOfLongitudeShrinksAwayFromTheEquator() {
        double atEquator = GeoMath.distanceMeters(new GeoPoint(0, 0), new GeoPoint(0, 1));
        double at60 = GeoMath.distanceMeters(new GeoPoint(60, 0), new GeoPoint(60, 1));
        assertEquals(0.5, at60 / atEquator, 0.001);   // cos(60 degrees)
    }

    @Test
    public void bearingsToTheFourCompassPoints() {
        GeoPoint here = new GeoPoint(-36.85, 174.76);
        assertEquals(0, GeoMath.bearingDegrees(here, new GeoPoint(-36.80, 174.76)), 0.01);
        assertEquals(180, GeoMath.bearingDegrees(here, new GeoPoint(-36.90, 174.76)), 0.01);
        assertEquals(90, GeoMath.bearingDegrees(here, new GeoPoint(-36.85, 174.80)), 0.05);
        assertEquals(270, GeoMath.bearingDegrees(here, new GeoPoint(-36.85, 174.72)), 0.05);
    }

    @Test
    public void bearingIsNeverNegativeOr360() {
        GeoPoint here = new GeoPoint(10, 10);
        double justWestOfNorth = GeoMath.bearingDegrees(here, new GeoPoint(11, 9.999));
        assertTrue(justWestOfNorth > 359 && justWestOfNorth < 360);
    }

    @Test
    public void cumulativeDistanceAddsUp() {
        List<GeoPoint> line = Arrays.asList(
                new GeoPoint(0, 0), new GeoPoint(0.01, 0), new GeoPoint(0.02, 0));
        double[] cumulative = GeoMath.cumulativeMeters(line);
        assertEquals(0, cumulative[0], 0);
        assertEquals(cumulative[1] * 2, cumulative[2], 0.01);
    }

    @Test
    public void samplesIncludeBothEndsAndRespectSpacing() {
        // Eleven points 1.11 km apart, heading north: about 11 km in all.
        GeoPoint[] points = new GeoPoint[11];
        for (int i = 0; i < points.length; i++) {
            points[i] = new GeoPoint(i * 0.01, 0);
        }
        List<Integer> samples = GeoMath.sampleIndexes(Arrays.asList(points), 5_000);
        assertEquals(Arrays.asList(0, 5, 10), samples);
    }

    @Test
    public void aShortRouteStillGetsItsStartAndEnd() {
        List<GeoPoint> shortRide = Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0.001, 0));
        assertEquals(Arrays.asList(0, 1), GeoMath.sampleIndexes(shortRide, 5_000));
        assertEquals(Collections.singletonList(0),
                GeoMath.sampleIndexes(Collections.singletonList(new GeoPoint(0, 0)), 5_000));
    }

    private static void assertPoint(double lat, double lng, GeoPoint actual) {
        assertEquals(lat, actual.lat, 1e-9);
        assertEquals(lng, actual.lng, 1e-9);
    }
}
