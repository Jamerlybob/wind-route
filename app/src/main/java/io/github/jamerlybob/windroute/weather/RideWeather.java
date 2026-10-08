package io.github.jamerlybob.windroute.weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.Route;

/** Weather the rider meets at the forecast sample points along a route. */
public final class RideWeather {

    /** Weather at one sampled route point, at the time the rider reaches it. */
    public static final class Point {
        public final int routePointIndex;
        public final double distanceMeters;
        public final long arrivalEpochSeconds;
        public final double temperatureC;
        public final double precipitationMm;
        public final double precipitationProbabilityPercent;

        private Point(int routePointIndex, double distanceMeters, long arrivalEpochSeconds,
                      double temperatureC, double precipitationMm,
                      double precipitationProbabilityPercent) {
            this.routePointIndex = routePointIndex;
            this.distanceMeters = distanceMeters;
            this.arrivalEpochSeconds = arrivalEpochSeconds;
            this.temperatureC = temperatureC;
            this.precipitationMm = precipitationMm;
            this.precipitationProbabilityPercent = precipitationProbabilityPercent;
        }
    }

    public final List<Point> points;
    /** Forecast-hour timestamp for the wettest sampled point, or -1 when unknown. */
    public final long wettestHourEpochSeconds;
    /** Rainfall at {@link #wettestHourEpochSeconds}, or NaN when unknown. */
    public final double wettestRainMm;
    /** Maximum forecast probability met on the ride, or NaN when unknown. */
    public final double chanceOfAnyRainPercent;
    public final double coldestC;
    public final double warmestC;

    private RideWeather(List<Point> points, long wettestHourEpochSeconds,
                        double wettestRainMm, double chanceOfAnyRainPercent,
                        double coldestC, double warmestC) {
        this.points = Collections.unmodifiableList(points);
        this.wettestHourEpochSeconds = wettestHourEpochSeconds;
        this.wettestRainMm = wettestRainMm;
        this.chanceOfAnyRainPercent = chanceOfAnyRainPercent;
        this.coldestC = coldestC;
        this.warmestC = warmestC;
    }

    /** True when at least one rainfall amount or probability was supplied. */
    public boolean hasRainData() {
        return !Double.isNaN(wettestRainMm) || !Double.isNaN(chanceOfAnyRainPercent);
    }

    /**
     * Spreads travel time evenly over route distance, matching
     * {@code RouteWind.analyze}. Each forecast belongs to the sample index in
     * the same list position.
     */
    public static RideWeather analyze(Route route, List<Integer> sampleIndexes,
                                      List<WindForecast> forecasts,
                                      long departEpochSeconds) {
        if (sampleIndexes.size() != forecasts.size()) {
            throw new IllegalArgumentException("Each sample point needs one forecast.");
        }
        if (route.points.isEmpty()) {
            return empty();
        }

        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        double total = cumulative[cumulative.length - 1];
        List<Point> points = new ArrayList<>();
        long wettestHour = -1;
        double wettestRain = Double.NaN;
        double rainChance = Double.NaN;
        double coldest = Double.NaN;
        double warmest = Double.NaN;

        for (int i = 0; i < sampleIndexes.size(); i++) {
            int routeIndex = sampleIndexes.get(i);
            if (routeIndex < 0 || routeIndex >= route.points.size()) {
                throw new IllegalArgumentException("Sample index is outside the route.");
            }
            double distance = cumulative[routeIndex];
            long arrival = departEpochSeconds + Math.round(route.durationSeconds
                    * (total > 0 ? distance / total : 0));
            WindForecast forecast = forecasts.get(i);
            int hour = forecast.indexAt(arrival);
            double temperature = valueAt(forecast.temperatureC, hour);
            double rain = valueAt(forecast.precipitationMm, hour);
            double probability = valueAt(forecast.precipitationProbabilityPercent, hour);
            points.add(new Point(routeIndex, distance, arrival,
                    temperature, rain, probability));

            coldest = smallerKnown(coldest, temperature);
            warmest = largerKnown(warmest, temperature);
            rainChance = largerKnown(rainChance, probability);
            if (!Double.isNaN(rain) && (Double.isNaN(wettestRain) || rain > wettestRain)) {
                wettestRain = rain;
                wettestHour = forecast.epochSeconds[hour];
            }
        }

        // Hourly probabilities at nearby places are strongly related, so adding
        // them as independent events would exaggerate the chance. The maximum is
        // a conservative, easy-to-explain "chance anywhere on this ride".
        return new RideWeather(points, wettestHour, wettestRain, rainChance,
                coldest, warmest);
    }

    private static RideWeather empty() {
        return new RideWeather(new ArrayList<Point>(), -1, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN);
    }

    private static double valueAt(double[] values, int index) {
        return index < values.length ? values[index] : Double.NaN;
    }

    private static double smallerKnown(double current, double candidate) {
        if (Double.isNaN(candidate)) {
            return current;
        }
        return Double.isNaN(current) ? candidate : Math.min(current, candidate);
    }

    private static double largerKnown(double current, double candidate) {
        if (Double.isNaN(candidate)) {
            return current;
        }
        return Double.isNaN(current) ? candidate : Math.max(current, candidate);
    }
}
