package io.github.jamerlybob.windroute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.OpenMeteoClient;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** Fetches free weather samples for a route; it never calls Google Routes. */
public final class RouteForecastLoader {
    private static final double SAMPLE_SPACING_METERS = 5_000;
    private static final int MAX_SAMPLES = 60;

    private RouteForecastLoader() {
    }

    public static Result load(Route route) throws IOException {
        double spacing = Math.max(SAMPLE_SPACING_METERS, route.distanceMeters / MAX_SAMPLES);
        List<Integer> indexes = GeoMath.sampleIndexes(route.points, spacing);
        List<GeoPoint> places = new ArrayList<>();
        for (int index : indexes) {
            places.add(route.points.get(index));
        }
        return new Result(indexes, OpenMeteoClient.fetch(places));
    }

    public static final class Result {
        public final List<Integer> sampleIndexes;
        public final List<WindForecast> forecasts;

        Result(List<Integer> sampleIndexes, List<WindForecast> forecasts) {
            this.sampleIndexes = sampleIndexes;
            this.forecasts = forecasts;
        }
    }
}
