package io.github.jamerlybob.windroute.wind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** Finds the gusty crosswind sections that could push a rider sideways. */
public final class GustWarnings {

    /** A gust starts being worth a prominent warning at about 28 mph. */
    public static final double DEFAULT_THRESHOLD_KMH = 45.0;

    public enum Side {
        LEFT,
        RIGHT
    }

    /** One continuous warning section, measured from the route start. */
    public static final class Warning {
        public final double startMeters;
        public final double lengthMeters;
        public final Side side;
        public final double maxGustKmh;

        private Warning(double startMeters, double lengthMeters,
                        Side side, double maxGustKmh) {
            this.startMeters = startMeters;
            this.lengthMeters = lengthMeters;
            this.side = side;
            this.maxGustKmh = maxGustKmh;
        }
    }

    private GustWarnings() {
    }

    public static List<Warning> find(RouteWind routeWind, Route route,
                                     List<Integer> sampleIndexes,
                                     List<WindForecast> forecasts,
                                     long departEpochSeconds) {
        return find(routeWind, route, sampleIndexes, forecasts,
                departEpochSeconds, DEFAULT_THRESHOLD_KMH);
    }

    /**
     * The wind direction is looked up again because {@link RouteWind.Stretch}
     * intentionally stores only the along-road component. Its midpoint, arrival
     * time and nearest forecast are calculated in the same way as RouteWind.
     */
    public static List<Warning> find(RouteWind routeWind, Route route,
                                     List<Integer> sampleIndexes,
                                     List<WindForecast> forecasts,
                                     long departEpochSeconds, double thresholdKmh) {
        if (sampleIndexes.size() != forecasts.size()) {
            throw new IllegalArgumentException("Each sample point needs one forecast.");
        }
        if (route.points.isEmpty()) {
            return Collections.emptyList();
        }

        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        double total = cumulative[cumulative.length - 1];
        List<Warning> warnings = new ArrayList<>();
        double start = 0;
        double length = 0;
        double maxGust = 0;
        Side side = null;

        for (RouteWind.Stretch stretch : routeWind.stretches) {
            Side stretchSide = warningSide(stretch, route, sampleIndexes, forecasts,
                    cumulative, total, departEpochSeconds, thresholdKmh);
            if (stretchSide == null) {
                if (side != null) {
                    warnings.add(new Warning(start, length, side, maxGust));
                    side = null;
                }
                continue;
            }

            if (side == null) {
                start = cumulative[stretch.fromIndex];
                length = stretch.lengthMeters;
                maxGust = stretch.gustKmh;
                side = stretchSide;
            } else if (side == stretchSide) {
                // Consecutive stretches with the same side are one useful road warning.
                length += stretch.lengthMeters;
                maxGust = Math.max(maxGust, stretch.gustKmh);
            } else {
                warnings.add(new Warning(start, length, side, maxGust));
                start = cumulative[stretch.fromIndex];
                length = stretch.lengthMeters;
                maxGust = stretch.gustKmh;
                side = stretchSide;
            }
        }
        if (side != null) {
            warnings.add(new Warning(start, length, side, maxGust));
        }
        return Collections.unmodifiableList(warnings);
    }

    private static Side warningSide(RouteWind.Stretch stretch, Route route,
                                    List<Integer> sampleIndexes,
                                    List<WindForecast> forecasts, double[] cumulative,
                                    double total, long departure, double thresholdKmh) {
        if (stretch.effect != WindEffect.CROSSWIND || stretch.gustKmh <= thresholdKmh) {
            return null;
        }
        double middle = (cumulative[stretch.fromIndex] + cumulative[stretch.toIndex]) / 2;
        long arrival = departure + Math.round(route.durationSeconds
                * (total > 0 ? middle / total : 0));
        WindForecast forecast = forecasts.get(nearestSample(sampleIndexes, cumulative, middle));
        int hour = forecast.indexAt(arrival);
        GeoPoint from = route.points.get(stretch.fromIndex);
        GeoPoint to = route.points.get(stretch.toIndex);
        double heading = GeoMath.bearingDegrees(from, to);
        double crosswind = WindMath.crosswindComponent(
                heading, forecast.directionDeg[hour], forecast.speedKmh[hour]);
        return crosswind >= 0 ? Side.RIGHT : Side.LEFT;
    }

    private static int nearestSample(List<Integer> sampleIndexes, double[] cumulative,
                                     double meters) {
        int best = 0;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < sampleIndexes.size(); i++) {
            double gap = Math.abs(cumulative[sampleIndexes.get(i)] - meters);
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }
}
