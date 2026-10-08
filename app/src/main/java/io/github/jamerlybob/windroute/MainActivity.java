package io.github.jamerlybob.windroute;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteWaypoint;
import io.github.jamerlybob.windroute.route.RoutesClient;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.settings.SettingsStore;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.wind.RouteWind;

/**
 * The main screen wires user input, background work and route drawing together.
 * Feature details live in small collaborators so this Activity stays readable.
 */
public final class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        CurrentLocationController.Listener {
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private GoogleMap map;
    private RouteMapRenderer renderer;
    private MaterialAutoCompleteTextView originInput;
    private MaterialAutoCompleteTextView destinationInput;
    private View goButton;
    private View progress;
    private View searchCard;
    private View summaryCard;
    private ChipGroup departChips;
    private RouteState state;
    private RouteStore routeStore;
    private Settings settings;
    private CurrentLocationController currentLocation;
    private PlaceSuggestionsController suggestions;
    private int searchGeneration;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        state = new ViewModelProvider(this).get(RouteState.class);
        routeStore = new RouteStore(this);
        settings = SettingsStore.load(this);
        bindViews();
        keepCardsClearOfSystemBars();
        wireControls();

        currentLocation = new CurrentLocationController(this, this);
        suggestions = new PlaceSuggestionsController(this, originInput, destinationInput,
                routeStore, background, mainThread, this::visibleMapBounds,
                getString(R.string.my_location), () -> state.originCoordinates = null,
                () -> state.destinationCoordinates = null);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        restoreLastRouteIfNeeded();
    }

    private void bindViews() {
        originInput = findViewById(R.id.origin);
        destinationInput = findViewById(R.id.destination);
        goButton = findViewById(R.id.go);
        progress = findViewById(R.id.progress);
        searchCard = findViewById(R.id.search_card);
        summaryCard = findViewById(R.id.summary_card);
        departChips = findViewById(R.id.depart_chips);
    }

    private void wireControls() {
        goButton.setOnClickListener(v -> search());
        destinationInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        departChips.setOnCheckedStateChangeListener((group, checkedIds) -> showWind());

        TextInputLayout originLayout = findViewById(R.id.origin_layout);
        originLayout.setEndIconOnClickListener(v -> currentLocation.requestAfterTap());
        TextInputLayout destinationLayout = findViewById(R.id.destination_layout);
        destinationLayout.setEndIconOnClickListener(v -> swapPlaces());
        findViewById(R.id.settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Settings latest = SettingsStore.load(this);
        if (settings != null && !latest.equals(settings)) {
            settings = latest;
            showWind();
        } else {
            settings = latest;
        }
    }

    @Override
    protected void onDestroy() {
        if (suggestions != null) {
            suggestions.destroy();
        }
        background.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMapToolbarEnabled(false);
        if (isNight()) {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_night));
        }
        renderer = new RouteMapRenderer(this, map, findViewById(R.id.main),
                searchCard, summaryCard);
        enableMyLocationIfAllowed();
        showWind();
    }

    private void restoreLastRouteIfNeeded() {
        if (state.route != null) {
            if (state.forecasts == null) {
                refreshForecast(state.route);
            }
            return;
        }
        RouteStore.SavedRoute saved = routeStore.loadRoute();
        if (saved == null) {
            return;
        }
        originInput.setText(saved.origin);
        destinationInput.setText(saved.destination);
        state.route = saved.route;
        refreshForecast(saved.route);
    }

    /** A restore refreshes only free weather; it must never spend a Routes call. */
    private void refreshForecast(Route route) {
        setBusy(true);
        int generation = ++searchGeneration;
        background.execute(() -> {
            try {
                RouteForecastLoader.Result weather = RouteForecastLoader.load(route);
                mainThread.post(() -> {
                    if (generation != searchGeneration || isDestroyed()) {
                        return;
                    }
                    state.sampleIndexes = weather.sampleIndexes;
                    state.forecasts = weather.forecasts;
                    setBusy(false);
                    showWind();
                });
            } catch (Exception e) {
                postFailure(generation, e);
            }
        });
    }

    /** The only method that calls Google Routes, reached by an explicit user action. */
    private void search() {
        if (busy) {
            return;
        }
        String origin = originInput.getText().toString().trim();
        String destination = destinationInput.getText().toString().trim();
        if (origin.isEmpty() || destination.isEmpty()) {
            showMessage(getString(R.string.error_need_both));
            return;
        }
        if (BuildConfig.MAPS_API_KEY.isEmpty()) {
            showMessage(getString(R.string.error_no_key));
            return;
        }
        String myLocation = getString(R.string.my_location);
        if ((origin.equals(myLocation) && state.originCoordinates == null)
                || (destination.equals(myLocation) && state.destinationCoordinates == null)) {
            showMessage(getString(R.string.location_needs_refresh));
            return;
        }

        RouteWaypoint originWaypoint = state.originCoordinates == null
                ? RouteWaypoint.address(origin)
                : RouteWaypoint.coordinates(state.originCoordinates);
        RouteWaypoint destinationWaypoint = state.destinationCoordinates == null
                ? RouteWaypoint.address(destination)
                : RouteWaypoint.coordinates(state.destinationCoordinates);
        hideKeyboard();
        setBusy(true);
        int generation = ++searchGeneration;

        background.execute(() -> {
            try {
                Route route = new RoutesClient(BuildConfig.MAPS_API_KEY)
                        .fetch(originWaypoint, destinationWaypoint);
                RouteForecastLoader.Result weather = RouteForecastLoader.load(route);
                mainThread.post(() -> {
                    if (generation != searchGeneration || isDestroyed()) {
                        return;
                    }
                    state.route = route;
                    state.sampleIndexes = weather.sampleIndexes;
                    state.forecasts = weather.forecasts;
                    routeStore.saveRoute(origin, destination, route);
                    if (!origin.equals(myLocation)) {
                        routeStore.addRecentPlace(origin);
                    }
                    if (!destination.equals(myLocation)) {
                        routeStore.addRecentPlace(destination);
                    }
                    setBusy(false);
                    showWind();
                });
            } catch (Exception e) {
                postFailure(generation, e);
            }
        });
    }

    private void postFailure(int generation, Exception error) {
        String message = error.getMessage() != null ? error.getMessage() : error.toString();
        mainThread.post(() -> {
            if (generation != searchGeneration || isDestroyed()) {
                return;
            }
            setBusy(false);
            showMessage(message);
        });
    }

    private void swapPlaces() {
        if (busy) {
            return;
        }
        String origin = originInput.getText().toString();
        String destination = destinationInput.getText().toString();
        GeoPoint originPoint = state.originCoordinates;
        GeoPoint destinationPoint = state.destinationCoordinates;
        originInput.setText(destination);
        destinationInput.setText(origin);
        state.originCoordinates = destinationPoint;
        state.destinationCoordinates = originPoint;
        if (state.route != null && state.forecasts != null) {
            search();
        }
    }

    /** Recomputes presentation from retained data; no network call occurs here. */
    private void showWind() {
        if (renderer == null || state.route == null || state.sampleIndexes == null
                || state.forecasts == null || state.forecasts.isEmpty()) {
            return;
        }
        long duration = UnitFormatter.ridingDurationSeconds(state.route.distanceMeters,
                state.route.durationSeconds, settings.ridingSpeedKmh);
        long departure = departureEpochSeconds();
        RouteWind outward = RouteWind.analyze(state.route, state.sampleIndexes,
                state.forecasts, departure, duration, settings.calmBelowKmh);
        RouteWind reverse = RouteWind.analyzeReversed(state.route, state.sampleIndexes,
                state.forecasts, departure, duration, settings.calmBelowKmh);
        renderer.show(state.route, outward, reverse, settings, duration);
    }

    private long departureEpochSeconds() {
        long now = System.currentTimeMillis() / 1000;
        int checked = departChips.getCheckedChipId();
        if (checked == R.id.depart_1h) {
            return now + 3600;
        }
        if (checked == R.id.depart_3h) {
            return now + 3 * 3600;
        }
        return now;
    }

    @Override
    public void onLocation(GeoPoint point) {
        originInput.setText(R.string.my_location);
        originInput.setSelection(originInput.length());
        state.originCoordinates = point;
    }

    @Override
    public void onLocationPermissionAvailable() {
        enableMyLocationIfAllowed();
    }

    @Override
    public void onLocationMessage(String message) {
        showMessage(message);
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocationIfAllowed() {
        if (map == null) {
            return;
        }
        boolean coarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (coarse || fine) {
            try {
                map.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
                // Permission can be revoked between the check and the SDK call.
            }
        }
    }

    private PlaceSuggestionsController.SearchBounds visibleMapBounds() {
        if (map == null) {
            return null;
        }
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        return new PlaceSuggestionsController.SearchBounds(bounds.southwest.latitude,
                bounds.southwest.longitude, bounds.northeast.latitude,
                bounds.northeast.longitude);
    }

    private void keepCardsClearOfSystemBars() {
        int margin = Math.round(12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            setMargins(searchCard, margin + bars.left, margin + bars.top,
                    margin + bars.right, 0);
            setMargins(summaryCard, margin + bars.left, 0, margin + bars.right,
                    margin + bars.bottom);
            return insets;
        });
    }

    private boolean isNight() {
        int night = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void setMargins(View view, int left, int top, int right, int bottom) {
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) view.getLayoutParams();
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        goButton.setEnabled(!busy);
        findViewById(R.id.origin_layout).setEnabled(!busy);
        findViewById(R.id.destination_layout).setEnabled(!busy);
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG).show();
    }

    private void hideKeyboard() {
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(destinationInput.getWindowToken(), 0);
        destinationInput.clearFocus();
        originInput.clearFocus();
    }
}
