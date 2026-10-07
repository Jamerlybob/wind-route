package io.github.jamerlybob.windroute;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.route.RoutesClient;
import io.github.jamerlybob.windroute.weather.OpenMeteoClient;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/**
 * The one screen: a map, a card to say where you are going, and a card that
 * says what the wind will do about it.
 *
 * <p>All the thinking happens in the route, weather and wind packages. This
 * class only collects input, runs the network calls off the main thread, and
 * draws what comes back.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    /** Look up the forecast about this often along the route. */
    private static final double SAMPLE_SPACING_METERS = 5_000;
    /** Never ask the weather service for more places than this in one request. */
    private static final int MAX_SAMPLES = 60;

    private static final float ROUTE_WIDTH_PX = 16f;
    private static final float CASING_WIDTH_PX = 24f;

    // Network calls must not run on the main thread, and views may only be
    // touched from it. So work goes to this background thread, and results come
    // back through a Handler tied to the main thread.
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private GoogleMap map;
    private TextView originInput;
    private TextView destinationInput;
    private View goButton;
    private View progress;
    private View searchCard;
    private View summaryCard;
    private ChipGroup departChips;

    // Kept after a search so that changing the departure time can redraw
    // without asking Google or the weather service again.
    private Route route;
    private List<Integer> sampleIndexes;
    private List<WindForecast> forecasts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        originInput = findViewById(R.id.origin);
        destinationInput = findViewById(R.id.destination);
        goButton = findViewById(R.id.go);
        progress = findViewById(R.id.progress);
        searchCard = findViewById(R.id.search_card);
        summaryCard = findViewById(R.id.summary_card);
        departChips = findViewById(R.id.depart_chips);

        keepCardsClearOfSystemBars();

        goButton.setOnClickListener(v -> search());
        destinationInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        departChips.setOnCheckedStateChangeListener((group, checkedIds) -> showWind());

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        background.shutdownNow();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMapToolbarEnabled(false);
        showWind();   // in case a route arrived before the map did
    }

    /**
     * The app draws behind the status and navigation bars. This pushes the two
     * cards in by the size of those bars so nothing sits under the clock.
     */
    private void keepCardsClearOfSystemBars() {
        int margin = Math.round(12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            setMargins(searchCard, margin + bars.left, margin + bars.top, margin + bars.right, 0);
            setMargins(summaryCard, margin + bars.left, 0, margin + bars.right,
                    margin + bars.bottom);
            return insets;
        });
    }

    private static void setMargins(View view, int left, int top, int right, int bottom) {
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) view.getLayoutParams();
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }

    // ---- searching ---------------------------------------------------------------

    private void search() {
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
        hideKeyboard();
        setBusy(true);

        background.execute(() -> {
            try {
                Route found = new RoutesClient(BuildConfig.MAPS_API_KEY).fetch(origin, destination);

                // Spread the forecast lookups along the route, widening the gap
                // on a long ride so the request stays a sensible size.
                double spacing = Math.max(SAMPLE_SPACING_METERS,
                        found.distanceMeters / MAX_SAMPLES);
                List<Integer> indexes = GeoMath.sampleIndexes(found.points, spacing);
                List<GeoPoint> places = new ArrayList<>();
                for (int index : indexes) {
                    places.add(found.points.get(index));
                }
                List<WindForecast> wind = OpenMeteoClient.fetch(places);

                mainThread.post(() -> {
                    route = found;
                    sampleIndexes = indexes;
                    forecasts = wind;
                    setBusy(false);
                    showWind();
                });
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                mainThread.post(() -> {
                    setBusy(false);
                    showMessage(message);
                });
            }
        });
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

    // ---- drawing -----------------------------------------------------------------

    /** Colours the route on the map and fills in the summary card. */
    private void showWind() {
        if (map == null || route == null) {
            return;
        }
        RouteWind wind = RouteWind.analyze(route, sampleIndexes, forecasts,
                departureEpochSeconds());

        map.clear();
        List<LatLng> all = new ArrayList<>();
        for (GeoPoint point : route.points) {
            all.add(new LatLng(point.lat, point.lng));
        }

        // A white line under the coloured one, slightly wider, so the route
        // reads clearly whether it crosses a park, water or a motorway.
        map.addPolyline(new PolylineOptions().addAll(all)
                .color(ContextCompat.getColor(this, R.color.route_casing))
                .width(CASING_WIDTH_PX).jointType(JointType.ROUND)
                .startCap(new RoundCap()).endCap(new RoundCap()));

        // Neighbouring stretches with the same verdict are drawn as one line.
        // Each line starts on the last point of the one before, so they join.
        int runStart = 0;
        for (int i = 1; i <= wind.stretches.size(); i++) {
            boolean runEnds = i == wind.stretches.size()
                    || wind.stretches.get(i).effect != wind.stretches.get(runStart).effect;
            if (!runEnds) {
                continue;
            }
            RouteWind.Stretch first = wind.stretches.get(runStart);
            RouteWind.Stretch last = wind.stretches.get(i - 1);
            map.addPolyline(new PolylineOptions()
                    .addAll(all.subList(first.fromIndex, last.toIndex + 1))
                    .color(ContextCompat.getColor(this, colorFor(first.effect)))
                    .width(ROUTE_WIDTH_PX).jointType(JointType.ROUND)
                    .startCap(new RoundCap()).endCap(new RoundCap())
                    .zIndex(1f));
            runStart = i;
        }

        map.addMarker(new MarkerOptions().position(all.get(0))
                .title(getString(R.string.marker_start)));
        map.addMarker(new MarkerOptions().position(all.get(all.size() - 1))
                .title(getString(R.string.marker_end)));

        fillSummary(wind);
        summaryCard.setVisibility(View.VISIBLE);

        // Zoom to fit, once the summary card has its real height, keeping the
        // route out from under both cards.
        summaryCard.post(() -> {
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (LatLng point : all) {
                bounds.include(point);
            }
            map.setPadding(0, searchCard.getBottom(), 0,
                    findViewById(R.id.main).getHeight() - summaryCard.getTop());
            int edge = Math.round(32 * getResources().getDisplayMetrics().density);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), edge));
        });
    }

    private static int colorFor(WindEffect effect) {
        switch (effect) {
            case HEADWIND:
                return R.color.wind_headwind;
            case TAILWIND:
                return R.color.wind_tailwind;
            case CROSSWIND:
                return R.color.wind_crosswind;
            default:
                return R.color.wind_calm;
        }
    }

    private void fillSummary(RouteWind wind) {
        ((TextView) findViewById(R.id.headline)).setText(headlineFor(wind));

        int net = (int) Math.round(wind.averageHeadwindKmh);
        String push = net > 0 ? getString(R.string.net_headwind, net)
                : net < 0 ? getString(R.string.net_tailwind, -net)
                : getString(R.string.net_neutral);
        ((TextView) findViewById(R.id.details)).setText(getString(R.string.details,
                getString(R.string.distance_km, route.distanceMeters / 1000.0),
                formatDuration(route.durationSeconds), push,
                (int) Math.round(wind.maxGustKmh)));

        setShare(R.id.bar_headwind, R.id.legend_headwind, R.string.legend_headwind,
                wind.share(WindEffect.HEADWIND));
        setShare(R.id.bar_crosswind, R.id.legend_crosswind, R.string.legend_crosswind,
                wind.share(WindEffect.CROSSWIND));
        setShare(R.id.bar_tailwind, R.id.legend_tailwind, R.string.legend_tailwind,
                wind.share(WindEffect.TAILWIND));
        setShare(R.id.bar_calm, R.id.legend_calm, R.string.legend_calm,
                wind.share(WindEffect.CALM));

        // Google requires its notices to be shown with a cycling route.
        TextView warnings = findViewById(R.id.warnings);
        warnings.setText(TextUtils.join("\n", route.warnings));
        warnings.setVisibility(route.warnings.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** One word for the whole ride: whichever wind covers more than half of it. */
    private String headlineFor(RouteWind wind) {
        if (wind.share(WindEffect.HEADWIND) > 0.5) {
            return getString(R.string.headline_headwind);
        }
        if (wind.share(WindEffect.TAILWIND) > 0.5) {
            return getString(R.string.headline_tailwind);
        }
        if (wind.share(WindEffect.CROSSWIND) > 0.5) {
            return getString(R.string.headline_crosswind);
        }
        if (wind.share(WindEffect.CALM) > 0.5) {
            return getString(R.string.headline_calm);
        }
        return getString(R.string.headline_mixed);
    }

    /** Sets one slice of the share bar and its legend label. */
    private void setShare(int barId, int legendId, int labelRes, double share) {
        View bar = findViewById(barId);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
        params.weight = (float) share;
        bar.setLayoutParams(params);
        ((TextView) findViewById(legendId)).setText(
                getString(labelRes, (int) Math.round(share * 100)));
    }

    private String formatDuration(long seconds) {
        long minutes = Math.round(seconds / 60.0);
        return minutes >= 60
                ? getString(R.string.duration_h_min, minutes / 60, minutes % 60)
                : getString(R.string.duration_min, minutes);
    }

    // ---- small helpers -----------------------------------------------------------

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        goButton.setEnabled(!busy);
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
