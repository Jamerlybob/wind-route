package io.github.jamerlybob.windroute.poi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** A useful place positioned by progress along a route. */
public final class PoiAlongRoute {
    public final Poi poi;
    public final double distanceAlongRouteMeters;
    public final double distanceOffRouteMeters;

    public PoiAlongRoute(Poi poi, double distanceAlongRouteMeters,
                         double distanceOffRouteMeters) {
        this.poi = poi;
        this.distanceAlongRouteMeters = distanceAlongRouteMeters;
        this.distanceOffRouteMeters = distanceOffRouteMeters;
    }

    /**
     * Finds each place's nearest route point, then orders the places from start
     * to finish. The spec deliberately uses route points rather than projecting
     * onto segments, so this result agrees with the path returned by routing.
     */
    public static List<PoiAlongRoute> locate(List<GeoPoint> routePoints, List<Poi> places) {
        List<PoiAlongRoute> located = new ArrayList<>();
        if (routePoints.isEmpty()) {
            return located;
        }

        double[] cumulative = GeoMath.cumulativeMeters(routePoints);
        for (Poi place : places) {
            int nearestIndex = 0;
            double nearestDistance = GeoMath.distanceMeters(place.position, routePoints.get(0));
            for (int i = 1; i < routePoints.size(); i++) {
                double distance = GeoMath.distanceMeters(place.position, routePoints.get(i));
                if (distance < nearestDistance) {
                    nearestIndex = i;
                    nearestDistance = distance;
                }
            }
            located.add(new PoiAlongRoute(place, cumulative[nearestIndex], nearestDistance));
        }
        Collections.sort(located, Comparator.comparingDouble(
                place -> place.distanceAlongRouteMeters));
        return located;
    }

    /**
     * Returns the largest distance without the requested kind, counting from
     * the route start to the first place and from the last place to the finish.
     */
    public static double longestGapMeters(List<GeoPoint> routePoints,
                                          List<PoiAlongRoute> places, PoiKind kind) {
        if (routePoints.isEmpty()) {
            return 0.0;
        }
        double[] cumulative = GeoMath.cumulativeMeters(routePoints);
        double routeLength = cumulative[cumulative.length - 1];
        List<Double> distances = new ArrayList<>();
        for (PoiAlongRoute place : places) {
            if (place.poi.kind == kind) {
                // Clamp defensive caller-created values to this route. Values
                // produced by locate() are already within these bounds.
                distances.add(Math.max(0.0,
                        Math.min(routeLength, place.distanceAlongRouteMeters)));
            }
        }
        Collections.sort(distances);

        double longest = 0.0;
        double previous = 0.0;
        for (double distance : distances) {
            longest = Math.max(longest, distance - previous);
            previous = distance;
        }
        return Math.max(longest, routeLength - previous);
    }
}
