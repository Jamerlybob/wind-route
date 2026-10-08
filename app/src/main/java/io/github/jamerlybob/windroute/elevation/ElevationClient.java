package io.github.jamerlybob.windroute.elevation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.Http;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/**
 * Fetches ground height from Open-Meteo's free elevation service.
 *
 * <p>The eventual screen must credit both Open-Meteo and the Copernicus
 * programme, as required by the elevation API's acknowledgement section.
 */
public final class ElevationClient {

    public static final int MAX_COORDINATES_PER_REQUEST = 100;
    public static final int MAX_ROUTE_SAMPLES = 500;
    public static final double TARGET_SPACING_METERS = 100.0;

    private static final String BASE = "https://api.open-meteo.com/v1/elevation";

    private ElevationClient() {
    }

    /**
     * Fetches all requested places, splitting them into requests accepted by the service.
     */
    public static double[] fetch(List<GeoPoint> places) throws IOException {
        double[] elevations = new double[places.size()];
        int written = 0;
        for (int from = 0; from < places.size(); from += MAX_COORDINATES_PER_REQUEST) {
            int to = Math.min(from + MAX_COORDINATES_PER_REQUEST, places.size());
            double[] batch = parse(Http.get(buildUrl(places.subList(from, to))));
            if (batch.length != to - from) {
                throw new IOException("The elevation service returned the wrong number of heights.");
            }
            System.arraycopy(batch, 0, elevations, written, batch.length);
            written += batch.length;
        }
        return elevations;
    }

    public static String buildUrl(List<GeoPoint> places) {
        StringBuilder latitudes = new StringBuilder();
        StringBuilder longitudes = new StringBuilder();
        for (GeoPoint place : places) {
            if (latitudes.length() > 0) {
                latitudes.append(',');
                longitudes.append(',');
            }
            // A comma is part of this API's list syntax, so a locale that writes
            // 52.5 as 52,5 would silently turn one coordinate into two.
            latitudes.append(String.format(Locale.US, "%.6f", place.lat));
            longitudes.append(String.format(Locale.US, "%.6f", place.lng));
        }
        return BASE + "?latitude=" + latitudes + "&longitude=" + longitudes;
    }

    public static double[] parse(String json) throws IOException {
        try {
            JSONObject reply = new JSONObject(json);
            if (reply.optBoolean("error")) {
                throw new IOException(reply.optString("reason", "Elevation service error."));
            }
            JSONArray values = reply.getJSONArray("elevation");
            double[] elevations = new double[values.length()];
            for (int i = 0; i < elevations.length; i++) {
                elevations[i] = values.getDouble(i);
            }
            return elevations;
        } catch (JSONException e) {
            throw new IOException("Could not read the elevation service's reply.", e);
        }
    }

    /**
     * Chooses evenly spaced points along a route, including both ends.
     *
     * <p>Open-Meteo uses a 90 m terrain grid, so asking substantially more often
     * than every 100 m would create extra network traffic without adding detail.
     * Long routes widen the spacing enough to stay within five API requests.
     */
    public static List<GeoPoint> sampleRoute(List<GeoPoint> routePoints) {
        if (routePoints.isEmpty()) {
            return new ArrayList<>();
        }
        if (routePoints.size() == 1) {
            return new ArrayList<>(routePoints);
        }

        double[] cumulative = GeoMath.cumulativeMeters(routePoints);
        double total = cumulative[cumulative.length - 1];
        if (total <= 0) {
            return new ArrayList<>(Arrays.asList(routePoints.get(0)));
        }

        int intervals = Math.max(1, (int) Math.ceil(total / TARGET_SPACING_METERS));
        int sampleCount = Math.min(MAX_ROUTE_SAMPLES, intervals + 1);
        List<GeoPoint> samples = new ArrayList<>(sampleCount);
        int segment = 0;
        for (int i = 0; i < sampleCount; i++) {
            double wanted = total * i / (sampleCount - 1.0);
            while (segment < cumulative.length - 2 && cumulative[segment + 1] < wanted) {
                segment++;
            }
            samples.add(interpolate(routePoints.get(segment), routePoints.get(segment + 1),
                    cumulative[segment], cumulative[segment + 1], wanted));
        }
        return samples;
    }

    /** A descriptive alias for callers which are choosing elevation samples. */
    public static List<GeoPoint> samplePoints(List<GeoPoint> routePoints) {
        return sampleRoute(routePoints);
    }

    private static GeoPoint interpolate(GeoPoint start, GeoPoint end, double startMeters,
                                        double endMeters, double wantedMeters) {
        double length = endMeters - startMeters;
        if (length <= 0) {
            return start;
        }
        double fraction = (wantedMeters - startMeters) / length;
        return new GeoPoint(start.lat + (end.lat - start.lat) * fraction,
                start.lng + (end.lng - start.lng) * fraction);
    }
}
