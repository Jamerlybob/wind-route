package io.github.jamerlybob.windroute.route;

/**
 * One end of a route, represented by either readable text or coordinates.
 * This explicit either/or value lets RoutesClient support "My location"
 * without teaching its caller how Google's request JSON represents endpoints.
 */
public final class RouteWaypoint {
    public final String address;
    public final GeoPoint coordinates;

    private RouteWaypoint(String address, GeoPoint coordinates) {
        this.address = address;
        this.coordinates = coordinates;
    }

    public static RouteWaypoint address(String address) {
        if (address == null) {
            throw new IllegalArgumentException("An address is required.");
        }
        return new RouteWaypoint(address, null);
    }

    public static RouteWaypoint coordinates(GeoPoint coordinates) {
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates are required.");
        }
        return new RouteWaypoint(null, coordinates);
    }
}
