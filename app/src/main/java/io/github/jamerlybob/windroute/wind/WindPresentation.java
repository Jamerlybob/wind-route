package io.github.jamerlybob.windroute.wind;

/** Shared direction and opacity conventions for planning and riding visuals. */
public final class WindPresentation {
    private WindPresentation() { }

    public static double relativeArrow(double fromDegrees, double headingDegrees) {
        // Meteorological direction is FROM; the arrow shows the air travelling
        // TO, relative to the direction in which the rider is facing.
        return ((fromDegrees + 180 - headingDegrees) % 360 + 360) % 360;
    }

    public static WindEffect verdict(RouteWind wind) {
        WindEffect best = WindEffect.CALM;
        for (WindEffect effect : WindEffect.values()) {
            if (wind.share(effect) > wind.share(best)) best = effect;
        }
        return best;
    }

    public static double routeArrow(io.github.jamerlybob.windroute.route.Route route, RouteWind wind) {
        double east = 0, north = 0;
        for (RouteWind.Stretch stretch : wind.stretches) {
            WindVector vector = WindVector.from(stretch.windSpeedKmh, stretch.windFromDegrees);
            east += vector.east * stretch.lengthMeters;
            north += vector.north * stretch.lengthMeters;
        }
        double from = Math.toDegrees(Math.atan2(-east, -north));
        double heading = io.github.jamerlybob.windroute.route.GeoMath.bearingDegrees(
                route.points.get(0), route.points.get(route.points.size() - 1));
        return relativeArrow(from, heading);
    }

    public static double particleOpacity(double speedKmh, double calmBelowKmh) {
        double calm = Math.max(0, Math.min(29, calmBelowKmh));
        if (speedKmh <= calm) return calm == 0 ? 0 : .03 * Math.max(0, speedKmh) / calm;
        return Math.min(1, .03 + .97 * (speedKmh - calm) / (30 - calm));
    }
}
