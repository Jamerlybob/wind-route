package io.github.jamerlybob.windroute.poi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/**
 * Finds useful OpenStreetMap places along a route through the Overpass API.
 *
 * <p>The public Overpass instance is suitable for James's personal use: its stated
 * expectation is at most about 10,000 requests and 1 GB per user per day, and it
 * can answer with HTTP 429 or 504 when busy. It must not become the backend of a
 * widely distributed app. Revisit the service and usage policy before any Play
 * Store release.
 */
public final class OverpassClient {

    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final int TIMEOUT_MS = 30_000;
    private static final int MAX_ROUTE_POINTS = 100;

    private OverpassClient() {
    }

    /** Fetches all requested kinds in one Overpass request. Never call on the main thread. */
    public static List<Poi> fetch(List<GeoPoint> points, Collection<PoiKind> kinds,
                                  int corridorMeters) throws IOException {
        String query = buildQuery(points, kinds, corridorMeters);
        String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name());

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("User-Agent", "WindRoute/0.1 (personal cycling route planner)");
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream response = status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            if (response == null) {
                throw new IOException("Places service returned HTTP " + status + ".");
            }
            String responseBody;
            try (InputStream in = response) {
                responseBody = readAll(in);
            }
            if (status >= 400) {
                throw new IOException("Places service returned HTTP " + status + ".");
            }
            return parse(responseBody);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Builds one query whose {@code around} filter follows the whole route.
     *
     * <p>Long routes are replaced by at most 100 points at equal distances. That
     * keeps the request bounded without clustering all the samples in a section
     * where the route provider happened to use many short line segments.
     */
    public static String buildQuery(List<GeoPoint> points, Collection<PoiKind> kinds,
                                    int corridorMeters) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("A route needs at least one point.");
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("At least one kind is required.");
        }
        if (corridorMeters <= 0) {
            throw new IllegalArgumentException("The corridor must be wider than zero.");
        }

        String around = aroundFilter(evenlySpacedPoints(points), corridorMeters);
        StringBuilder query = new StringBuilder("[out:json][timeout:25];\n(\n");
        for (PoiKind kind : PoiKind.values()) {
            if (!kinds.contains(kind)) {
                continue;
            }
            for (int tag = 0; tag < kind.tagCount(); tag++) {
                appendSearch(query, "node", kind.tagKey(tag), kind.tagValue(tag), around);
                appendSearch(query, "way", kind.tagKey(tag), kind.tagValue(tag), around);
            }
        }
        query.append(");\nout center;");
        return query.toString();
    }

    /**
     * Parses nodes and way centres from an Overpass JSON reply.
     *
     * <p>If an element has tags for several kinds, the declaration order in
     * {@link PoiKind} wins: water, food, camping, bike shop, toilets, shelter.
     */
    public static List<Poi> parse(String json) throws IOException {
        try {
            JSONArray elements = new JSONObject(json).getJSONArray("elements");
            List<Poi> places = new ArrayList<>();
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                JSONObject tags = element.optJSONObject("tags");
                PoiKind kind = firstMatchingKind(tags);
                GeoPoint position = positionOf(element);
                if (kind == null || position == null) {
                    continue;
                }
                places.add(new Poi(kind, nullableTag(tags, "name"), position,
                        nullableTag(tags, "opening_hours")));
            }
            return places;
        } catch (JSONException e) {
            throw new IOException("Could not read the places service's reply.", e);
        }
    }

    private static PoiKind firstMatchingKind(JSONObject tags) {
        if (tags == null) {
            return null;
        }
        for (PoiKind kind : PoiKind.values()) {
            if (kind.matches(tags)) {
                return kind;
            }
        }
        return null;
    }

    private static GeoPoint positionOf(JSONObject element) {
        if (element.has("lat") && element.has("lon")) {
            return new GeoPoint(element.optDouble("lat"), element.optDouble("lon"));
        }
        JSONObject center = element.optJSONObject("center");
        if (center != null && center.has("lat") && center.has("lon")) {
            return new GeoPoint(center.optDouble("lat"), center.optDouble("lon"));
        }
        return null;
    }

    private static String nullableTag(JSONObject tags, String name) {
        if (!tags.has(name) || tags.isNull(name)) {
            return null;
        }
        return tags.optString(name, null);
    }

    private static void appendSearch(StringBuilder query, String elementType,
                                     String key, String value, String around) {
        query.append("  ").append(elementType)
                .append("[\"").append(key).append("\"=\"").append(value).append("\"]")
                .append(around).append(";\n");
    }

    private static String aroundFilter(List<GeoPoint> points, int corridorMeters) {
        StringBuilder filter = new StringBuilder("(around:").append(corridorMeters);
        for (GeoPoint point : points) {
            // A fixed precision makes tests and logs stable and is far more precise
            // than a corridor measured in hundreds of metres.
            filter.append(',').append(String.format(Locale.US, "%.6f", point.lat));
            filter.append(',').append(String.format(Locale.US, "%.6f", point.lng));
        }
        return filter.append(')').toString();
    }

    private static List<GeoPoint> evenlySpacedPoints(List<GeoPoint> points) {
        if (points.size() <= MAX_ROUTE_POINTS) {
            return new ArrayList<>(points);
        }

        double[] cumulative = GeoMath.cumulativeMeters(points);
        double total = cumulative[cumulative.length - 1];
        List<GeoPoint> result = new ArrayList<>(MAX_ROUTE_POINTS);
        if (total == 0.0) {
            result.add(points.get(0));
            return result;
        }

        int segment = 1;
        for (int sample = 0; sample < MAX_ROUTE_POINTS; sample++) {
            double wanted = total * sample / (MAX_ROUTE_POINTS - 1);
            while (segment < cumulative.length - 1 && cumulative[segment] < wanted) {
                segment++;
            }
            GeoPoint before = points.get(segment - 1);
            GeoPoint after = points.get(segment);
            double segmentLength = cumulative[segment] - cumulative[segment - 1];
            double fraction = segmentLength == 0.0 ? 0.0
                    : (wanted - cumulative[segment - 1]) / segmentLength;
            result.add(new GeoPoint(
                    before.lat + (after.lat - before.lat) * fraction,
                    before.lng + (after.lng - before.lng) * fraction));
        }
        return result;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }
}
