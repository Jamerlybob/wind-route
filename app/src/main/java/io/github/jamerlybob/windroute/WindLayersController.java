package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.*;
import io.github.jamerlybob.windroute.settings.SettingsStore;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.weather.WindGridClient;
import io.github.jamerlybob.windroute.wind.*;

/** Owns the optional free weather layers and their camera/lifecycle budget. */
public final class WindLayersController {
    private final Activity activity;
    private final GoogleMap map;
    private final WindFlowView flow;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final TextView today;
    private final View legend;
    private LatLng rider;
    public void setRider(LatLng rider) { this.rider=rider; redraw(); }
    private WindField field;
    private GroundOverlay heat;
    private volatile boolean moving, speed, active, riding, cameraMoving, loading, destroyed, hasRoute;
    private long time = System.currentTimeMillis() / 1000, fetchedHour = -1;
    private Runnable cameraIdle = () -> { };
    public void setCameraIdle(Runnable callback) { cameraIdle = callback; }
    private final Runnable request = this::load;
    public WindLayersController(Activity activity, GoogleMap map) {
        this.activity = activity; this.map = map;
        flow = activity.findViewById(R.id.wind_flow);
        today = activity.findViewById(R.id.wind_today);
        legend = activity.findViewById(R.id.wind_legend_card);
        preferences = activity.getSharedPreferences(SettingsStore.PREFERENCES_NAME, 0);
        moving = preferences.getBoolean("moving_wind", true);
        speed = preferences.getBoolean("speed_wind", false);
        activity.findViewById(R.id.layers).setOnClickListener(v -> showChoices());
        View search=activity.findViewById(R.id.search_card);
        search.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)-> {
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params=
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)activity.findViewById(R.id.layers).getLayoutParams();
            params.topMargin = bottom + dp(12 + 72 + 12);
            View location = activity.findViewById(R.id.map_my_location);
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams locationParams =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)location.getLayoutParams();
            locationParams.topMargin = bottom + dp(12);
            location.setLayoutParams(locationParams);
            activity.findViewById(R.id.layers).setLayoutParams(params);
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams legendParams =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)legend.getLayoutParams();
            legendParams.topMargin = bottom + Math.round(12 * activity.getResources().getDisplayMetrics().density);
            legend.setLayoutParams(legendParams);
            if(!hasRoute) map.setPadding(0,bottom,0,0);
        });
        map.setOnCameraMoveStartedListener(reason -> { cameraMoving = true; updateAnimation(); });
        map.setOnCameraIdleListener(() -> { cameraMoving = false; schedule(); updateAnimation(); cameraIdle.run(); });
        schedule();
    }
    public void setRoute(boolean route, long departure) {
        hasRoute = route; if (departure > 0) time = departure;
        activity.findViewById(R.id.wind_today_card).setVisibility(route ? View.GONE : View.VISIBLE); redraw(); schedule();
    }
    public void setRiding(boolean riding) { this.riding = riding;
        if (riding) {
            main.removeCallbacks(request);
            if (heat != null) { heat.remove(); heat = null; }
        } else {
            redraw();
            schedule();
        }
        activity.findViewById(R.id.layers).setVisibility(riding ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.map_my_location).setVisibility(riding ? View.GONE : View.VISIBLE);
        if (riding) { activity.findViewById(R.id.wind_today_card).setVisibility(View.GONE); legend.setVisibility(View.GONE); }
        else activity.findViewById(R.id.wind_today_card).setVisibility(hasRoute ? View.GONE : View.VISIBLE);
        updateAnimation(); }
    public void setActive(boolean active) { this.active = active; updateAnimation(); if (active) schedule(); }
    private void updateAnimation() {
        LatLngBounds visible = map.getProjection().getVisibleRegion().latLngBounds;
        boolean covered = field != null
                && field.covers(visible.southwest.latitude, visible.southwest.longitude)
                && field.covers(visible.northeast.latitude, visible.northeast.longitude);
        flow.setRunning(active && !riding && !cameraMoving && moving && covered);
    }
    private void schedule() { main.removeCallbacks(request); if (!destroyed && active) main.postDelayed(request, 1000); }
    private void load() {
        if (destroyed || loading || !active || riding || cameraMoving) return;
        if (!moving && !speed && hasRoute) return;
        LatLngBounds visible = map.getProjection().getVisibleRegion().latLngBounds;
        // Bounds crossing the date line are fetched as a narrow unwrapped interval.
        double west = visible.southwest.longitude, east = visible.northeast.longitude;
        if (east < west) east += 360;
        if (field != null && fetchedHour == time / 3600
                && field.covers(visible.southwest.latitude, west)
                && field.covers(visible.northeast.latitude, east)) { redraw(); return; }
        double latPad = Math.max(.02, (visible.northeast.latitude - visible.southwest.latitude) * .2);
        double lngPad = Math.max(.02, (east - west) * .2);
        double south = Math.max(-85, visible.southwest.latitude - latPad);
        double north = Math.min(85, visible.northeast.latitude + latPad);
        double left = west - lngPad;
        double right = east + lngPad;
        if (right - left >= 360) {
            left = -179.99;
            right = 179.99;
        }
        if (right <= left) return;
        if (north <= south) return;
        loading = true;
        final long requestedHour = time / 3600;
        // A settled-camera debounce, coverage cache and single in-flight request
        // protect the free tier (600/minute, 10,000/day). No Google HTTP calls.
        final double requestWest = left;
        final double requestEast = right;
        worker.execute(() -> {
            try {
                if (riding || destroyed || !active) {
                    main.post(() -> loading = false);
                    return;
                }
                WindField loaded = WindGridClient.fetch(south, requestWest, north, requestEast);
                main.post(() -> { loading = false; if (destroyed) return;
                    field = loaded; fetchedHour = requestedHour; redraw();
                    schedule();
                });
            } catch (Exception error) {
                main.post(() -> { loading = false;
                    if (!hasRoute) today.setText(R.string.wind_unavailable); });
            }
        });
    }
    /** Called after a route redraw clears Maps overlays. */
    public void redraw() {
        if (heat != null) { heat.remove(); heat = null; }
        flow.setWind(map, field, time); updateAnimation();
        legend.setVisibility(speed && field != null && !riding ? View.VISIBLE : View.GONE);
        UnitText units = new UnitText(activity, SettingsStore.load(activity));
        android.graphics.drawable.GradientDrawable ramp = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{WindPalette.color(0), WindPalette.color(12), WindPalette.color(24),
                        WindPalette.color(36), WindPalette.color(48), WindPalette.color(60)});
        ramp.setCornerRadius(dp(4));
        activity.findViewById(R.id.wind_legend_ramp).setBackground(ramp);
        ((TextView) activity.findViewById(R.id.wind_legend_low)).setText(units.windSpeed(0));
        ((TextView) activity.findViewById(R.id.wind_legend_mid)).setText(units.windSpeed(30));
        ((TextView) activity.findViewById(R.id.wind_legend_high)).setText(units.windSpeed(60));
        flow.setCalmThreshold(SettingsStore.load(activity).calmBelowKmh);
        if (field == null) return;
        if (speed && !riding) {
            Bitmap image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
                image.setPixel(x, y, WindPalette.color(field.at(field.north - (field.north-field.south)*y/63,
                        field.west + (field.east-field.west)*x/63, time).speed()));
            heat = map.addGroundOverlay(new GroundOverlayOptions().image(BitmapDescriptorFactory.fromBitmap(image))
                    .positionFromBounds(new LatLngBounds(new LatLng(field.south, field.west),
                            new LatLng(field.north, field.east))).transparency(.45f).zIndex(-1));
            image.recycle();
        }
        if (!hasRoute) {
            LatLng position = rider == null ? map.getCameraPosition().target : rider;
            if (!field.covers(position.latitude,position.longitude)) return;
            WindVector now = field.at(position.latitude, position.longitude, time);
            long laterTime = WindOutlook.nextWholeHour(time + 3 * 3600 - 1);
            WindVector later = field.at(position.latitude, position.longitude, laterTime);
            String[] directions = activity.getResources().getStringArray(R.array.wind_compass);
            int from = WindOutlook.compass(now.fromDegrees());
            int home = WindOutlook.compass(now.fromDegrees() + 180);
            String trend = WindOutlook.steady(now.speed(), later.speed())
                    ? activity.getString(R.string.wind_steady, hourLabel(laterTime))
                    : activity.getString(R.string.wind_outlook_trend,
                            activity.getString(WindOutlook.easing(now.speed(), later.speed())
                                    ? R.string.wind_easing : R.string.wind_later),
                            units.windSpeed(later.speed()), hourLabel(laterTime));
            today.setText(activity.getString(R.string.wind_today_headline,
                    directions[from], units.windSpeed(now.speed()), trend));
            ((TextView) activity.findViewById(R.id.wind_today_advice)).setText(
                    activity.getString(R.string.wind_today_advice, directions[home]));
            ((WindBadgeView) activity.findViewById(R.id.wind_today_badge)).setWind(
                    WindPalette.color(now.speed()), WindPresentation.relativeArrow(now.fromDegrees(), 0),
                    units.windSpeed(now.speed()));
        }
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private String hourLabel(long epochSeconds) {
        String pattern = android.text.format.DateFormat.is24HourFormat(activity) ? "HH:mm" : "h a";
        return new java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(new Date(epochSeconds * 1000));
    }

    private void showChoices() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(20), dp(24), dp(24));
        TextView title = new TextView(activity);
        title.setText(R.string.map_layers);
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        body.addView(title);
        MaterialSwitch particles = new MaterialSwitch(activity);
        particles.setText(R.string.moving_wind);
        particles.setChecked(moving);
        body.addView(particles);
        addDescription(body, R.string.moving_wind_description);
        MaterialSwitch heatmap = new MaterialSwitch(activity);
        heatmap.setText(R.string.speed_wind);
        heatmap.setChecked(speed);
        body.addView(heatmap);
        addDescription(body, R.string.speed_wind_description);
        particles.setOnCheckedChangeListener((button, checked) -> {
            moving = checked; save(); redraw(); schedule();
        });
        heatmap.setOnCheckedChangeListener((button, checked) -> {
            speed = checked; save(); redraw(); schedule();
        });
        LinearLayout hours = new LinearLayout(activity);
        long now = System.currentTimeMillis() / 1000;
        long next = WindOutlook.nextWholeHour(time);
        long[] choices = {now, next, next + 2 * 3600};
        for (int i = 0; i < choices.length; i++) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(activity);
            final long selected = choices[i];
            chip.setText(i == 0 ? activity.getString(R.string.depart_now) : hourLabel(selected));
            chip.setOnClickListener(v -> { time = selected; redraw(); schedule(); dialog.dismiss(); });
            hours.addView(chip);
        }
        body.addView(hours);
        dialog.setContentView(body);
        dialog.show();
    }

    private void addDescription(LinearLayout body, int text) {
        TextView description = new TextView(activity);
        description.setText(text);
        description.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        description.setPadding(0, 0, 0, dp(12));
        body.addView(description);
    }
    private void save() { preferences.edit().putBoolean("moving_wind",moving).putBoolean("speed_wind",speed).apply(); }
    public void destroy() { destroyed=true; main.removeCallbacksAndMessages(null); flow.setRunning(false); worker.shutdownNow(); }
}
