package io.github.jamerlybob.windroute;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteJson;
import io.github.jamerlybob.windroute.settings.SettingsStore;

/**
 * Persists the last drawn route and the small recent-place list. This storage
 * concern is separate from the Activity so corrupt old data can be handled in
 * one place and startup remains safe.
 */
public final class RouteStore {
    private static final String LAST_ORIGIN = "last_origin";
    private static final String LAST_DESTINATION = "last_destination";
    private static final String LAST_ROUTE = "last_route";
    private static final String LAST_ELEVATIONS = "last_elevations";
    private static final String RECENT_PLACES = "recent_places";
    private static final int MAX_RECENT = 8;

    private final SharedPreferences preferences;

    public RouteStore(Context context) {
        // SharedPreferences suits this tiny local record and writes it without
        // introducing a database or any network-backed account.
        preferences = context.getSharedPreferences(SettingsStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE);
    }

    public void saveRoute(String origin, String destination, Route route) {
        preferences.edit()
                .putString(LAST_ORIGIN, origin)
                .putString(LAST_DESTINATION, destination)
                .putString(LAST_ROUTE, RouteJson.write(route))
                .remove(LAST_ELEVATIONS)
                .apply();
    }

    /** Saves heights separately after the route has already been shown. */
    public void saveElevations(double[] elevations) {
        JSONArray values = new JSONArray();
        for (double elevation : elevations) {
            // The Object overload accepts a finite boxed number without the
            // checked exception used by Android's primitive double overload.
            values.put(Double.valueOf(elevation));
        }
        preferences.edit().putString(LAST_ELEVATIONS, values.toString()).apply();
    }

    /** Returns null for missing, old or damaged data; startup must never fail. */
    public SavedRoute loadRoute() {
        String origin = preferences.getString(LAST_ORIGIN, null);
        String destination = preferences.getString(LAST_DESTINATION, null);
        String route = preferences.getString(LAST_ROUTE, null);
        if (origin == null || destination == null || route == null) {
            return null;
        }
        try {
            return new SavedRoute(origin, destination, RouteJson.read(route),
                    readElevations(preferences.getString(LAST_ELEVATIONS, null)));
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    private static double[] readElevations(String json) throws JSONException {
        if (json == null) {
            return null;
        }
        JSONArray values = new JSONArray(json);
        double[] elevations = new double[values.length()];
        for (int i = 0; i < elevations.length; i++) {
            elevations[i] = values.getDouble(i);
        }
        return elevations;
    }

    public List<String> recentPlaces() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(preferences.getString(RECENT_PLACES, "[]"));
            for (int i = 0; i < stored.length() && result.size() < MAX_RECENT; i++) {
                String place = stored.optString(i, "").trim();
                if (!place.isEmpty()) {
                    result.add(place);
                }
            }
        } catch (JSONException | RuntimeException ignored) {
            // A damaged preference is equivalent to having no recent places.
        }
        return result;
    }

    public void addRecentPlace(String place) {
        String clean = place == null ? "" : place.trim();
        if (clean.isEmpty()) {
            return;
        }
        List<String> places = recentPlaces();
        for (int i = places.size() - 1; i >= 0; i--) {
            if (places.get(i).equalsIgnoreCase(clean)) {
                places.remove(i);
            }
        }
        places.add(0, clean);
        JSONArray stored = new JSONArray();
        for (int i = 0; i < places.size() && i < MAX_RECENT; i++) {
            stored.put(places.get(i));
        }
        preferences.edit().putString(RECENT_PLACES, stored.toString()).apply();
    }

    /** The route and its display labels must be restored as one snapshot. */
    public static final class SavedRoute {
        public final String origin;
        public final String destination;
        public final Route route;
        public final double[] elevations;

        SavedRoute(String origin, String destination, Route route, double[] elevations) {
            this.origin = origin;
            this.destination = destination;
            this.route = route;
            this.elevations = elevations;
        }
    }
}
