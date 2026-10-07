package io.github.jamerlybob.windroute.route;

import java.util.ArrayList;
import java.util.List;

/** Distances and directions on the surface of the Earth. */
public final class GeoMath {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoMath() {
    }

    /**
     * Great-circle distance in metres (the haversine formula).
     *
     * <p>Pythagoras on raw degrees would be wrong: a degree of longitude is 111 km
     * at the equator and shrinks to nothing at the poles.
     */
    public static double distanceMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.lat);
        double lat2 = Math.toRadians(b.lat);
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng - a.lng);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    /**
     * Compass bearing when setting off from a towards b: 0 = north, 90 = east,
     * in the range [0, 360).
     *
     * <p>atan2(y, x) gives the angle of the vector (x, y). Here x points north and
     * y points east, which is what turns a maths angle (anticlockwise from east)
     * into a compass bearing (clockwise from north).
     */
    public static double bearingDegrees(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.lat);
        double lat2 = Math.toRadians(b.lat);
        double dLng = Math.toRadians(b.lng - a.lng);
        double east = Math.sin(dLng) * Math.cos(lat2);
        double north = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(east, north)) + 360.0) % 360.0;
    }

    /** Running distance from the first point to each point, in metres. */
    public static double[] cumulativeMeters(List<GeoPoint> points) {
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + distanceMeters(points.get(i - 1), points.get(i));
        }
        return cumulative;
    }

    /**
     * Picks points roughly every spacingMeters along a route, always including
     * the first and last. These are where the weather gets looked up: wind does
     * not change much over a few kilometres, and asking for every point of a
     * 2,000 point route would be slow and rude to a free API.
     *
     * @return indexes into points
     */
    public static List<Integer> sampleIndexes(List<GeoPoint> points, double spacingMeters) {
        List<Integer> indexes = new ArrayList<>();
        if (points.isEmpty()) {
            return indexes;
        }
        double[] cumulative = cumulativeMeters(points);
        indexes.add(0);
        double nextAt = spacingMeters;
        for (int i = 1; i < points.size() - 1; i++) {
            if (cumulative[i] >= nextAt) {
                indexes.add(i);
                nextAt = cumulative[i] + spacingMeters;
            }
        }
        if (points.size() > 1) {
            indexes.add(points.size() - 1);
        }
        return indexes;
    }
}
