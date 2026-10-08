package io.github.jamerlybob.windroute.gpx;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** Reduces very large GPX tracks while retaining bends and matching elevations. */
public final class RouteThinner {
    private RouteThinner() {
    }

    public static final class Result {
        public final List<GeoPoint> points;
        public final List<Double> elevations;

        Result(List<GeoPoint> points, List<Double> elevations) {
            this.points = points;
            this.elevations = elevations;
        }
    }

    public static Result thin(List<GeoPoint> points, List<Double> elevations, int maximum) {
        if (maximum < 2) throw new IllegalArgumentException("Keep at least two points.");
        if (points.size() != elevations.size()) {
            throw new IllegalArgumentException("Each point needs a matching elevation.");
        }
        if (points.size() <= maximum) return copy(points, elevations);

        double tolerance = 2.0;
        List<Integer> kept;
        do {
            boolean[] include = new boolean[points.size()];
            include[0] = true;
            include[points.size() - 1] = true;
            simplify(points, 0, points.size() - 1, tolerance, include);
            kept = indexes(include);
            tolerance *= 1.7;
        } while (kept.size() > maximum);

        List<GeoPoint> resultPoints = new ArrayList<>();
        List<Double> resultElevations = new ArrayList<>();
        for (int index : kept) {
            resultPoints.add(points.get(index));
            resultElevations.add(elevations.get(index));
        }
        return new Result(resultPoints, resultElevations);
    }

    private static void simplify(List<GeoPoint> points, int first, int last,
                                 double tolerance, boolean[] include) {
        if (last <= first + 1) return;
        double farthest = -1;
        int farthestIndex = -1;
        GeoPoint start = points.get(first);
        GeoPoint end = points.get(last);
        for (int i = first + 1; i < last; i++) {
            double distance = distanceToSegment(points.get(i), start, end);
            if (distance > farthest) {
                farthest = distance;
                farthestIndex = i;
            }
        }
        if (farthest > tolerance) {
            include[farthestIndex] = true;
            simplify(points, first, farthestIndex, tolerance, include);
            simplify(points, farthestIndex, last, tolerance, include);
        }
    }

    private static double distanceToSegment(GeoPoint point, GeoPoint start, GeoPoint end) {
        double length = GeoMath.distanceMeters(start, end);
        if (length == 0) return GeoMath.distanceMeters(point, start);
        // Local equirectangular coordinates are accurate at cycling-route scale
        // and let the usual point-to-line projection preserve sharp bends.
        double meanLat = Math.toRadians((start.lat + end.lat + point.lat) / 3.0);
        double x = (point.lng - start.lng) * Math.cos(meanLat);
        double y = point.lat - start.lat;
        double dx = (end.lng - start.lng) * Math.cos(meanLat);
        double dy = end.lat - start.lat;
        double fraction = Math.max(0, Math.min(1, (x * dx + y * dy) / (dx * dx + dy * dy)));
        GeoPoint projected = new GeoPoint(start.lat + (end.lat - start.lat) * fraction,
                start.lng + (end.lng - start.lng) * fraction);
        return GeoMath.distanceMeters(point, projected);
    }

    private static List<Integer> indexes(boolean[] include) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < include.length; i++) if (include[i]) result.add(i);
        return result;
    }

    private static Result copy(List<GeoPoint> points, List<Double> elevations) {
        return new Result(new ArrayList<>(points), new ArrayList<>(elevations));
    }
}
