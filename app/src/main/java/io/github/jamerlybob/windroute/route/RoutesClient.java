package io.github.jamerlybob.windroute.route;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.jamerlybob.windroute.Http;

/**
 * Asks Google's Routes API for a cycling route between two places.
 *
 * <p>This is the Routes API (computeRoutes), not the older Directions API, which
 * Google now labels "Legacy". Two things differ from the old one and both show
 * up below: the request is a JSON POST rather than a URL with query parameters,
 * and a "field mask" header is mandatory.
 */
public final class RoutesClient {

    private static final String URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

    /**
     * The fields we want back. Google bills by what you ask for and returns
     * nothing at all without this header, so it lists exactly what the app uses.
     */
    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration,"
            + "routes.polyline.encodedPolyline,routes.warnings";

    private final String apiKey;

    public RoutesClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public Route fetch(String origin, String destination) throws IOException {
        return fetch(RouteWaypoint.address(origin), RouteWaypoint.address(destination));
    }

    public Route fetch(RouteWaypoint origin, RouteWaypoint destination) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Goog-Api-Key", apiKey);
        headers.put("X-Goog-FieldMask", FIELD_MASK);
        return parse(Http.postJson(URL, headers, requestBody(origin, destination)));
    }

    public static String requestBody(String origin, String destination) {
        return requestBody(RouteWaypoint.address(origin), RouteWaypoint.address(destination));
    }

    public static String requestBody(RouteWaypoint origin, RouteWaypoint destination) {
        try {
            return new JSONObject()
                    .put("origin", waypointJson(origin))
                    .put("destination", waypointJson(destination))
                    .put("travelMode", "BICYCLE")
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);   // only thrown for a null key
        }
    }

    private static JSONObject waypointJson(RouteWaypoint waypoint) throws JSONException {
        if (waypoint.coordinates != null) {
            JSONObject latLng = new JSONObject()
                    .put("latitude", waypoint.coordinates.lat)
                    .put("longitude", waypoint.coordinates.lng);
            return new JSONObject().put("location",
                    new JSONObject().put("latLng", latLng));
        }
        return new JSONObject().put("address", waypoint.address);
    }

    /** Turns the response body into a Route, or throws with a message fit to show. */
    public static Route parse(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("error")) {
                throw new IOException(root.getJSONObject("error").optString(
                        "message", "The routing service returned an error."));
            }
            JSONArray routes = root.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                // An empty object is how the API says "no route".
                throw new IOException("No cycling route found between those two places.");
            }
            JSONObject first = routes.getJSONObject(0);
            List<GeoPoint> points = PolylineDecoder.decode(
                    first.getJSONObject("polyline").getString("encodedPolyline"));
            if (points.size() < 2) {
                throw new IOException("The route came back with no path.");
            }

            List<String> warnings = new ArrayList<>();
            JSONArray rawWarnings = first.optJSONArray("warnings");
            for (int i = 0; rawWarnings != null && i < rawWarnings.length(); i++) {
                warnings.add(rawWarnings.getString(i));
            }
            return new Route(points, first.optDouble("distanceMeters", 0),
                    parseSeconds(first.optString("duration", "0s")), warnings);
        } catch (JSONException e) {
            throw new IOException("Could not read the routing service's reply.", e);
        }
    }

    /** Durations arrive as text such as "1234s". */
    public static long parseSeconds(String duration) {
        String digits = duration.endsWith("s")
                ? duration.substring(0, duration.length() - 1) : duration;
        try {
            return Math.round(Double.parseDouble(digits));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
