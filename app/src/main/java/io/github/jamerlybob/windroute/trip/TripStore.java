package io.github.jamerlybob.windroute.trip;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.jamerlybob.windroute.poi.Poi;
import io.github.jamerlybob.windroute.RouteForecastLoader;
import io.github.jamerlybob.windroute.poi.PoiKind;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteJson;
import io.github.jamerlybob.windroute.weather.DaylightForecast;
import io.github.jamerlybob.windroute.weather.WindForecast;

/** Stores complete trip plans as private JSON files for offline opening. */
public final class TripStore {
    private static final String DIRECTORY = "trips";
    private static final String DRAFT = "draft.json";
    private final File directory;

    /** Forecast-only value used to test the offline JSON without Android storage. */
    public static final class ForecastCache {
        public final List<RouteForecastLoader.Result> forecasts;
        public final List<List<DaylightForecast>> daylight;

        ForecastCache(List<RouteForecastLoader.Result> forecasts,
                      List<List<DaylightForecast>> daylight) {
            this.forecasts = forecasts;
            this.daylight = daylight;
        }
    }

    public TripStore(Context context) {
        directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists()) directory.mkdirs();
    }

    public static final class SavedTrip {
        public final String id;
        public final String name;
        public final Route route;
        public final double[] elevationDistances;
        public final double[] elevations;
        public final double[] boundaries;
        public final List<Poi> places;
        public final long forecastFetchedAt;
        public final List<RouteForecastLoader.Result> forecasts;
        public final List<List<DaylightForecast>> daylight;
        public final long startDayMillis;
        public final int startHour;
        public final int startMinute;

        public SavedTrip(String id, String name, Route route, double[] elevations,
                         double[] boundaries, List<Poi> places, long fetchedAt) {
            this(id, name, route, null, elevations, boundaries, places, fetchedAt,
                    new ArrayList<>(), new ArrayList<>(), 0, 8, 0);
        }

        public SavedTrip(String id, String name, Route route, double[] elevationDistances,
                         double[] elevations,
                         double[] boundaries, List<Poi> places, long fetchedAt,
                         List<RouteForecastLoader.Result> forecasts,
                         List<List<DaylightForecast>> daylight, long startDayMillis,
                         int startHour, int startMinute) {
            this.id = id;
            this.name = name;
            this.route = route;
            this.elevationDistances = elevationDistances;
            this.elevations = elevations;
            this.boundaries = boundaries;
            this.places = places;
            this.forecastFetchedAt = fetchedAt;
            this.forecasts = forecasts;
            this.daylight = daylight;
            this.startDayMillis = startDayMillis;
            this.startHour = startHour;
            this.startMinute = startMinute;
        }
    }

    public void saveDraft(Route route, double[] elevationDistances, double[] elevations)
            throws IOException {
        write(new File(directory, DRAFT), new SavedTrip("draft", "New trip", route,
                elevationDistances, elevations, new double[0], new ArrayList<>(), 0,
                new ArrayList<>(), new ArrayList<>(), 0, 8, 0));
    }

    public SavedTrip loadDraft() throws IOException {
        return read(new File(directory, DRAFT));
    }

    public String save(SavedTrip trip) throws IOException {
        String id = trip.id == null || trip.id.equals("draft")
                ? UUID.randomUUID().toString() : trip.id;
        write(new File(directory, id + ".json"), new SavedTrip(id, trip.name, trip.route,
                trip.elevationDistances, trip.elevations, trip.boundaries, trip.places,
                trip.forecastFetchedAt,
                trip.forecasts, trip.daylight, trip.startDayMillis,
                trip.startHour, trip.startMinute));
        return id;
    }

    public List<SavedTrip> list() {
        List<SavedTrip> result = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json")
                && !name.equals(DRAFT));
        if (files == null) return result;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            try { result.add(read(file)); } catch (IOException ignored) {
                // A damaged plan should not hide the other saved trips.
            }
        }
        return result;
    }

    public SavedTrip load(String id) throws IOException {
        return read(new File(directory, id + ".json"));
    }

    public void delete(String id) {
        new File(directory, id + ".json").delete();
    }

    private static void write(File file, SavedTrip trip) throws IOException {
        try {
            JSONObject root = new JSONObject().put("id", trip.id).put("name", trip.name)
                    .put("route", new JSONObject(RouteJson.write(trip.route)))
                    .put("fetchedAt", trip.forecastFetchedAt)
                    .put("startDayMillis", trip.startDayMillis)
                    .put("startHour", trip.startHour).put("startMinute", trip.startMinute);
            root.put("elevations", numbers(trip.elevations));
            root.put("elevationDistances", numbers(trip.elevationDistances));
            root.put("boundaries", numbers(trip.boundaries));
            JSONArray places = new JSONArray();
            for (Poi place : trip.places) {
                places.put(new JSONObject().put("kind", place.kind.name())
                        .put("name", place.name).put("lat", place.position.lat)
                        .put("lng", place.position.lng).put("hours", place.openingHours));
            }
            root.put("places", places);
            root.put("forecasts", writeForecasts(trip.forecasts));
            root.put("daylight", writeDaylight(trip.daylight));
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (JSONException error) {
            throw new IOException("Could not save this trip.", error);
        }
    }

    private static SavedTrip read(File file) throws IOException {
        try {
            JSONObject root = new JSONObject(readFile(file));
            List<Poi> places = new ArrayList<>();
            JSONArray rawPlaces = root.optJSONArray("places");
            for (int i = 0; rawPlaces != null && i < rawPlaces.length(); i++) {
                JSONObject place = rawPlaces.getJSONObject(i);
                places.add(new Poi(PoiKind.valueOf(place.getString("kind")),
                        nullable(place, "name"), new GeoPoint(place.getDouble("lat"),
                        place.getDouble("lng")), nullable(place, "hours")));
            }
            return new SavedTrip(root.optString("id", "draft"), root.optString("name", "Trip"),
                    RouteJson.read(root.getJSONObject("route").toString()),
                    doubles(root.optJSONArray("elevationDistances")),
                    doubles(root.optJSONArray("elevations")),
                    doubles(root.optJSONArray("boundaries")), places,
                    root.optLong("fetchedAt", 0),
                    readForecasts(root.optJSONArray("forecasts")),
                    readDaylight(root.optJSONArray("daylight")),
                    root.optLong("startDayMillis", 0), root.optInt("startHour", 8),
                    root.optInt("startMinute", 0));
        } catch (JSONException | RuntimeException error) {
            throw new IOException("Could not open this saved trip.", error);
        }
    }

    private static JSONArray numbers(double[] values) throws JSONException {
        JSONArray result = new JSONArray();
        if (values != null) for (double value : values) result.put(value);
        return result;
    }

    private static double[] doubles(JSONArray values) throws JSONException {
        if (values == null) return null;
        double[] result = new double[values.length()];
        for (int i = 0; i < result.length; i++) result[i] = values.getDouble(i);
        return result;
    }

    private static String nullable(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONArray writeForecasts(List<RouteForecastLoader.Result> days)
            throws JSONException {
        JSONArray result = new JSONArray();
        for (RouteForecastLoader.Result day : days) {
            JSONArray indexes = new JSONArray();
            for (int index : day.sampleIndexes) indexes.put(index);
            JSONArray forecasts = new JSONArray();
            for (WindForecast forecast : day.forecasts) {
                forecasts.put(new JSONObject().put("time", longs(forecast.epochSeconds))
                        .put("speed", nullableNumbers(forecast.speedKmh))
                        .put("direction", nullableNumbers(forecast.directionDeg))
                        .put("gust", nullableNumbers(forecast.gustKmh))
                        .put("temperature", nullableNumbers(forecast.temperatureC))
                        .put("rain", nullableNumbers(forecast.precipitationMm))
                        .put("probability", nullableNumbers(
                                forecast.precipitationProbabilityPercent)));
            }
            result.put(new JSONObject().put("indexes", indexes).put("values", forecasts));
        }
        return result;
    }

    public static String encodeForecastCache(List<RouteForecastLoader.Result> forecasts,
                                             List<List<DaylightForecast>> daylight)
            throws JSONException {
        return new JSONObject().put("forecasts", writeForecasts(forecasts))
                .put("daylight", writeDaylight(daylight)).toString();
    }

    public static ForecastCache decodeForecastCache(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        return new ForecastCache(readForecasts(root.getJSONArray("forecasts")),
                readDaylight(root.getJSONArray("daylight")));
    }

    private static List<RouteForecastLoader.Result> readForecasts(JSONArray raw)
            throws JSONException {
        List<RouteForecastLoader.Result> result = new ArrayList<>();
        for (int d = 0; raw != null && d < raw.length(); d++) {
            JSONObject day = raw.getJSONObject(d);
            List<Integer> indexes = new ArrayList<>();
            JSONArray rawIndexes = day.getJSONArray("indexes");
            for (int i = 0; i < rawIndexes.length(); i++) indexes.add(rawIndexes.getInt(i));
            List<WindForecast> forecasts = new ArrayList<>();
            JSONArray values = day.getJSONArray("values");
            for (int i = 0; i < values.length(); i++) {
                JSONObject forecast = values.getJSONObject(i);
                forecasts.add(new WindForecast(readLongs(forecast.getJSONArray("time")),
                        readNullableNumbers(forecast.getJSONArray("speed")),
                        readNullableNumbers(forecast.getJSONArray("direction")),
                        readNullableNumbers(forecast.getJSONArray("gust")),
                        readNullableNumbers(forecast.getJSONArray("temperature")),
                        readNullableNumbers(forecast.getJSONArray("rain")),
                        readNullableNumbers(forecast.getJSONArray("probability"))));
            }
            result.add(new RouteForecastLoader.Result(indexes, forecasts));
        }
        return result;
    }

    private static JSONArray writeDaylight(List<List<DaylightForecast>> days)
            throws JSONException {
        JSONArray result = new JSONArray();
        for (List<DaylightForecast> day : days) {
            JSONArray values = new JSONArray();
            for (DaylightForecast light : day) {
                values.put(new JSONObject().put("day", light.dayEpochSeconds)
                        .put("sunrise", light.sunriseEpochSeconds)
                        .put("sunset", light.sunsetEpochSeconds));
            }
            result.put(values);
        }
        return result;
    }

    private static List<List<DaylightForecast>> readDaylight(JSONArray raw)
            throws JSONException {
        List<List<DaylightForecast>> result = new ArrayList<>();
        for (int d = 0; raw != null && d < raw.length(); d++) {
            List<DaylightForecast> day = new ArrayList<>();
            JSONArray values = raw.getJSONArray(d);
            for (int i = 0; i < values.length(); i++) {
                JSONObject light = values.getJSONObject(i);
                day.add(new DaylightForecast(light.getLong("day"),
                        light.getLong("sunrise"), light.getLong("sunset")));
            }
            result.add(day);
        }
        return result;
    }

    private static JSONArray longs(long[] values) {
        JSONArray result = new JSONArray();
        for (long value : values) result.put(value);
        return result;
    }

    private static long[] readLongs(JSONArray values) throws JSONException {
        long[] result = new long[values.length()];
        for (int i = 0; i < result.length; i++) result[i] = values.getLong(i);
        return result;
    }

    private static JSONArray nullableNumbers(double[] values) throws JSONException {
        JSONArray result = new JSONArray();
        for (double value : values) {
            result.put(Double.isFinite(value) ? Double.valueOf(value) : JSONObject.NULL);
        }
        return result;
    }

    private static double[] readNullableNumbers(JSONArray values) throws JSONException {
        double[] result = new double[values.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.isNull(i) ? Double.NaN : values.getDouble(i);
        }
        return result;
    }
}
