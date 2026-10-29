package io.github.jamerlybob.windroute.route;

import java.util.List;

/** Weather points every two kilometres, widened only to keep a 100-point budget. */
public final class RouteSamples {
    private RouteSamples() { }
    public static List<Integer> indexes(List<GeoPoint> points) {
        double[] distances=GeoMath.cumulativeMeters(points);
        double length=distances.length==0 ? 0 : distances[distances.length-1];
        return GeoMath.sampleIndexes(points,Math.max(2000,length/99));
    }
}
