package io.github.jamerlybob.windroute;

import android.Manifest;
import android.content.SharedPreferences;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.nav.TurnGuide;
import io.github.jamerlybob.windroute.nav.CuePlanFactory;
import io.github.jamerlybob.windroute.nav.CuePlanner;
import io.github.jamerlybob.windroute.nav.CueBuilder;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.Build;
import android.view.WindowManager;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
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
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jamerlybob.windroute.elevation.ElevationClient;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.gpx.GpxParser;
import io.github.jamerlybob.windroute.gpx.GpxWriter;
import io.github.jamerlybob.windroute.gpx.RouteThinner;
import io.github.jamerlybob.windroute.poi.OverpassClient;
import io.github.jamerlybob.windroute.poi.Poi;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import io.github.jamerlybob.windroute.poi.PoiKind;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RouteWaypoint;
import io.github.jamerlybob.windroute.route.RoutesClient;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.settings.SettingsStore;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.trip.TripStore;

/** Wires the route form, retained data, background loaders and screen controllers. */
public final class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        CurrentLocationController.Listener, RideService.Listener {
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
    private RouteState state;
    private RouteStore routeStore;
    private Settings settings;
    private CurrentLocationController currentLocation;
    private PlaceSuggestionsController suggestions;
    private SearchCardController searchController;
    private DeparturePickerController departurePicker;
    private RideSheetController sheetController;
    private int searchGeneration;
    private boolean busy;
    private RideService rideService;
    private boolean rideBound;
    private boolean rideBinding;
    private RideScreenController rideScreen;
    private RideService.RideUpdate lastRideUpdate;
    private final ActivityResultLauncher<String[]> importGpx = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importGpx);
    private final ActivityResultLauncher<String> exportGpx = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/gpx+xml"), this::exportGpx);
    private final ActivityResultLauncher<String[]> preciseLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                if (Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_FINE_LOCATION))) beginRideService();
                else showMessage(getString(R.string.precise_location_explanation));
            });
    private final ActivityResultLauncher<String> notifications = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), ignored -> { });
    private final ServiceConnection rideConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            rideService = ((RideService.RideBinder) binder).service();
            rideBound = true;
            rideBinding = false;
            if (rideService.isRunning()) {
                state.route = rideService.route();
                showRideMode();
                rideService.setListener(MainActivity.this);
            } else leaveRideMode();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            rideService = null;
            leaveRideMode();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        state = new ViewModelProvider(this).get(RouteState.class);
        routeStore = new RouteStore(this);
        state.exportWaypoints = routeStore.exportWaypoints();
        settings = SettingsStore.load(this);
        bindViews();
        searchController = new SearchCardController(this);
        sheetController = new RideSheetController(this);
        rideScreen = new RideScreenController(this);
        departurePicker = new DeparturePickerController(this, this::departureChanged);
        keepCardsClearOfSystemBars();
        wireControls();
        wireBack();

        currentLocation = new CurrentLocationController(this, this);
        suggestions = new PlaceSuggestionsController(this, originInput, destinationInput,
                routeStore, mainThread, this::visibleMapBounds,
                getString(R.string.my_location), () -> state.originCoordinates = null,
                () -> state.destinationCoordinates = null);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        restoreLastRouteIfNeeded();
        long tripDeparture = getIntent().getLongExtra("trip_departure", 0);
        if (tripDeparture > 0) state.departureEpochSeconds = tripDeparture;
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) {
            importGpx(getIntent().getData());
        } else if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            Uri shared = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (shared != null) importGpx(shared);
        }
    }

    private void bindViews() {
        originInput = findViewById(R.id.origin);
        destinationInput = findViewById(R.id.destination);
        goButton = findViewById(R.id.go);
        progress = findViewById(R.id.progress);
        searchCard = findViewById(R.id.search_card);
        summaryCard = findViewById(R.id.summary_card);
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
        TextInputLayout originLayout = findViewById(R.id.origin_layout);
        originLayout.setEndIconOnClickListener(v -> currentLocation.requestAfterTap());
        TextInputLayout destinationLayout = findViewById(R.id.destination_layout);
        destinationLayout.setEndIconOnClickListener(v -> swapPlaces());
        findViewById(R.id.settings).setOnClickListener(this::showMenu);
        findViewById(R.id.settings_collapsed).setOnClickListener(this::showMenu);
        findViewById(R.id.find_places).setOnClickListener(v -> findPlaces());
        findViewById(R.id.start_ride).setOnClickListener(v -> startRide());
        findViewById(R.id.stop_ride).setOnClickListener(v -> stopRide());
        findViewById(R.id.reroute).setOnClickListener(v -> rerouteFromCurrentPosition());
    }

    private void startRide() {
        if (state.route == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences choices = getSharedPreferences(
                    SettingsStore.PREFERENCES_NAME, MODE_PRIVATE);
            if (choices.getBoolean("ride_precise_requested", false)) {
                new AlertDialog.Builder(this).setMessage(R.string.precise_location_explanation)
                        .setPositiveButton(R.string.location_open_settings, (dialog, which) ->
                                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton(android.R.string.cancel, null).show();
                return;
            }
            new AlertDialog.Builder(this).setMessage(R.string.precise_location_explanation)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        choices.edit().putBoolean("ride_precise_requested", true).apply();
                        preciseLocation.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION});
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        beginRideService();
    }

    private void beginRideService() {
        // Start ride means riding now, even if the planning screen was previewing
        // tomorrow. Reuse cached forecasts; starting guidance never calls Routes.
        state.departureEpochSeconds = System.currentTimeMillis() / 1000;
        RideAnalysis analysis = currentAnalysis();
        CueBuilder words =
                new CueBuilder(
                        getResources().getStringArray(R.array.cue_words), settings.distanceUnit);
        List<CuePlanner.Event> events;
        if (analysis != null) {
            events = CuePlanFactory.build(analysis, settings, words);
        } else {
            // A weather failure must not silence real Google turn instructions.
            events = settings.cueTurns ? CuePlanner.turnEvents(
                    state.route.steps, new TurnGuide(state.route).starts,
                    words, settings.cueAheadMeters) : Collections.emptyList();
        }
        RideService.prepare(state.route, analysis, events, state.sampleIndexes, state.forecasts);
        Intent intent = new Intent(this, RideService.class);
        ContextCompat.startForegroundService(this, intent);
        if (!rideBinding && !rideBound) rideBinding = bindService(intent, rideConnection, 0);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setMessage(R.string.notification_explanation)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void showRideMode() {
        rideScreen.show(state.route, rideService == null ? null : rideService.analysis(), settings, renderer);
    }

    private void stopRide() {
        if (rideService != null) rideService.stopRide(false);
        else stopService(new Intent(this, RideService.class));
        leaveRideMode();
    }

    private void leaveRideMode() {
        rideScreen.hide(state.route != null, renderer);
        detachRide();
    }

    private void detachRide() {
        if (rideBound || rideBinding) {
            if (rideService != null) rideService.setListener(null);
            unbindService(rideConnection);
        }
        rideService = null;
        rideBound = false;
        rideBinding = false;
    }

    @Override public void onRideUpdate(RideService.RideUpdate update) {
        lastRideUpdate = update;
        showRideMode();
        rideScreen.onRideUpdate(update, map, settings);
    }

    private void rerouteFromCurrentPosition() {
        if (lastRideUpdate == null) return;
        // Re-routing spends one of the small daily Google Routes quota. It is
        // intentionally tied only to this explicit tap and never runs on GPS drift.
        stopRide();
        originInput.setText(R.string.my_location);
        state.originCoordinates = lastRideUpdate.position;
        state.destinationCoordinates = state.route.points.get(state.route.points.size() - 1);
        search();
    }

    @Override public void onRideStopped(boolean arrived) {
        if (arrived && lastRideUpdate != null) {
            double average = rideService == null ? Double.NaN : rideService.averageHeadwindKmh();
            String experiencedWind = Double.isNaN(average) ? getString(R.string.ride_wind_unknown)
                    : getString(average >= 0 ? R.string.net_headwind : R.string.net_tailwind,
                    new UnitText(this, settings)
                            .windSpeed(Math.abs(average)));
            showMessage(getString(R.string.ride_summary,
                    new UnitText(this, settings)
                            .distance(lastRideUpdate.riddenMeters),
                    rideScreen.durationText(lastRideUpdate.elapsedSeconds), experiencedWind));
        }
        leaveRideMode();
    }

    private void wireBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (sheetController.collapseForBack() || searchController.collapseForBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
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

    @Override protected void onStart() {
        super.onStart();
        // Binding without AUTO_CREATE only reconnects an existing ride. Opening
        // the app must never silently start GPS tracking or a foreground service.
        if (!rideBound && !rideBinding) {
            rideBinding = bindService(new Intent(this, RideService.class), rideConnection, 0);
            // The notification's Stop action can finish a ride while this
            // Activity is off-screen. Do not redisplay its stale riding controls.
            if (!rideBinding) leaveRideMode();
        }
    }

    @Override protected void onStop() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        detachRide();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (suggestions != null) {
            suggestions.destroy();
        }
        if (rideBound) {
            rideService.setListener(null);
            unbindService(rideConnection);
            rideBound = false;
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
        if (rideService != null && rideService.isRunning()) {
            showRideMode();
            if (lastRideUpdate != null) onRideUpdate(lastRideUpdate);
        }
    }

    private void restoreLastRouteIfNeeded() {
        if (state.route != null) {
            if (state.forecasts == null) {
                refreshForecast(state.route);
            } else if (state.elevation == null && !state.elevationLoading
                    && !state.elevationFailed) {
                fetchElevation(state.route, searchGeneration);
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
        state.departureEpochSeconds = System.currentTimeMillis() / 1000;
        if (saved.elevations != null) {
            List<GeoPoint> samples = saved.route.source == Route.Source.GPX
                    ? saved.route.points : ElevationClient.sampleRoute(saved.route.points);
            if (samples.size() == saved.elevations.length) {
                state.elevation = new ElevationProfile(samples, saved.elevations);
            }
        }
        refreshForecast(saved.route);
        if (state.elevation == null) {
            fetchElevation(saved.route, searchGeneration);
        }
    }

    /** Refreshes free weather only; it can never spend a Routes request. */
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

    /** The only method that calls Google Routes, reached by an explicit tap. */
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
                ? RouteWaypoint.address(origin) : RouteWaypoint.coordinates(state.originCoordinates);
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
                mainThread.post(() -> acceptNewRoute(generation, origin, destination,
                        myLocation, route, weather));
            } catch (Exception e) {
                postFailure(generation, e);
            }
        });
    }

    private void acceptNewRoute(int generation, String origin, String destination,
                                String myLocation, Route route,
                                RouteForecastLoader.Result weather) {
        if (generation != searchGeneration || isDestroyed()) {
            return;
        }
        state.route = route;
        state.sampleIndexes = weather.sampleIndexes;
        state.forecasts = weather.forecasts;
        state.elevation = null;
        state.elevationFailed = false;
        state.exportWaypoints.clear();
        routeStore.saveExportWaypoints(state.exportWaypoints);
        state.departureEpochSeconds = System.currentTimeMillis() / 1000;
        routeStore.saveRoute(origin, destination, route);
        if (!origin.equals(myLocation)) routeStore.addRecentPlace(origin);
        if (!destination.equals(myLocation)) routeStore.addRecentPlace(destination);
        setBusy(false);
        showWind();
        fetchElevation(route, generation);
    }

    private void fetchElevation(Route route, int routeGeneration) {
        if (state.elevationLoading) {
            return;
        }
        state.elevationLoading = true;
        state.elevationFailed = false;
        showWind();
        background.execute(() -> {
            try {
                List<GeoPoint> samples = ElevationClient.sampleRoute(route.points);
                double[] heights = ElevationClient.fetch(samples);
                ElevationProfile profile = new ElevationProfile(samples, heights);
                mainThread.post(() -> {
                    if (route != state.route || isDestroyed()) {
                        return;
                    }
                    state.elevation = profile;
                    state.elevationLoading = false;
                    routeStore.saveElevations(heights);
                    showWind();
                });
            } catch (Exception error) {
                mainThread.post(() -> {
                    if (route != state.route || isDestroyed()) {
                        return;
                    }
                    state.elevationLoading = false;
                    state.elevationFailed = true;
                    showWind();
                });
            }
        });
    }

    private void departureChanged(long epochSeconds) {
        state.departureEpochSeconds = Math.max(epochSeconds, System.currentTimeMillis() / 1000);
        if (state.route == null || state.forecasts == null) {
            return;
        }
        long duration = UnitFormatter.ridingDurationSeconds(state.route.distanceMeters,
                state.route.durationSeconds, settings.ridingSpeedKmh);
        if (!RideAnalysis.forecastCovers(state.forecasts,
                state.departureEpochSeconds, duration)) {
            refreshForecast(state.route);
        } else {
            showWind();
        }
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
        if (busy) return;
        String origin = originInput.getText().toString();
        String destination = destinationInput.getText().toString();
        GeoPoint originPoint = state.originCoordinates;
        GeoPoint destinationPoint = state.destinationCoordinates;
        originInput.setText(destination);
        destinationInput.setText(origin);
        state.originCoordinates = destinationPoint;
        state.destinationCoordinates = originPoint;
        if (state.route != null && state.forecasts != null) {
            // Reversing a known line is a comparison, not permission to spend
            // another Google Routes request. Free weather is resampled because
            // its indexes must follow the new point order.
            state.route = state.route.reversed();
            state.sampleIndexes = null;
            state.forecasts = null;
            state.elevation = null;
            state.elevationFailed = false;
            routeStore.saveRoute(destination, origin, state.route);
            refreshForecast(state.route);
            fetchElevation(state.route, searchGeneration);
        }
    }

    /** Recomputes presentation from retained data; no network call occurs here. */
    private void showWind() {
        if (renderer != null && rideService != null && rideService.isRunning()) {
            renderer.setRideMode(true);
            RideAnalysis ride = rideService.analysis();
            if (ride != null) {
                renderer.show(ride, settings, sheetController.shortWindCost(
                        ride.power.differenceMinutes), 0);
                rideScreen.mapCleared(); // the renderer clears old map overlays
            }
            showRideMode();
            if (lastRideUpdate != null) onRideUpdate(lastRideUpdate);
            return;
        }
        if (renderer == null || state.route == null || state.sampleIndexes == null
                || state.forecasts == null || state.forecasts.isEmpty()) {
            return;
        }
        if (state.departureEpochSeconds == 0) {
            state.departureEpochSeconds = System.currentTimeMillis() / 1000;
        }
        RideAnalysis analysis = RideAnalysis.build(state.route, state.sampleIndexes,
                state.forecasts, state.departureEpochSeconds, settings, state.elevation,
                System.currentTimeMillis() / 1000);
        sheetController.show(analysis, settings, departurePicker::select,
                state.elevationLoading, state.elevationFailed);
        renderer.show(analysis, settings,
                sheetController.shortWindCost(analysis.power.differenceMinutes),
                sheetController.collapsedHeight());
        renderer.showPlaces(state.places);
        searchController.showRoute(originInput.getText().toString(),
                destinationInput.getText().toString(), analysis.departureEpochSeconds);
    }

    @Override
    public void onLocation(GeoPoint point) {
        originInput.setText(R.string.my_location);
        originInput.setSelection(originInput.length());
        state.originCoordinates = point;
    }

    @Override public void onLocationPermissionAvailable() { enableMyLocationIfAllowed(); }
    @Override public void onLocationMessage(String message) { showMessage(message); }

    @SuppressLint("MissingPermission")
    private void enableMyLocationIfAllowed() {
        if (map == null) return;
        boolean coarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (coarse || fine) {
            try {
                map.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
                // Permission can be revoked between the check and SDK call.
            }
        }
    }

    private PlaceSuggestionsController.SearchBounds visibleMapBounds() {
        if (map == null) return null;
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        return new PlaceSuggestionsController.SearchBounds(bounds.southwest.latitude,
                bounds.southwest.longitude, bounds.northeast.latitude,
                bounds.northeast.longitude);
    }

    private void keepCardsClearOfSystemBars() {
        int margin = Math.round(12 * getResources().getDisplayMetrics().density);
        int collapsedSide = Math.round(16 * getResources().getDisplayMetrics().density);
        int collapsedTop = Math.round(8 * getResources().getDisplayMetrics().density);
        int collapsedBottom = Math.round(12 * getResources().getDisplayMetrics().density);
        int contentBottom = Math.round(32 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
            Insets mandatoryGestures = insets.getInsets(
                    WindowInsetsCompat.Type.mandatorySystemGestures());
            int safeBottom = Math.max(bars.bottom,
                    Math.max(gestures.bottom, mandatoryGestures.bottom));
            setMargins(searchCard, margin + bars.left, margin + bars.top,
                    margin + bars.right, 0);
            setMargins(summaryCard, bars.left, 0, bars.right, 0);
            setMargins(findViewById(R.id.ride_banner), margin + bars.left, margin + bars.top,
                    margin + bars.right, 0);
            setMargins(findViewById(R.id.ride_strip), margin + bars.left, 0,
                    margin + bars.right, margin + safeBottom);
            // BottomSheetBehavior positions the sheet itself and can disregard
            // its bottom margin. Padding the actual content is what reliably
            // keeps both resting and scrolled text above gesture navigation.
            findViewById(R.id.summary_collapsed).setPadding(collapsedSide, collapsedTop,
                    collapsedSide, collapsedBottom + safeBottom);
            findViewById(R.id.summary_content).setPadding(collapsedSide, 0,
                    collapsedSide, contentBottom + safeBottom);
            // A non-fit-to-contents sheet expands to y=0 unless its offset is
            // explicit, regardless of the margin used for its resting layout.
            sheetController.setExpandedOffset(bars.top);
            return insets;
        });
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
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

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.menu_import_gpx);
        menu.getMenu().add(0, 2, 1, R.string.menu_export_gpx);
        menu.getMenu().add(0, 3, 2, R.string.menu_plan_trip).setEnabled(state.route != null);
        menu.getMenu().add(0, 4, 3, R.string.menu_saved_trips);
        menu.getMenu().add(0, 5, 4, R.string.menu_settings);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                importGpx.launch(new String[]{"application/gpx+xml", "application/octet-stream",
                        "text/xml", "application/xml"});
            } else if (item.getItemId() == 2) {
                if (state.route == null) showMessage(getString(R.string.no_route_to_export));
                else exportGpx.launch(getString(R.string.gpx_export_name));
            } else if (item.getItemId() == 3) {
                openTrip(null);
            } else if (item.getItemId() == 4) {
                showSavedTrips();
            } else if (item.getItemId() == 5) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });
        menu.show();
    }

    private void importGpx(Uri uri) {
        if (uri == null) return;
        setBusy(true);
        int generation = ++searchGeneration;
        background.execute(() -> {
            try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("Could not open this GPX file.");
                GpxParser.Result parsed = GpxParser.parse(input);
                RouteThinner.Result thin = RouteThinner.thin(parsed.points, parsed.elevations, 2000);
                double[] cumulative = GeoMath.cumulativeMeters(thin.points);
                double distance = cumulative[cumulative.length - 1];
                int speed = settings.ridingSpeedKmh > 0 ? settings.ridingSpeedKmh : 18;
                long duration = Math.round(distance / 1000.0 / speed * 3600.0);
                Route route = new Route(thin.points, distance, duration, Collections.emptyList(),
                        Route.Source.GPX);
                ElevationProfile elevation = elevationFromGpx(thin);
                String importName = parsed.name.isEmpty() ? fileName(uri) : parsed.name;
                mainThread.post(() -> acceptImportedRoute(generation, route, elevation,
                        parsed.waypoints, importName, settings.ridingSpeedKmh <= 0
                                && routeStore.shouldExplainDefaultGpxSpeed()));
            } catch (Exception error) {
                postFailure(generation, error);
            }
        });
    }

    private static ElevationProfile elevationFromGpx(RouteThinner.Result result) {
        double[] heights = new double[result.elevations.size()];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = result.elevations.get(i);
            if (!Double.isFinite(heights[i])) return null;
        }
        return new ElevationProfile(result.points, heights);
    }

    private void acceptImportedRoute(int generation, Route route, ElevationProfile elevation,
                                     List<GpxParser.Waypoint> waypoints, String importName,
                                     boolean usedDefaultSpeed) {
        if (generation != searchGeneration || isDestroyed()) return;
        String displayName = importName == null || importName.trim().isEmpty()
                ? getString(R.string.gpx_imported) : importName.trim();
        originInput.setText(displayName);
        destinationInput.setText(R.string.gpx_finish);
        state.route = route;
        state.elevation = elevation;
        state.elevationFailed = elevation == null;
        state.exportWaypoints = new ArrayList<>(waypoints);
        routeStore.saveExportWaypoints(state.exportWaypoints);
        state.departureEpochSeconds = System.currentTimeMillis() / 1000;
        routeStore.saveRoute(displayName, getString(R.string.gpx_finish), route);
        if (elevation != null) routeStore.saveElevations(elevation.rawElevationMeters);
        if (usedDefaultSpeed) showMessage(getString(R.string.gpx_default_speed));
        refreshForecast(route);
        if (elevation == null) fetchElevation(route, generation);
    }

    private String fileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".gpx")) {
                    name = name.substring(0, name.length() - 4);
                }
                return name;
            }
        } catch (RuntimeException ignored) {
            // Some document providers do not expose metadata; the generic GPX
            // label is still a safe fallback.
        }
        return getString(R.string.gpx_imported);
    }

    private void exportGpx(Uri uri) {
        if (uri == null || state.route == null) return;
        background.execute(() -> {
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new java.io.IOException("Could not create the GPX file.");
                List<Double> elevations = new ArrayList<>();
                if (state.elevation != null
                        && state.elevation.rawElevationMeters.length == state.route.points.size()) {
                    for (double value : state.elevation.rawElevationMeters) elevations.add(value);
                }
                List<GpxParser.Waypoint> waypoints = new ArrayList<>(state.exportWaypoints);
                RideAnalysis analysis = currentAnalysis();
                if (analysis != null) {
                    for (int i = 0; i < analysis.climbs.size(); i++) {
                        int point = nearestPoint(analysis.route,
                                analysis.climbs.get(i).startDistanceMeters);
                        waypoints.add(new GpxParser.Waypoint(analysis.route.points.get(point),
                                getString(R.string.export_climb, i + 1), null));
                    }
                }
                String xml = GpxWriter.write("WindRoute", state.route.points, elevations, waypoints);
                output.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                mainThread.post(() -> showMessage(getString(R.string.gpx_exported)));
            } catch (Exception error) {
                mainThread.post(() -> showMessage(error.getMessage() == null
                        ? error.toString() : error.getMessage()));
            }
        });
    }

    private static int nearestPoint(Route route, double distance) {
        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        int best = 0;
        for (int i = 1; i < cumulative.length; i++) {
            if (Math.abs(cumulative[i] - distance) < Math.abs(cumulative[best] - distance)) best = i;
        }
        return best;
    }

    private RideAnalysis currentAnalysis() {
        if (state.route == null || state.forecasts == null || state.sampleIndexes == null) return null;
        return RideAnalysis.build(state.route, state.sampleIndexes, state.forecasts,
                state.departureEpochSeconds, settings, state.elevation,
                System.currentTimeMillis() / 1000);
    }

    private void openTrip(String id) {
        try {
            if (id == null) {
                double[] distances = state.elevation == null
                        ? null : state.elevation.distanceMeters;
                double[] elevations = state.elevation == null
                        ? null : state.elevation.rawElevationMeters;
                new TripStore(this).saveDraft(state.route, distances, elevations);
            }
            Intent intent = new Intent(this, TripActivity.class);
            if (id != null) intent.putExtra("trip_id", id);
            startActivity(intent);
        } catch (Exception error) {
            showMessage(error.getMessage());
        }
    }

    private void showSavedTrips() {
        TripStore store = new TripStore(this);
        List<TripStore.SavedTrip> trips = store.list();
        if (trips.isEmpty()) {
            showMessage(getString(R.string.no_saved_trips));
            return;
        }
        String[] names = new String[trips.size()];
        for (int i = 0; i < names.length; i++) names[i] = trips.get(i).name;
        int[] selected = {0};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.menu_saved_trips)
                .setSingleChoiceItems(names, 0, (d, which) -> selected[0] = which)
                .setPositiveButton(android.R.string.ok,
                        (d, which) -> openTrip(trips.get(selected[0]).id))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.delete_trip, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    store.delete(trips.get(selected[0]).id);
                    dialog.dismiss();
                    showSavedTrips();
                }));
        dialog.show();
    }

    private void findPlaces() {
        if (state.route == null || busy) return;
        if (!findViewById(R.id.find_places).isEnabled()) return;
        final Route lookupRoute = state.route;
        findViewById(R.id.find_places).setEnabled(false);
        findViewById(R.id.places_progress).setVisibility(View.VISIBLE);
        android.widget.TextView status = findViewById(R.id.places_status);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.places_busy);
        background.execute(() -> {
            try {
                List<Poi> found = OverpassClient.fetch(lookupRoute.points,
                        Arrays.asList(PoiKind.WATER, PoiKind.FOOD, PoiKind.CAMPING), 500);
                List<PoiAlongRoute> located = PoiAlongRoute.locate(lookupRoute.points, found);
                mainThread.post(() -> {
                    if (isDestroyed()) return;
                    findViewById(R.id.find_places).setEnabled(true);
                    findViewById(R.id.places_progress).setVisibility(View.GONE);
                    if (lookupRoute != state.route) { status.setVisibility(View.GONE); return; }
                    status.setText(located.isEmpty() ? getString(R.string.places_none, 500)
                            : getString(R.string.places_results, located.size()));
                    showPlaces(located);
                });
            } catch (Exception error) {
                mainThread.post(() -> {
                    if (isDestroyed()) return;
                    findViewById(R.id.find_places).setEnabled(true);
                    findViewById(R.id.places_progress).setVisibility(View.GONE);
                    status.setText(R.string.places_error);
                });
            }
        });
    }

    private void showPlaces(List<PoiAlongRoute> places) {
        state.places = places;
        android.widget.LinearLayout list = findViewById(R.id.places_list);
        list.removeAllViews();
        findViewById(R.id.places_credit).setVisibility(View.VISIBLE);
        if (places.isEmpty()) showMessage(getString(R.string.places_none, 500));
        PlacesListController.append(this, list, places, settings, (located, name, kind) ->
                new AlertDialog.Builder(this).setTitle(name)
                    .setMessage(kind + (located.poi.openingHours == null ? ""
                            : "\n" + located.poi.openingHours))
                    .setPositiveButton(R.string.add_to_export, (dialog, which) -> {
                        state.exportWaypoints.add(new GpxParser.Waypoint(located.poi.position,
                                name, null));
                        routeStore.saveExportWaypoints(state.exportWaypoints);
                        showMessage(getString(R.string.added_to_export));
                    }).setNegativeButton(android.R.string.cancel, null).show());
        if (renderer != null) renderer.showPlaces(places);
    }

    private void hideKeyboard() {
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(destinationInput.getWindowToken(), 0);
        destinationInput.clearFocus();
        originInput.clearFocus();
    }

}
