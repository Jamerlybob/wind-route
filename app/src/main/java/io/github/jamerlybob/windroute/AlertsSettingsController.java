package io.github.jamerlybob.windroute;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.trip.TripStore;

/** Permission, route choice and scheduling are kept out of SettingsActivity. */
final class AlertsSettingsController {
    private final AppCompatActivity activity;
    private final AlertsStore store;
    private final CompoundButton enabled;
    private final Button routeButton, timeButton;
    private final ActivityResultLauncher<String> permission;
    private boolean updating;
    AlertsSettingsController(AppCompatActivity activity) {
        this.activity = activity; store = new AlertsStore(activity);
        enabled = activity.findViewById(R.id.alerts_enabled);
        routeButton = activity.findViewById(R.id.alerts_route);
        timeButton = activity.findViewById(R.id.alerts_time);
        permission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            setEnabled(granted);
            if (!granted) Toast.makeText(activity, R.string.alert_permission_denied, Toast.LENGTH_LONG).show();
        });
        enabled.setChecked(store.enabled());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            if (!checked) setEnabled(false);
            else if (store.route() == null) { setEnabled(false); chooseRoute(true); }
            else requestEnable();
        });
        CompoundButton bad = activity.findViewById(R.id.alerts_bad);
        bad.setChecked(store.bad());
        bad.setOnCheckedChangeListener((button, checked) -> store.prefs.edit().putBoolean("bad", checked).apply());
        routeButton.setOnClickListener(v -> chooseRoute(false));
        timeButton.setOnClickListener(v -> new TimePickerDialog(activity, (view, hour, minute) -> {
            store.prefs.edit().putInt("hour", hour).putInt("minute", minute).apply(); labels();
        }, store.hour(), store.minute(), android.text.format.DateFormat.is24HourFormat(activity)).show());
        labels(); AlertsScheduler.update(activity);
    }
    private void setEnabled(boolean value) {
        store.prefs.edit().putBoolean("enabled", value).apply();
        updating = true; enabled.setChecked(value); updating = false;
        AlertsScheduler.update(activity);
    }
    private void requestEnable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            setEnabled(false);
            new MaterialAlertDialogBuilder(activity).setMessage(R.string.alert_permission_explanation)
                    .setPositiveButton(R.string.alert_permission_continue, (dialog, which) -> permission.launch(Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton(android.R.string.cancel, null).show();
        } else setEnabled(true);
    }
    private void labels() {
        routeButton.setText(activity.getString(R.string.alert_route_choice, store.name().isEmpty()
                ? activity.getString(R.string.alert_choose_route) : store.name()));
        Calendar clock = Calendar.getInstance(); clock.set(Calendar.HOUR_OF_DAY, store.hour()); clock.set(Calendar.MINUTE, store.minute());
        timeButton.setText(activity.getString(R.string.alert_time_choice,
                android.text.format.DateFormat.getTimeFormat(activity).format(clock.getTime())));
    }
    private void chooseRoute(boolean enableAfter) {
        List<String> names = new ArrayList<>(); List<Route> routes = new ArrayList<>();
        RouteStore.SavedRoute current = new RouteStore(activity).loadRoute();
        if (current != null) {
            names.add(activity.getString(R.string.alert_current_route, current.origin, current.destination)); routes.add(current.route);
        }
        for (TripStore.SavedTrip trip : new TripStore(activity).list()) { names.add(trip.name); routes.add(trip.route); }
        if (routes.isEmpty()) { Toast.makeText(activity, R.string.alert_no_routes, Toast.LENGTH_LONG).show(); return; }
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.alert_choose_route)
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    store.choose(names.get(which), routes.get(which)); labels();
                    if (enableAfter) requestEnable();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
