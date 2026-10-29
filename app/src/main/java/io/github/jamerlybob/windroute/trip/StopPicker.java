package io.github.jamerlybob.windroute.trip;

import java.util.List;
import io.github.jamerlybob.windroute.poi.*;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.wind.*;

/** Chooses progress along the road, never straight-line distance across a bend. */
public final class StopPicker {
    private StopPicker() { }
    public static double snap(double requested, double total, List<PoiAlongRoute> places) {
        double target = Math.max(0, Math.min(total, requested));
        PoiAlongRoute best = null;
        for (PoiAlongRoute place : places) {
            if (place.distanceAlongRouteMeters <= 0 || place.distanceAlongRouteMeters >= total
                    || Math.abs(place.distanceAlongRouteMeters - target) > 5000
                    || place.distanceOffRouteMeters > 1000) continue;
            // A rural cafe alone is not an overnight town. Require an OSM
            // settlement with a food place nearby, already returned by lookup.
            if (place.poi.kind != PoiKind.CAMPING
                    && !(place.poi.kind == PoiKind.TOWN && hasFood(place, places))) continue;
            if (best == null || (place.poi.kind == PoiKind.CAMPING && best.poi.kind != PoiKind.CAMPING)
                    || (place.poi.kind == best.poi.kind && Math.abs(place.distanceAlongRouteMeters-target)
                    < Math.abs(best.distanceAlongRouteMeters-target))) best = place;
        }
        return best == null ? target : best.distanceAlongRouteMeters;
    }
    private static boolean hasFood(PoiAlongRoute town, List<PoiAlongRoute> places) {
        for (PoiAlongRoute place : places) {
            if (place.poi.kind == PoiKind.FOOD
                    && GeoMath.distanceMeters(town.poi.position, place.poi.position) <= 3000) return true;
        }
        return false;
    }

    /** Name the actual selected stop, rather than only its reverse-geocoded district. */
    public static String nameAt(double meters, List<PoiAlongRoute> places) {
        for (PoiAlongRoute place : places) {
            if ((place.poi.kind == PoiKind.CAMPING || place.poi.kind == PoiKind.TOWN)
                    && Math.abs(place.distanceAlongRouteMeters - meters) < 1
                    && place.poi.name != null && !place.poi.name.trim().isEmpty()) return place.poi.name;
        }
        return null;
    }

    public static GeoPoint pointAt(List<GeoPoint> points, double meters) {
        double[] distances = GeoMath.cumulativeMeters(points);
        meters = Math.max(0, Math.min(distances[distances.length-1], meters));
        for (int i=1; i<points.size(); i++) if (distances[i] >= meters) {
            double span = distances[i]-distances[i-1];
            double f = span == 0 ? 0 : (meters-distances[i-1])/span;
            GeoPoint a=points.get(i-1), b=points.get(i);
            return new GeoPoint(a.lat+(b.lat-a.lat)*f, a.lng+(b.lng-a.lng)*f);
        }
        return points.get(points.size()-1);
    }
    public static double minutesTo(double meters, Route route, RouteWind wind) {
        double power = PowerModel.estimate(route, wind).assumedPowerWatts;
        double seconds = 0, covered = 0;
        for (RouteWind.Stretch stretch : wind.stretches) {
            double length = stretch.lengthMeters * route.distanceMeters / wind.totalMeters;
            double take = Math.max(0, Math.min(length, meters-covered));
            seconds += take / Math.max(.1, PowerModel.speedForPower(power, stretch.headwindKmh/3.6));
            covered += length;
            if (covered >= meters) break;
        }
        return seconds / 60;
    }
    public static double distanceForMinutes(double minutes, Route route, RouteWind wind) {
        double low=0, high=route.distanceMeters;
        for (int i=0;i<30;i++) { double middle=(low+high)/2;
            if (minutesTo(middle,route,wind)<minutes) low=middle; else high=middle; }
        return (low+high)/2;
    }
    /** Match the nearest local solar midday, including departures after sunset. */
    public static Long sunsetFor(java.util.List<io.github.jamerlybob.windroute.weather.DaylightForecast> days,
                                 long departure) {
        long closestGap = Long.MAX_VALUE;
        Long sunset = null;
        for (io.github.jamerlybob.windroute.weather.DaylightForecast day : days) {
            if (day.sunriseEpochSeconds <= 0 || day.sunsetEpochSeconds <= day.sunriseEpochSeconds) continue;
            long midday = (day.sunriseEpochSeconds + day.sunsetEpochSeconds) / 2;
            long gap = Math.abs(departure - midday);
            if (gap < closestGap && gap <= 18 * 3600) {
                closestGap = gap;
                sunset = day.sunsetEpochSeconds;
            }
        }
        return sunset;
    }

    public static long minutesAfterSunset(long arrival, long sunset) {
        return Math.max(0, Math.round((arrival-sunset)/60.0));
    }
}
