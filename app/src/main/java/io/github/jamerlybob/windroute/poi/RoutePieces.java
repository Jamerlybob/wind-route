package io.github.jamerlybob.windroute.poi;

import java.util.*;
import io.github.jamerlybob.windroute.route.*;

/** Adjacent pieces share their cut point, so no corridor is lost at a seam. */
public final class RoutePieces {
    public static final double MAX_METERS = 25000;
    private RoutePieces() { }
    public static List<List<GeoPoint>> split(List<GeoPoint> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("A route needs a point");
        List<List<GeoPoint>> result = new ArrayList<>();
        List<GeoPoint> piece = new ArrayList<>(); piece.add(points.get(0));
        double used = 0;
        for (int i=1; i<points.size(); i++) {
            GeoPoint from = points.get(i-1), to = points.get(i);
            double distance = GeoMath.distanceMeters(from,to);
            while (used + distance >= MAX_METERS && distance > 0) {
                GeoPoint cut = interpolate(from,to,(MAX_METERS-used)/distance);
                piece.add(cut); result.add(piece);
                piece = new ArrayList<>(); piece.add(cut);
                from=cut; used=0; distance=GeoMath.distanceMeters(from,to);
            }
            if (distance > .000001) piece.add(to);
            used += distance;
        }
        if (piece.size()>1 || result.isEmpty()) result.add(piece);
        return result;
    }
    private static GeoPoint interpolate(GeoPoint from, GeoPoint to, double fraction) {
        // Spherical interpolation handles sparse GPX segments and the date line,
        // where interpolating raw longitude would travel the long way round.
        double angle = GeoMath.distanceMeters(from,to)/6371000;
        if (angle < 1e-12) return from;
        double a = Math.sin((1-fraction)*angle)/Math.sin(angle);
        double b = Math.sin(fraction*angle)/Math.sin(angle);
        double lat1=Math.toRadians(from.lat), lng1=Math.toRadians(from.lng);
        double lat2=Math.toRadians(to.lat), lng2=Math.toRadians(to.lng);
        double x=a*Math.cos(lat1)*Math.cos(lng1)+b*Math.cos(lat2)*Math.cos(lng2);
        double y=a*Math.cos(lat1)*Math.sin(lng1)+b*Math.cos(lat2)*Math.sin(lng2);
        double z=a*Math.sin(lat1)+b*Math.sin(lat2);
        return new GeoPoint(Math.toDegrees(Math.atan2(z,Math.hypot(x,y))),Math.toDegrees(Math.atan2(y,x)));
    }
}
