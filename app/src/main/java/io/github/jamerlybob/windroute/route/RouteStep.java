package io.github.jamerlybob.windroute.route;

/** One spoken navigation instruction returned by Google for a route leg. */
public final class RouteStep {
    public final int distanceMeters;
    public final String maneuver;
    public final String instruction;
    public final GeoPoint start;
    public final GeoPoint end;

    public RouteStep(int distanceMeters, String maneuver, String instruction,
                     GeoPoint start, GeoPoint end) {
        this.distanceMeters = distanceMeters;
        this.maneuver = maneuver == null ? "" : maneuver;
        this.instruction = instruction == null ? "" : instruction;
        this.start = start;
        this.end = end;
    }
}
