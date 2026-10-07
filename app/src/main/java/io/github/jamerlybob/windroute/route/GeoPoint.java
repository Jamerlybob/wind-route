package io.github.jamerlybob.windroute.route;

/**
 * A latitude and longitude in degrees.
 *
 * <p>The Maps SDK has its own LatLng class, but using it here would drag Android
 * into the route maths and make it untestable on a plain JVM. The activity
 * converts at the edge, when it draws.
 */
public final class GeoPoint {
    public final double lat;
    public final double lng;

    public GeoPoint(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    @Override
    public String toString() {
        return "(" + lat + ", " + lng + ")";
    }
}
