package io.github.jamerlybob.windroute;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.trip.TripStore;
import java.util.*;

/** An independent route snapshot: editing the last route cannot change an alert. */
final class AlertsStore {
    final SharedPreferences prefs;
    AlertsStore(Context context) { prefs = context.getSharedPreferences("ride_alerts", Context.MODE_PRIVATE); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    int hour() { return prefs.getInt("hour", 7); }
    int minute() { return prefs.getInt("minute", 0); }
    boolean bad() { return prefs.getBoolean("bad", false); }
    String name() { return prefs.getString("name", ""); }
    String routeJson() { return prefs.getString("route", ""); }
    Route route() {
        try { return RouteJson.read(routeJson()); } catch (Exception ignored) { return null; }
    }
    void choose(String name, Route route) {
        prefs.edit().putString("name", name).putString("route", RouteJson.write(route)).apply();
    }
    // The notification must never point to a half-written forecast after process death.
    // This synchronous disk write runs on the job worker before posting the notification.
    @android.annotation.SuppressLint("ApplySharedPref")
    void preview(Route route, long departure, RouteForecastLoader.Result weather) throws org.json.JSONException {
        String cache = TripStore.encodeForecastCache(Collections.singletonList(weather), Collections.emptyList());
        prefs.edit().putString("preview_route", RouteJson.write(route)).putLong("preview_departure", departure)
                .putString("preview_name", name()).putString("preview_weather", cache).commit();
    }
}
