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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private static final int READ_TIMEOUT_MS = 70_000;
    private static final int MAX_ROUTE_POINTS = 51;

    private OverpassClient() {
    }

    public interface Progress {
        void updated(int piece, int total, Result partial);
    }
    public static final class Result {
        public final List<Poi> places;
        public final int completed, total, busyFailures, networkFailures;
        Result(List<Poi> places, int completed, int total, int busyFailures, int networkFailures) {
            this.places=java.util.Collections.unmodifiableList(new ArrayList<>(places));
            this.completed=completed; this.total=total;
            this.busyFailures=busyFailures; this.networkFailures=networkFailures;
        }
        public boolean failed() { return busyFailures+networkFailures>0; }
    }
    public static final class BusyException extends IOException {
        public BusyException() { super("The places service is busy. Try again in a minute."); }
    }
    /** A transport boundary lets the sequential/partial-result behaviour be tested without HTTP. */
    public interface Request { List<Poi> run(String query) throws IOException; }
    public static Result lookup(List<GeoPoint> points, Collection<PoiKind> kinds,
                                int corridorMeters, Progress progress) throws IOException {
        return lookup(points,kinds,corridorMeters,progress,OverpassClient::request);
    }
    public static Result lookup(List<GeoPoint> points, Collection<PoiKind> kinds,
                                int corridorMeters, Progress progress, Request request) throws IOException {
        List<List<GeoPoint>> pieces=RoutePieces.split(points);
        List<Poi> found=new ArrayList<>(); int busy=0, network=0, completed=0;
        for (List<GeoPoint> piece : pieces) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            progress.updated(completed+1,pieces.size(),new Result(found,completed,pieces.size(),busy,network));
            try {
                merge(found,PoiAlongRoute.withinCorridor(points,request.run(buildQuery(piece,kinds,corridorMeters)),corridorMeters));
            } catch (BusyException error) { busy++; }
            catch (IOException error) { network++; }
            completed++;
            progress.updated(completed,pieces.size(),new Result(found,completed,pieces.size(),busy,network));
        }
        return new Result(found,completed,pieces.size(),busy,network);
    }
    /** Overlapping corridors return the same OSM object at adjacent seams. */
    private static void merge(List<Poi> target,List<Poi> additions) {
        for (Poi next : additions) {
            boolean duplicate=false;
            for (Poi prior : target) if (prior.kind==next.kind
                    && java.util.Objects.equals(prior.name,next.name)
                    && java.util.Objects.equals(prior.openingHours,next.openingHours)
                    && GeoMath.distanceMeters(prior.position,next.position)<.1) { duplicate=true; break; }
            if (!duplicate) target.add(next);
        }
    }
    public static List<Poi> fetch(List<GeoPoint> points, Collection<PoiKind> kinds,
                                  int corridorMeters) throws IOException {
        return lookup(points,kinds,corridorMeters,(piece,total,result)->{}).places;
    }

    /** One small-box request only after the rider taps Find camping near here. */
    public static List<Poi> fetchCampingBox(GeoPoint point) throws IOException {
        return request(buildCampingBoxQuery(point));
    }

    public static String buildCampingBoxQuery(GeoPoint point) {
        double latitudeRadius = 5000.0 / 111320;
        double longitudeRadius = latitudeRadius / Math.max(.01, Math.cos(Math.toRadians(point.lat)));
        return String.format(Locale.US,
                "[out:json][timeout:60];nwr[\"tourism\"~\"^(camp_site|caravan_site)$\"](%.5f,%.5f,%.5f,%.5f);out center tags;node._;out body;",
                Math.max(-90, point.lat-latitudeRadius), Math.max(-180, point.lng-longitudeRadius),
                Math.min(90, point.lat+latitudeRadius), Math.min(180, point.lng+longitudeRadius));
    }

    private static synchronized List<Poi> request(String query) throws IOException {
        // A cancelled Activity may have been waiting behind another request.
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
        String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name());

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("User-Agent", "WindRoute/0.1 (personal cycling route planner)");
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (isBusyStatus(status)) throw new BusyException();
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
     * Builds one compact query for one route piece.
     *
     * <p>Long routes are replaced by at most 51 points at equal distances. That
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

        List<GeoPoint> samples = evenlySpacedPoints(points);
        int safeCorridor = corridorMeters + (int) Math.ceil(maxDistanceToSample(points, samples));
        String around = aroundFilter(samples, safeCorridor);
        StringBuilder query = new StringBuilder("[out:json][timeout:60];\n(\n");
        Map<String,Set<String>> tags=new LinkedHashMap<>();
        for (PoiKind kind : PoiKind.values()) if (kinds.contains(kind)) {
            for (int tag=0; tag<kind.tagCount(); tag++)
                tags.computeIfAbsent(kind.tagKey(tag), key->new LinkedHashSet<>()).add(kind.tagValue(tag));
        }
        for (Map.Entry<String,Set<String>> tag : tags.entrySet()) {
            query.append("  nwr[\"").append(tag.getKey()).append("\"~\"^(")
                    .append(String.join("|",tag.getValue())).append(")$\"]").append(around).append(";\n");
        }
        // `out tags` omits node coordinates; `center` restores only way/relation
        // centres. A node-only body output retains their positions without
        // repeating the expensive search or downloading full way geometry.
        query.append(");\nout center tags;node._;out body;");
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
            JSONObject root = new JSONObject(json);
            // Overpass may return HTTP 200 with a runtime-error remark and an
            // empty elements array. That is a failed query, not a dry town.
            if (!root.optString("remark").isEmpty()) {
                throw new BusyException();
            }
            JSONArray elements = root.getJSONArray("elements");
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

    public static boolean isBusyStatus(int status) { return status==429 || status==504; }

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

    /**
     * Measures how far thinning moved the query away from the real route.
     * Adding this to the requested corridor makes every original point remain
     * covered even when a sparse straight chord cuts across a winding road.
     */
    static double maxDistanceToSample(List<GeoPoint> points, List<GeoPoint> samples) {
        double largest = 0;
        for (GeoPoint point : points) {
            double nearest = Double.MAX_VALUE;
            for (GeoPoint sample : samples) {
                nearest = Math.min(nearest, GeoMath.distanceMeters(point, sample));
            }
            largest = Math.max(largest, nearest);
        }
        return largest;
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
