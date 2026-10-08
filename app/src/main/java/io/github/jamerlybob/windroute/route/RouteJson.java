package io.github.jamerlybob.windroute.route;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a Route to the small JSON record kept on the device. Serialization
 * is isolated here so Route stays a simple value and storage code does not
 * need to know the shape of every point and warning.
 */
public final class RouteJson {
    private RouteJson() {
    }

    public static String write(Route route) {
        try {
            JSONArray points = new JSONArray();
            for (GeoPoint point : route.points) {
                points.put(new JSONArray().put(point.lat).put(point.lng));
            }
            JSONArray warnings = new JSONArray();
            for (String warning : route.warnings) {
                warnings.put(warning);
            }
            return new JSONObject()
                    .put("points", points)
                    .put("distanceMeters", route.distanceMeters)
                    .put("durationSeconds", route.durationSeconds)
                    .put("source", route.source.name())
                    .put("warnings", warnings)
                    .toString();
        } catch (JSONException e) {
            // All keys and values above are known and finite, so this indicates
            // a programming mistake rather than bad stored user data.
            throw new IllegalStateException(e);
        }
    }

    public static Route read(String json) throws JSONException {
        // Reading is deliberately strict: a partial polyline is not useful and
        // must not crash startup later when the map tries to draw it.
        JSONObject root = new JSONObject(json);
        JSONArray rawPoints = root.getJSONArray("points");
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < rawPoints.length(); i++) {
            JSONArray point = rawPoints.getJSONArray(i);
            points.add(new GeoPoint(point.getDouble(0), point.getDouble(1)));
        }
        if (points.size() < 2) {
            throw new JSONException("A saved route needs at least two points.");
        }

        List<String> warnings = new ArrayList<>();
        JSONArray rawWarnings = root.optJSONArray("warnings");
        for (int i = 0; rawWarnings != null && i < rawWarnings.length(); i++) {
            warnings.add(rawWarnings.getString(i));
        }
        double distance = root.getDouble("distanceMeters");
        long duration = root.getLong("durationSeconds");
        if (!Double.isFinite(distance) || distance < 0 || duration < 0) {
            throw new JSONException("A saved route has invalid measurements.");
        }
        Route.Source source;
        try {
            source = Route.Source.valueOf(root.optString("source", Route.Source.GOOGLE.name()));
        } catch (IllegalArgumentException ignored) {
            source = Route.Source.GOOGLE;
        }
        return new Route(points, distance, duration, warnings, source);
    }
}
