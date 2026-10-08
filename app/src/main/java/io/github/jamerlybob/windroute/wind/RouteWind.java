package io.github.jamerlybob.windroute.wind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;

/**
 * A route cut into short stretches, each labelled with what the wind does there.
 *
 * <p>Pure Java: give it a route, forecasts and a departure time, and it returns
 * numbers. Drawing is somebody else's job.
 */
public final class RouteWind {

    /** Roughly how long each coloured stretch is. Short enough to follow bends. */
    public static final double STRETCH_METERS = 400.0;

    /** One stretch of road with a single wind verdict. */
    public static final class Stretch {
        /** Indexes into the route's points, both inclusive. */
        public final int fromIndex;
        public final int toIndex;
        public final double lengthMeters;
        public final WindEffect effect;
        /** Positive is against you, negative is behind you. km/h. */
        public final double headwindKmh;
        public final double windSpeedKmh;
        public final double gustKmh;

        Stretch(int fromIndex, int toIndex, double lengthMeters, WindEffect effect,
                double headwindKmh, double windSpeedKmh, double gustKmh) {
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.lengthMeters = lengthMeters;
            this.effect = effect;
            this.headwindKmh = headwindKmh;
            this.windSpeedKmh = windSpeedKmh;
            this.gustKmh = gustKmh;
        }
    }

    public final List<Stretch> stretches;
    public final double totalMeters;
    /** Averaged over distance. Positive means the ride is into the wind overall. */
    public final double averageHeadwindKmh;
    public final double averageWindKmh;
    public final double maxGustKmh;
    private final Map<WindEffect, Double> metersByEffect;

    private RouteWind(List<Stretch> stretches) {
        this.stretches = stretches;
        this.metersByEffect = new EnumMap<>(WindEffect.class);
        double total = 0;
        double headwindSum = 0;
        double windSum = 0;
        double gust = 0;
        for (Stretch s : stretches) {
            total += s.lengthMeters;
            headwindSum += s.headwindKmh * s.lengthMeters;
            windSum += s.windSpeedKmh * s.lengthMeters;
            gust = Math.max(gust, s.gustKmh);
            metersByEffect.merge(s.effect, s.lengthMeters, Double::sum);
        }
        this.totalMeters = total;
        this.averageHeadwindKmh = total > 0 ? headwindSum / total : 0;
        this.averageWindKmh = total > 0 ? windSum / total : 0;
        this.maxGustKmh = gust;
    }

    /** Share of the route's distance spent in one kind of wind, from 0 to 1. */
    public double share(WindEffect effect) {
        Double meters = metersByEffect.get(effect);
        return totalMeters > 0 && meters != null ? meters / totalMeters : 0;
    }

    /**
     * @param sampleIndexes      indexes of the route points that have a forecast
     * @param forecasts          one forecast per sample index, same order
     * @param departEpochSeconds when the ride starts
     */
    public static RouteWind analyze(Route route, List<Integer> sampleIndexes,
                                    List<WindForecast> forecasts, long departEpochSeconds) {
        return analyze(route, sampleIndexes, forecasts, departEpochSeconds,
                route.durationSeconds, WindMath.CALM_BELOW_KMH);
    }

    /**
     * Analyzes a route with the rider's chosen duration and calm threshold.
     * The original overload remains for callers that want Google's estimate.
     */
    public static RouteWind analyze(Route route, List<Integer> sampleIndexes,
                                    List<WindForecast> forecasts, long departEpochSeconds,
                                    long durationSeconds, double calmBelowKmh) {
        List<GeoPoint> points = route.points;
        double[] cumulative = GeoMath.cumulativeMeters(points);
        double total = cumulative[cumulative.length - 1];

        List<Stretch> stretches = new ArrayList<>();
        int from = 0;
        for (int i = 1; i < points.size(); i++) {
            boolean last = i == points.size() - 1;
            double length = cumulative[i] - cumulative[from];
            if (length < STRETCH_METERS && !last) {
                continue;
            }
            if (length <= 0) {
                continue;   // repeated points at the very end of a route
            }
            double middle = (cumulative[from] + cumulative[i]) / 2;

            // Where will the rider be in time? Spread Google's ride duration
            // evenly over the distance. Good enough to pick a forecast hour.
            long arrival = departEpochSeconds
                    + Math.round(durationSeconds * (total > 0 ? middle / total : 0));

            WindForecast forecast = forecasts.get(
                    nearestSample(sampleIndexes, cumulative, middle));
            int hour = forecast.indexAt(arrival);
            double speed = forecast.speedKmh[hour];
            double direction = forecast.directionDeg[hour];

            // The heading of a stretch is the straight line from its first point
            // to its last, which smooths out wiggles shorter than the stretch.
            double heading = GeoMath.bearingDegrees(points.get(from), points.get(i));

            stretches.add(new Stretch(from, i, length,
                    WindMath.classify(heading, direction, speed, calmBelowKmh),
                    WindMath.headwindComponent(heading, direction, speed),
                    speed, forecast.gustKmh[hour]));
            from = i;
        }
        return new RouteWind(stretches);
    }

    /**
     * Analyzes the same geometry from finish to start without another route or
     * weather request. Forecasts are reversed with their sample positions so
     * each forecast stays attached to the same physical place.
     */
    public static RouteWind analyzeReversed(Route route, List<Integer> sampleIndexes,
                                            List<WindForecast> forecasts,
                                            long departEpochSeconds, long durationSeconds,
                                            double calmBelowKmh) {
        List<Integer> reversedIndexes = new ArrayList<>();
        List<WindForecast> reversedForecasts = new ArrayList<>();
        int lastPoint = route.points.size() - 1;
        for (int i = sampleIndexes.size() - 1; i >= 0; i--) {
            reversedIndexes.add(lastPoint - sampleIndexes.get(i));
            reversedForecasts.add(forecasts.get(i));
        }
        return analyze(route.reversed(), reversedIndexes, reversedForecasts,
                departEpochSeconds, durationSeconds, calmBelowKmh);
    }

    /** Position in sampleIndexes of the sample closest, along the road, to a distance. */
    private static int nearestSample(List<Integer> sampleIndexes, double[] cumulative,
                                     double meters) {
        int best = 0;
        double bestGap = Double.MAX_VALUE;
        for (int s = 0; s < sampleIndexes.size(); s++) {
            double gap = Math.abs(cumulative[sampleIndexes.get(s)] - meters);
            if (gap < bestGap) {
                bestGap = gap;
                best = s;
            }
        }
        return best;
    }
}
