package io.github.jamerlybob.windroute.trip;

import java.util.List;
import io.github.jamerlybob.windroute.RideAnalysis;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.RideWeather;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.RouteWind;

/** Compare the same road one day later, using cached forecasts only. */
public final class DayAdvice {
    public enum Suggestion { NONE, LESS_HEADWIND, LESS_RAIN }
    public final boolean badDay;
    public final Suggestion suggestion;
    public final double laterHeadwindKmh;
    public final double laterRainMm;

    private DayAdvice(boolean badDay, Suggestion suggestion, double headwind, double rain) {
        this.badDay = badDay;
        this.suggestion = suggestion;
        this.laterHeadwindKmh = headwind;
        this.laterRainMm = rain;
    }

    public static DayAdvice compare(Route route, RouteWind currentWind, RideWeather currentWeather,
                                    List<Integer> indexes, List<WindForecast> forecasts,
                                    long departure, double calmBelowKmh) {
        boolean bad = DayVerdict.bad(currentWind.averageHeadwindKmh, currentWeather.wettestRainMm);
        long later = departure + 86400;
        // Do not describe a clamped end-of-forecast value as tomorrow's forecast.
        if (!bad || !RideAnalysis.forecastCovers(forecasts, later, route.durationSeconds)) {
            return new DayAdvice(bad, Suggestion.NONE, Double.NaN, Double.NaN);
        }
        RouteWind wind = RouteWind.analyze(route, indexes, forecasts, later,
                route.durationSeconds, calmBelowKmh);
        RideWeather weather = RideWeather.analyze(route, indexes, forecasts, later);
        Suggestion suggestion = Suggestion.NONE;
        if (DayVerdict.suggestLater(currentWind.averageHeadwindKmh, wind.averageHeadwindKmh)) {
            suggestion = Suggestion.LESS_HEADWIND;
        } else if (DayVerdict.suggestDrier(currentWeather.wettestRainMm, weather.wettestRainMm)) {
            suggestion = Suggestion.LESS_RAIN;
        }
        return new DayAdvice(bad, suggestion, wind.averageHeadwindKmh, weather.wettestRainMm);
    }
}
