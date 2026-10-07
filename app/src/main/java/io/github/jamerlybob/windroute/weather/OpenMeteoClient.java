package io.github.jamerlybob.windroute.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.Http;
import io.github.jamerlybob.windroute.route.GeoPoint;

/**
 * Fetches hourly wind forecasts from Open-Meteo for a list of places in one request.
 *
 * <p>Open-Meteo needs no API key and is free for non-commercial use. Its wind
 * direction follows the meteorological convention: the direction the wind is
 * coming FROM, which is what {@code WindMath} expects.
 */
public final class OpenMeteoClient {

    private static final String BASE = "https://api.open-meteo.com/v1/forecast";

    private OpenMeteoClient() {
    }

    public static List<WindForecast> fetch(List<GeoPoint> places) throws IOException {
        return parse(Http.get(buildUrl(places)));
    }

    public static String buildUrl(List<GeoPoint> places) {
        StringBuilder lats = new StringBuilder();
        StringBuilder lngs = new StringBuilder();
        for (GeoPoint place : places) {
            if (lats.length() > 0) {
                lats.append(',');
                lngs.append(',');
            }
            // Locale.US so the decimal separator is a dot on every phone. In a
            // German locale 52.5 would print as "52,5" and split into two places.
            lats.append(String.format(Locale.US, "%.4f", place.lat));
            lngs.append(String.format(Locale.US, "%.4f", place.lng));
        }
        return BASE + "?latitude=" + lats + "&longitude=" + lngs
                + "&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m"
                + "&wind_speed_unit=kmh"
                // Times as plain seconds since 1970, so no time zone parsing is needed.
                + "&timeformat=unixtime&timezone=GMT"
                + "&forecast_days=2";
    }

    public static List<WindForecast> parse(String json) throws IOException {
        try {
            // One place comes back as a single object, several as an array of them.
            JSONArray locations;
            if (json.trim().startsWith("[")) {
                locations = new JSONArray(json);
            } else {
                JSONObject single = new JSONObject(json);
                if (single.optBoolean("error")) {
                    throw new IOException(single.optString("reason", "Weather service error."));
                }
                locations = new JSONArray().put(single);
            }

            List<WindForecast> forecasts = new ArrayList<>();
            for (int i = 0; i < locations.length(); i++) {
                JSONObject hourly = locations.getJSONObject(i).getJSONObject("hourly");
                JSONArray times = hourly.getJSONArray("time");
                long[] epoch = new long[times.length()];
                for (int t = 0; t < epoch.length; t++) {
                    epoch[t] = times.getLong(t);
                }
                forecasts.add(new WindForecast(epoch,
                        numbers(hourly.getJSONArray("wind_speed_10m")),
                        numbers(hourly.getJSONArray("wind_direction_10m")),
                        numbers(hourly.getJSONArray("wind_gusts_10m"))));
            }
            return forecasts;
        } catch (JSONException e) {
            throw new IOException("Could not read the weather service's reply.", e);
        }
    }

    /** A missing hour arrives as null. Treat it as no wind rather than crash. */
    private static double[] numbers(JSONArray array) {
        double[] values = new double[array.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.isNull(i) ? 0.0 : array.optDouble(i, 0.0);
        }
        return values;
    }
}
