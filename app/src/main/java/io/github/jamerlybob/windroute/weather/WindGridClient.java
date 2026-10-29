package io.github.jamerlybob.windroute.weather;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import io.github.jamerlybob.windroute.Http;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.wind.WindField;

/** One multi-location request, capped at 100 locations to respect the free service. */
public final class WindGridClient {
    private WindGridClient() { }
    public static List<GeoPoint> points(double south, double west, double north, double east) {
        List<GeoPoint> points = new ArrayList<>();
        for (int row = 0; row < 10; row++) for (int col = 0; col < 10; col++)
            points.add(new GeoPoint(south + (north - south) * row / 9,
                    wrapLongitude(west + (east - west) * col / 9)));
        return points;
    }
    private static double wrapLongitude(double longitude) {
        return ((longitude + 180) % 360 + 360) % 360 - 180;
    }
    public static String buildUrl(double south, double west, double north, double east) {
        return OpenMeteoClient.buildUrl(points(south, west, north, east))
                .replace(",temperature_2m,precipitation,precipitation_probability", "")
                .replace("&minutely_15=wind_speed_10m,wind_direction_10m,wind_gusts_10m", "")
                .replace("&forecast_minutely_15=192", "");
    }
    public static WindField parse(String reply, double south, double west, double north, double east)
            throws IOException {
        List<WindForecast> forecasts = OpenMeteoClient.parse(reply);
        if (forecasts.size() != 100) throw new IOException("Incomplete wind grid");
        return new WindField(south, west, north, east, 10, 10, forecasts);
    }
    public static synchronized WindField fetch(double south, double west, double north, double east) throws IOException {
        // Serialise even across Activity recreation: interrupting an HTTP read
        // cannot guarantee that the old connection has already finished.
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
        return parse(Http.get(buildUrl(south, west, north, east)), south, west, north, east);
    }
}
