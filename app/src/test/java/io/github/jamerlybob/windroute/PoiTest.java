package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.poi.OverpassClient;
import io.github.jamerlybob.windroute.poi.Poi;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import io.github.jamerlybob.windroute.poi.PoiKind;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** Overpass parsing and route-place maths, all tested without using the network. */
public class PoiTest {

    @Test
    public void queryAsksForEveryTagAsNodesAndWays() {
        String query = OverpassClient.buildQuery(
                Arrays.asList(new GeoPoint(-36.85, 174.76), new GeoPoint(-36.86, 174.77)),
                Arrays.asList(PoiKind.WATER, PoiKind.FOOD, PoiKind.CAMPING,
                        PoiKind.BIKE_SHOP, PoiKind.TOILETS, PoiKind.SHELTER), 500);

        assertTrue(query.startsWith("[out:json][timeout:25];"));
        assertTrue(query.contains("node[\"amenity\"=\"drinking_water\"]"));
        assertTrue(query.contains("way[\"shop\"=\"supermarket\"]"));
        assertTrue(query.contains("node[\"shop\"=\"convenience\"]"));
        assertTrue(query.contains("way[\"amenity\"=\"cafe\"]"));
        assertTrue(query.contains("node[\"amenity\"=\"restaurant\"]"));
        assertTrue(query.contains("way[\"amenity\"=\"fast_food\"]"));
        assertTrue(query.contains("node[\"tourism\"=\"camp_site\"]"));
        assertTrue(query.contains("way[\"tourism\"=\"caravan_site\"]"));
        assertTrue(query.contains("node[\"shop\"=\"bicycle\"]"));
        assertTrue(query.contains("way[\"amenity\"=\"toilets\"]"));
        assertTrue(query.contains("node[\"amenity\"=\"shelter\"]"));
        assertTrue(query.endsWith("out center;"));
        assertTrue(query.contains("(around:500,-36.850000,174.760000,-36.860000,174.770000)"));
    }

    @Test
    public void queryIncludesOnlyRequestedKinds() {
        String query = OverpassClient.buildQuery(
                Collections.singletonList(new GeoPoint(-36.85, 174.76)),
                Collections.singleton(PoiKind.WATER), 250);

        assertTrue(query.contains("drinking_water"));
        assertFalse(query.contains("supermarket"));
        assertFalse(query.contains("camp_site"));
    }

    @Test
    public void longRouteIsThinnedToOneHundredEvenlySpacedPoints() {
        List<GeoPoint> route = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // Deliberately cluster most original points near the start. Sampling
            // by list index would not put the middle sample near longitude 0.5.
            double lng = (i / 999.0) * (i / 999.0);
            route.add(new GeoPoint(0.0, lng));
        }

        String query = OverpassClient.buildQuery(route,
                Collections.singleton(PoiKind.WATER), 500);
        String around = query.substring(query.indexOf("(around:"), query.indexOf(");"));
        String[] parts = around.split(",");

        assertEquals(201, parts.length); // radius, then 100 latitude/longitude pairs
        assertEquals(0.0, Double.parseDouble(parts[1]), 0.0);
        assertEquals(0.0, Double.parseDouble(parts[2]), 0.000001);
        assertEquals(0.5, Double.parseDouble(parts[102]), 0.006);
        assertEquals(1.0, Double.parseDouble(parts[200].replace(")", "")), 0.000001);
    }

    @Test
    public void nodesAndWayCentresAreParsed() throws IOException {
        List<Poi> places = OverpassClient.parse("{\"elements\":["
                + "{\"type\":\"node\",\"lat\":-36.85,\"lon\":174.76,\"tags\":{"
                + "\"amenity\":\"drinking_water\",\"name\":\"Tap\","
                + "\"opening_hours\":\"24/7\"}},"
                + "{\"type\":\"way\",\"center\":{\"lat\":-36.86,\"lon\":174.77},"
                + "\"tags\":{\"shop\":\"bicycle\"}},"
                + "{\"type\":\"node\",\"lat\":0,\"lon\":0,"
                + "\"tags\":{\"amenity\":\"bench\"}}]}");

        assertEquals(2, places.size());
        assertEquals(PoiKind.WATER, places.get(0).kind);
        assertEquals("Tap", places.get(0).name);
        assertEquals("24/7", places.get(0).openingHours);
        assertEquals(-36.85, places.get(0).position.lat, 0.0);
        assertEquals(PoiKind.BIKE_SHOP, places.get(1).kind);
        assertNull(places.get(1).name);
        assertNull(places.get(1).openingHours);
        assertEquals(174.77, places.get(1).position.lng, 0.0);
    }

    @Test
    public void firstKindInDocumentedOrderWins() throws IOException {
        List<Poi> places = OverpassClient.parse("{\"elements\":[{"
                + "\"type\":\"node\",\"lat\":1,\"lon\":2,\"tags\":{"
                + "\"amenity\":\"cafe\",\"shop\":\"bicycle\"}}]}");

        assertEquals(PoiKind.FOOD, places.get(0).kind);
    }

    @Test
    public void malformedReplyHasAReadableError() {
        try {
            OverpassClient.parse("<html>busy</html>");
        } catch (IOException error) {
            assertTrue(error.getMessage().contains("places service"));
            return;
        }
        throw new AssertionError("Expected an IOException");
    }

    @Test
    public void placesAreLocatedAtNearestRoutePointAndSorted() {
        List<GeoPoint> route = straightRoute();
        Poi nearFinish = poi(PoiKind.FOOD, 0.001, 0.020);
        Poi nearStart = poi(PoiKind.WATER, 0.001, 0.000);

        List<PoiAlongRoute> places = PoiAlongRoute.locate(route,
                Arrays.asList(nearFinish, nearStart));

        assertEquals(nearStart, places.get(0).poi);
        assertEquals(0.0, places.get(0).distanceAlongRouteMeters, 0.01);
        assertEquals(GeoMath.distanceMeters(nearStart.position, route.get(0)),
                places.get(0).distanceOffRouteMeters, 0.01);
        assertEquals(nearFinish, places.get(1).poi);
        assertEquals(GeoMath.distanceMeters(route.get(0), route.get(2)),
                places.get(1).distanceAlongRouteMeters, 0.01);
    }

    @Test
    public void longestGapsIncludeStartAndFinishAndFilterByKind() {
        List<GeoPoint> route = straightRoute();
        double firstLeg = GeoMath.distanceMeters(route.get(0), route.get(1));
        double routeLength = GeoMath.distanceMeters(route.get(0), route.get(2));
        List<PoiAlongRoute> places = Arrays.asList(
                new PoiAlongRoute(poi(PoiKind.WATER, 0, 0), firstLeg, 0),
                new PoiAlongRoute(poi(PoiKind.WATER, 0, 0), firstLeg * 1.5, 0),
                new PoiAlongRoute(poi(PoiKind.FOOD, 0, 0), firstLeg * 0.25, 0));

        assertEquals(firstLeg, PoiAlongRoute.longestGapMeters(
                route, places, PoiKind.WATER), 0.01);
        assertEquals(routeLength - firstLeg * 0.25, PoiAlongRoute.longestGapMeters(
                route, places, PoiKind.FOOD), 0.01);
        assertEquals(routeLength, PoiAlongRoute.longestGapMeters(
                route, places, PoiKind.CAMPING), 0.01);
    }

    private static List<GeoPoint> straightRoute() {
        return Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0.01),
                new GeoPoint(0, 0.02));
    }

    private static Poi poi(PoiKind kind, double lat, double lng) {
        return new Poi(kind, null, new GeoPoint(lat, lng), null);
    }
}
