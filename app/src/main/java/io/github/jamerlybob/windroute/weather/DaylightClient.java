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

/** Fetches Open-Meteo's daily sunrise and sunset values for a route start. */
public final class DaylightClient {
    private static final String BASE = "https://api.open-meteo.com/v1/forecast";

    private DaylightClient() {
    }

    public static List<DaylightForecast> fetch(GeoPoint point) throws IOException {
        return parse(Http.get(buildUrl(point)));
    }

    public static String buildUrl(GeoPoint point) {
        return String.format(Locale.US, "%s?latitude=%.5f&longitude=%.5f"
                        + "&daily=sunrise,sunset&timezone=auto&timeformat=unixtime&forecast_days=8",
                BASE, point.lat, point.lng);
    }

    public static List<DaylightForecast> parse(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("error")) {
                throw new IOException(root.optString("reason", "Daylight service error."));
            }
            JSONObject daily = root.getJSONObject("daily");
            JSONArray days = daily.getJSONArray("time");
            JSONArray sunrises = daily.getJSONArray("sunrise");
            JSONArray sunsets = daily.getJSONArray("sunset");
            int offset = root.optInt("utc_offset_seconds", 0);
            List<DaylightForecast> result = new ArrayList<>();
            for (int i = 0; i < days.length(); i++) {
                // Open-Meteo documents that daily unix dates need this offset
                // added to represent the location's calendar day correctly.
                result.add(new DaylightForecast(days.getLong(i) + offset,
                        sunrises.getLong(i), sunsets.getLong(i)));
            }
            return result;
        } catch (JSONException error) {
            throw new IOException("Could not read the daylight forecast.", error);
        }
    }
}
