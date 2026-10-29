package io.github.jamerlybob.windroute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.OpenMeteoClient;
import io.github.jamerlybob.windroute.weather.WindForecast;

/**
 * Chooses weather sample points and fetches their forecasts in one place.
 * Separating this from route loading lets restores refresh free Open-Meteo
 * data without accidentally making another quota-limited Google Routes call.
 */
public final class RouteForecastLoader {
    private RouteForecastLoader() {
    }

    public static Result load(Route route) throws IOException {
        return load(route, System.currentTimeMillis()/1000);
    }

    public static Result load(Route route, long departure) throws IOException {
        List<Integer> indexes = io.github.jamerlybob.windroute.route.RouteSamples.indexes(route.points);
        List<GeoPoint> places = new ArrayList<>();
        for (int index : indexes) {
            places.add(route.points.get(index));
        }
        return new Result(indexes, OpenMeteoClient.fetch(places, departure < System.currentTimeMillis()/1000 + 48*3600));
    }

    /** Keeps each forecast paired with the route index it was sampled from. */
    public static final class Result {
        public final List<Integer> sampleIndexes;
        public final List<WindForecast> forecasts;

        public Result(List<Integer> sampleIndexes, List<WindForecast> forecasts) {
            this.sampleIndexes = sampleIndexes;
            this.forecasts = forecasts;
        }
    }
}
