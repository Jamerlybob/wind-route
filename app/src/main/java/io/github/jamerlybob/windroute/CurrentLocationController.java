package io.github.jamerlybob.windroute;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import java.util.Map;

import io.github.jamerlybob.windroute.route.GeoPoint;

/**
 * Requests permission only after a tap, then obtains one platform location.
 * Permission contracts and provider selection live here so MainActivity does
 * not mix Android location lifecycle details with route-search decisions.
 */
public final class CurrentLocationController {
    /** Reports outcomes without making this controller depend on screen views. */
    public interface Listener {
        void onLocation(GeoPoint point);
        void onLocationPermissionAvailable();
        void onLocationMessage(String message);
    }

    private final AppCompatActivity activity;
    private final Listener listener;
    private final ActivityResultLauncher<String[]> permissionRequest;

    public CurrentLocationController(AppCompatActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        // ActivityResultLauncher ties the permission dialog to the Activity's
        // lifecycle, including recreation, instead of relying on request codes.
        permissionRequest = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::permissionResult);
    }

    public void requestAfterTap() {
        if (hasPermission()) {
            listener.onLocationPermissionAvailable();
            findCurrentLocation();
            return;
        }
        permissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        });
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void permissionResult(Map<String, Boolean> result) {
        boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
        boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
        if (coarse || fine) {
            listener.onLocationPermissionAvailable();
            findCurrentLocation();
        } else {
            // This is shown once in response to the tap. The app never asks at
            // launch or repeatedly prompts after a refusal.
            listener.onLocationMessage(activity.getString(R.string.location_permission_denied));
        }
    }

    @SuppressLint("MissingPermission")
    private void findCurrentLocation() {
        // The lint suppression is narrow because every path here follows a
        // successful runtime permission check in requestAfterTap().
        LocationManager manager = (LocationManager) activity.getSystemService(
                Context.LOCATION_SERVICE);
        String provider = chooseProvider(manager);
        if (provider == null) {
            listener.onLocationMessage(activity.getString(R.string.location_unavailable));
            return;
        }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider,
                    (android.os.CancellationSignal) null,
                    ContextCompat.getMainExecutor(activity), location -> {
                        if (location == null) {
                            listener.onLocationMessage(
                                    activity.getString(R.string.location_unavailable));
                        } else {
                            listener.onLocation(new GeoPoint(
                                    location.getLatitude(), location.getLongitude()));
                        }
                    });
        } catch (IllegalArgumentException | SecurityException e) {
            listener.onLocationMessage(activity.getString(R.string.location_unavailable));
        }
    }

    private String chooseProvider(LocationManager manager) {
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        boolean fine = ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        return null;
    }
}
