package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import io.github.jamerlybob.windroute.nav.RideDistanceFormatter;
import io.github.jamerlybob.windroute.nav.RideWind;
import io.github.jamerlybob.windroute.nav.TurnGuide;
import io.github.jamerlybob.windroute.route.CyclingWarnings;
import io.github.jamerlybob.windroute.nav.CameraFollow;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Owns ride presentation; permissions and Service binding stay in the Activity. */
final class RideScreenController {
    private final Activity activity;
    private Marker rider;
    private GeoPoint cameraPoint;
    private float cameraBearing;
    private boolean animating;
    private boolean visible;

    RideScreenController(Activity activity) { this.activity = activity; }

    void show(Route route, RideAnalysis analysis, Settings settings, RouteMapRenderer renderer) {
        if (renderer != null) renderer.setRideMode(true);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activity.findViewById(R.id.search_card).setVisibility(View.GONE);
        activity.findViewById(R.id.summary_card).setVisibility(View.GONE);
        activity.findViewById(R.id.ride_banner).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.ride_strip).setVisibility(View.VISIBLE);
        if (!visible) {
            // Before the first GPS fix the departure instruction needs no distance.
            int next = route == null ? -1 : new TurnGuide(route).nextIndex(Double.NaN);
            String instruction = next < 0 ? activity.getString(R.string.ride_default_instruction)
                    : route.steps.get(next).instruction;
            text(R.id.ride_instruction).setText(instruction);
        }
        visible = true;
        ElevationProfileView profile = activity.findViewById(R.id.ride_elevation);
        profile.setVisibility(analysis == null || analysis.elevation == null ? View.GONE : View.VISIBLE);
        if (analysis != null) profile.setData(analysis.elevation, analysis.wind,
                analysis.climbs, analysis.climbWinds, settings);
        TextView warnings = text(R.id.ride_warnings);
        warnings.setVisibility(route != null && route.source == Route.Source.GOOGLE ? View.VISIBLE : View.GONE);
        if (route != null && route.source == Route.Source.GOOGLE) warnings.setText(String.join("\n",
                CyclingWarnings.combine(activity.getString(R.string.cycling_notice), route.warnings)));
    }

    void hide(boolean hasRoute, RouteMapRenderer renderer) {
        if (renderer != null) renderer.setRideMode(false);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activity.findViewById(R.id.ride_banner).setVisibility(View.GONE);
        activity.findViewById(R.id.ride_strip).setVisibility(View.GONE);
        activity.findViewById(R.id.search_card).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.summary_card).setVisibility(hasRoute ? View.VISIBLE : View.GONE);
        if (rider != null) rider.remove();
        mapCleared();
        visible = false;
        ((ElevationProfileView) activity.findViewById(R.id.elevation_profile)).setProgressMeters(Double.NaN);
    }

    void mapCleared() { rider = null; cameraPoint = null; }

    void onRideUpdate(RideService.RideUpdate update, GoogleMap map, Settings settings) {
        if (activity.isFinishing() || map == null) return;
        String[] formats = activity.getResources().getStringArray(R.array.ride_distance_formats);
        text(R.id.ride_instruction).setText(Double.isNaN(update.instructionDistanceMeters)
                ? update.instruction : activity.getString(R.string.ride_next_instruction,
                RideDistanceFormatter.format(update.instructionDistanceMeters, settings.distanceUnit, formats),
                update.instruction));
        activity.findViewById(R.id.off_route_banner).setVisibility(update.offRoute ? View.VISIBLE : View.GONE);
        text(R.id.ride_stats).setText(activity.getString(R.string.ride_stats,
                RideDistanceFormatter.format(update.progress.distanceRemainingMeters, settings.distanceUnit, formats),
                durationText(update.remainingSeconds), windText(update.wind, settings)));
        View arrow = activity.findViewById(R.id.ride_wind_arrow);
        arrow.setVisibility(update.wind == null ? View.GONE : View.VISIBLE);
        if (update.wind != null) arrow.setRotation((float) update.wind.arrowDegrees);
        LatLng position = new LatLng(update.position.lat, update.position.lng);
        if (rider == null) rider = map.addMarker(new MarkerOptions().position(position)
                .title(activity.getString(R.string.use_my_location))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        else rider.setPosition(position);
        followCamera(map, update, position);
        ((ElevationProfileView) activity.findViewById(R.id.ride_elevation))
                .setProgressMeters(update.progress.distanceAlongMeters);
    }

    private void followCamera(GoogleMap map, RideService.RideUpdate update, LatLng position) {
        // GPS jitter at traffic lights must not repeatedly animate the map and
        // spend battery. Compare with the last camera target, not the last fix,
        // so small genuine movements accumulate. Never restart a running animation.
        if (!CameraFollow.shouldMove(cameraPoint, cameraBearing, update.position, update.bearing, animating)) return;
        cameraPoint = update.position;
        cameraBearing = update.bearing;
        animating = true;
        map.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(position).zoom(17).bearing(update.bearing).tilt(45).build()),
                new GoogleMap.CancelableCallback() {
                    @Override public void onFinish() { animating = false; }
                    @Override public void onCancel() { animating = false; }
                });
    }

    String durationText(long seconds) {
        long minutes = Math.max(0, Math.round(seconds / 60.0));
        return minutes >= 60 ? activity.getString(R.string.duration_h_min, minutes / 60, minutes % 60)
                : activity.getString(R.string.duration_min, minutes);
    }

    private String windText(RideWind wind, Settings settings) {
        if (wind == null) return activity.getString(R.string.ride_wind_unknown);
        int verdict = wind.effect == WindEffect.HEADWIND ? R.string.ride_headwind
                : wind.effect == WindEffect.TAILWIND ? R.string.ride_tailwind
                : wind.effect == WindEffect.CROSSWIND ? R.string.ride_crosswind : R.string.ride_calm;
        return activity.getString(R.string.ride_wind, activity.getString(verdict),
                new UnitText(activity, settings).windSpeed(wind.speedKmh));
    }

    private TextView text(int id) { return activity.findViewById(id); }
}
