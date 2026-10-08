package io.github.jamerlybob.windroute;

import androidx.lifecycle.ViewModel;

import java.util.List;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.gpx.GpxParser;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import java.util.ArrayList;

/**
 * The last search result, kept somewhere that outlives the Activity.
 *
 * <p>When the phone switches between light and dark, Android throws the
 * Activity away and builds a new one so every view picks up the new colours.
 * Fields on the Activity are lost when that happens. A ViewModel is not: the
 * new Activity is handed the same object, so the route is still there and can
 * be drawn again without asking Google or the weather service a second time.
 *
 * <p>It does not survive the app being killed. That is a separate roadmap item.
 */
public class RouteState extends ViewModel {
    Route route;
    /** Which points of the route the forecasts below belong to. */
    List<Integer> sampleIndexes;
    List<WindForecast> forecasts;
    ElevationProfile elevation;
    boolean elevationLoading;
    boolean elevationFailed;
    long departureEpochSeconds;
    /** Coordinates stay attached to the special "My location" text through rotation. */
    GeoPoint originCoordinates;
    GeoPoint destinationCoordinates;
    List<GpxParser.Waypoint> exportWaypoints = new ArrayList<>();
    List<PoiAlongRoute> places = new ArrayList<>();
}
