package io.github.jamerlybob.windroute.wind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.RideWeather;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** Compares hourly departure choices using weather that has already been fetched. */
public final class DepartureScorer {

    public static final int DEFAULT_HOURS = 24;
    public static final long HOUR_SECONDS = 3600;

    /** Each wettest-hour millimetre counts like 3 km/h of extra headwind. */
    public static final double RAIN_MM_WEIGHT = 3.0;
    /** A 100% rain chance counts like 5 km/h of extra headwind. */
    public static final double RAIN_CHANCE_WEIGHT = 5.0;
    /** Gusts below this are common enough not to affect the departure score. */
    public static final double STRONG_GUST_KMH = 45.0;
    /** Each km/h above the strong-gust mark adds a small but visible penalty. */
    public static final double GUST_WEIGHT = 0.25;

    /** The summary and score for one possible departure hour. Lower is better. */
    public static final class Entry {
        public final long departureEpochSeconds;
        public final double averageHeadwindKmh;
        public final double headwindShare;
        public final double tailwindShare;
        public final double crosswindShare;
        public final double calmShare;
        public final double maxGustKmh;
        public final boolean hasRainData;
        public final double wettestRainMm;
        public final double rainChancePercent;
        /** Score parts are public so the UI can explain why an hour won. */
        public final double rainPenalty;
        public final double gustPenalty;
        /** Equivalent km/h of headwind after weather penalties. Lower is better. */
        public final double score;

        private Entry(long departureEpochSeconds, RouteWind wind, RideWeather weather) {
            this.departureEpochSeconds = departureEpochSeconds;
            this.averageHeadwindKmh = wind.averageHeadwindKmh;
            this.headwindShare = wind.share(WindEffect.HEADWIND);
            this.tailwindShare = wind.share(WindEffect.TAILWIND);
            this.crosswindShare = wind.share(WindEffect.CROSSWIND);
            this.calmShare = wind.share(WindEffect.CALM);
            this.maxGustKmh = wind.maxGustKmh;
            this.hasRainData = weather.hasRainData();
            this.wettestRainMm = weather.wettestRainMm;
            this.rainChancePercent = weather.chanceOfAnyRainPercent;

            double amountPenalty = Double.isNaN(wettestRainMm)
                    ? 0 : wettestRainMm * RAIN_MM_WEIGHT;
            double chancePenalty = Double.isNaN(rainChancePercent)
                    ? 0 : rainChancePercent / 100.0 * RAIN_CHANCE_WEIGHT;
            this.rainPenalty = amountPenalty + chancePenalty;
            this.gustPenalty = Math.max(0, maxGustKmh - STRONG_GUST_KMH) * GUST_WEIGHT;
            this.score = averageHeadwindKmh + rainPenalty + gustPenalty;
        }
    }

    private DepartureScorer() {
    }

    public static List<Entry> score(Route route, List<Integer> sampleIndexes,
                                    List<WindForecast> forecasts,
                                    long firstDepartureEpochSeconds) {
        return score(route, sampleIndexes, forecasts, firstDepartureEpochSeconds,
                DEFAULT_HOURS);
    }

    /**
     * Returns one entry for each covered hourly departure. A lower score wins:
     * net tailwind can make the base negative, while rain and unusually strong
     * gusts add named penalties that are deliberately easy to inspect.
     */
    public static List<Entry> score(Route route, List<Integer> sampleIndexes,
                                    List<WindForecast> forecasts,
                                    long firstDepartureEpochSeconds, int hours) {
        if (sampleIndexes.size() != forecasts.size()) {
            throw new IllegalArgumentException("Each sample point needs one forecast.");
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < hours; i++) {
            long departure = firstDepartureEpochSeconds + i * HOUR_SECONDS;
            if (!forecastCoversRide(forecasts, departure, route.durationSeconds)) {
                continue;
            }
            RouteWind wind = RouteWind.analyze(route, sampleIndexes, forecasts, departure);
            RideWeather weather = RideWeather.analyze(
                    route, sampleIndexes, forecasts, departure);
            entries.add(new Entry(departure, wind, weather));
        }
        return Collections.unmodifiableList(entries);
    }

    private static boolean forecastCoversRide(List<WindForecast> forecasts,
                                              long departure, long durationSeconds) {
        if (forecasts.isEmpty()) {
            return false;
        }
        long arrival = departure + Math.max(0, durationSeconds);
        for (WindForecast forecast : forecasts) {
            if (forecast.epochSeconds.length == 0) {
                return false;
            }
            long start = forecast.epochSeconds[0];
            long end = endOfLastForecastHour(forecast.epochSeconds);
            if (departure < start || arrival > end) {
                return false;
            }
        }
        return true;
    }

    private static long endOfLastForecastHour(long[] times) {
        long hourLength = HOUR_SECONDS;
        if (times.length > 1) {
            long lastGap = times[times.length - 1] - times[times.length - 2];
            if (lastGap > 0) {
                hourLength = lastGap;
            }
        }
        return times[times.length - 1] + hourLength;
    }
}
