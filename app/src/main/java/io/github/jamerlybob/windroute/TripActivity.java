package io.github.jamerlybob.windroute;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.poi.OverpassClient;
import io.github.jamerlybob.windroute.poi.Poi;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import io.github.jamerlybob.windroute.poi.PoiKind;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.settings.SettingsStore;
import io.github.jamerlybob.windroute.trip.DaySplitter;
import io.github.jamerlybob.windroute.trip.TripPlanner;
import io.github.jamerlybob.windroute.trip.TripStore;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.weather.DaylightClient;
import io.github.jamerlybob.windroute.weather.DaylightForecast;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Plans one imported or searched route as consecutive forecast riding days. */
public final class TripActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TripStore store;
    private TripStore.SavedTrip saved;
    private Route route;
    private ElevationProfile elevation;
    private Settings settings;
    private List<DaySplitter.Day> days = new ArrayList<>();
    private double[] boundaries = new double[0];
    private final List<RouteForecastLoader.Result> forecasts = new ArrayList<>();
    private final List<List<DaylightForecast>> daylight = new ArrayList<>();
    private List<PoiAlongRoute> places = new ArrayList<>();
    private long startDayMillis;
    private int startHour = 8;
    private int startMinute;
    private long fetchedAt;
    private String savedId;
    private GoogleMap tripMap;
    private volatile int forecastGeneration;
    private int requestedDayCount = 3;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip);
        keepContentClearOfSystemBars();
        settings = SettingsStore.load(this);
        store = new TripStore(this);
        try {
            savedId = getIntent().getStringExtra("trip_id");
            saved = savedId == null ? store.loadDraft() : store.load(savedId);
            route = saved.route;
            places = PoiAlongRoute.locate(route.points, saved.places);
            fetchedAt = saved.forecastFetchedAt;
            if (saved.elevations != null && saved.elevationDistances != null
                    && saved.elevations.length == saved.elevationDistances.length) {
                elevation = new ElevationProfile(saved.elevationDistances, saved.elevations);
            }
        } catch (Exception error) {
            Snackbar.make(findViewById(R.id.trip_root), R.string.trip_error, Snackbar.LENGTH_LONG).show();
            return;
        }
        Calendar today = Calendar.getInstance();
        zeroTime(today);
        startDayMillis = saved.startDayMillis > 0
                ? saved.startDayMillis : today.getTimeInMillis();
        startHour = saved.startHour;
        startMinute = saved.startMinute;
        forecasts.addAll(saved.forecasts);
        daylight.addAll(saved.daylight);
        wireControls();
        if (saved.boundaries != null && saved.boundaries.length > 0) {
            boundaries = saved.boundaries.clone();
            days = DaySplitter.atDistances(GeoMath.cumulativeMeters(route.points), elevation,
                    boundaries);
            requestedDayCount = days.size();
            updateDayCount();
            if (!forecasts.isEmpty()) renderDays();
            refreshForecasts();
        } else {
            splitAutomatically();
        }
        updateAge();
        SupportMapFragment map = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.trip_map);
        if (map != null) map.getMapAsync(this);
    }

    private void keepContentClearOfSystemBars() {
        View root = findViewById(R.id.trip_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        tripMap = map;
        tripMap.getUiSettings().setMapToolbarEnabled(false);
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        tripMap.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(this,
                night ? R.raw.map_style_night : R.raw.map_style_day));
        drawTripMap();
    }

    private void drawTripMap() {
        if (tripMap == null || route == null) return;
        tripMap.clear();
        List<LatLng> line = new ArrayList<>();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (io.github.jamerlybob.windroute.route.GeoPoint point : route.points) {
            LatLng mapPoint = new LatLng(point.lat, point.lng);
            line.add(mapPoint);
            bounds.include(mapPoint);
        }
        int[] tones = {R.color.wind_tailwind, R.color.wind_crosswind, R.color.wind_headwind, R.color.wind_calm};
        for (int i=0; i<days.size(); i++) {
            DaySplitter.Day day=days.get(i);
            tripMap.addPolyline(new PolylineOptions().addAll(line.subList(day.startPointIndex,day.endPointIndex+1))
                    .color(ContextCompat.getColor(this,tones[i%tones.length])).width(dp(5))
                    .jointType(com.google.android.gms.maps.model.JointType.ROUND));
            if(i<days.size()-1) tripMap.addMarker(new MarkerOptions().position(line.get(day.endPointIndex))
                    .title(getString(R.string.trip_day_title,i+1,"" )).icon(MapIcons.tent(this,i+1)));
        }
        for (PoiAlongRoute place : places) {
            float hue = place.poi.kind == PoiKind.WATER ? BitmapDescriptorFactory.HUE_AZURE
                    : place.poi.kind == PoiKind.FOOD ? BitmapDescriptorFactory.HUE_ORANGE
                    : BitmapDescriptorFactory.HUE_GREEN;
            String kind = poiKind(place.poi.kind);
            tripMap.addMarker(new MarkerOptions().position(new LatLng(
                    place.poi.position.lat, place.poi.position.lng))
                    .title(place.poi.name == null ? getString(R.string.place_unnamed, kind)
                            : place.poi.name).snippet(kind)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
        }
        tripMap.setOnMapLoadedCallback(() -> tripMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(24))));
    }

    private void wireControls() {
        findViewById(R.id.trip_back).setOnClickListener(v -> finish());
        findViewById(R.id.more_options).setOnClickListener(v -> {
            View more=findViewById(R.id.trip_more); more.setVisibility(more.getVisibility()==View.GONE ? View.VISIBLE : View.GONE); renderDays();
        });
        com.google.android.material.slider.Slider daily = findViewById(R.id.daily_slider);
        daily.setValueFrom(10);
        daily.setValueTo(200);
        daily.setStepSize(5);
        daily.setTickVisible(false);
        daily.setValue(getSharedPreferences("stops",0).getInt("km",50));
        updateDistanceLabel();
        daily.addOnChangeListener((slider,value,user) -> updateDistanceLabel());
        daily.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(com.google.android.material.slider.Slider slider) { }
            @Override public void onStopTrackingTouch(com.google.android.material.slider.Slider slider) {
                getSharedPreferences("stops",0).edit().putInt("km",(int)slider.getValue()).apply();
                splitAutomatically();
            }
        });
        RadioGroup modes = findViewById(R.id.split_mode);
        modes.setOnCheckedChangeListener((group,checked) -> {
            boolean distance = checked == R.id.split_distance;
            daily.setVisibility(distance ? View.VISIBLE : View.GONE);
            findViewById(R.id.daily_distance_label).setVisibility(distance ? View.VISIBLE : View.GONE);
            findViewById(R.id.day_stepper).setVisibility(distance ? View.GONE : View.VISIBLE);
            splitAutomatically();
        });
        findViewById(R.id.day_minus).setOnClickListener(v -> changeDayCount(-1));
        findViewById(R.id.day_plus).setOnClickListener(v -> changeDayCount(1));
        updateDayCount();
        ((android.widget.CompoundButton)findViewById(R.id.count_climbing)).setOnCheckedChangeListener(
                (button,checked) -> splitAutomatically());
        findViewById(R.id.trip_start_date).setOnClickListener(v -> chooseDate());
        findViewById(R.id.trip_start_time).setOnClickListener(v -> chooseTime());
        findViewById(R.id.shift_earlier).setOnClickListener(v -> shift(-1));
        findViewById(R.id.shift_later).setOnClickListener(v -> shift(1));
        findViewById(R.id.trip_find_places).setOnClickListener(v -> findPlaces());
        findViewById(R.id.save_trip).setOnClickListener(v -> saveTrip());
        updateDateButtons();
    }

    private void updateDistanceLabel() {
        com.google.android.material.slider.Slider slider = findViewById(R.id.daily_slider);
        ((TextView)findViewById(R.id.daily_distance_label)).setText(
                getString(R.string.daily_distance_value,Math.round(slider.getValue())));
    }

    private void changeDayCount(int delta) {
        requestedDayCount = Math.max(1,Math.min(route.points.size()-1,requestedDayCount+delta));
        updateDayCount();
        splitAutomatically();
    }

    private void updateDayCount() {
        ((TextView)findViewById(R.id.day_count)).setText(getString(R.string.day_count_value,requestedDayCount));
        findViewById(R.id.day_minus).setEnabled(requestedDayCount>1);
        findViewById(R.id.day_plus).setEnabled(requestedDayCount<route.points.size()-1);
    }

    private void splitAutomatically() {
        try {
            boolean countClimbing = ((android.widget.CompoundButton) findViewById(
                    R.id.count_climbing)).isChecked();
            if (((RadioGroup)findViewById(R.id.split_mode)).getCheckedRadioButtonId() == R.id.split_distance) {
                double km = ((com.google.android.material.slider.Slider)findViewById(R.id.daily_slider)).getValue();
                double[] cumulative = GeoMath.cumulativeMeters(route.points);
                days = countClimbing && elevation != null
                        ? DaySplitter.byEffort(cumulative, elevation, km * 1000)
                        : DaySplitter.byDistance(cumulative, elevation, km * 1000);
            } else {
                int count = requestedDayCount;
                days = TripPlanner.byDays(route, elevation, count, countClimbing);
            }
            boundaries = TripPlanner.boundaries(days);
            double[] distances = GeoMath.cumulativeMeters(route.points);
            boundaries = TripPlanner.snapBoundaries(route, boundaries, places);
            days=DaySplitter.atDistances(distances,elevation,boundaries);
            drawTripMap();
            refreshForecasts();
        } catch (RuntimeException error) {
            message(error.getMessage());
        }
    }

    private void refreshForecasts() {
        final int generation = ++forecastGeneration;
        final List<DaySplitter.Day> requestedDays = new ArrayList<>(days);
        findViewById(R.id.trip_progress).setVisibility(View.VISIBLE);
        background.execute(() -> {
            try {
                List<RouteForecastLoader.Result> loadedForecasts = new ArrayList<>();
                List<List<DaylightForecast>> loadedDaylight = new ArrayList<>();
                for (DaySplitter.Day day : requestedDays) {
                    // Releasing the slider or tapping the stepper can supersede
                    // a queued plan. Do not spend free API calls on stale days.
                    if (generation != forecastGeneration || Thread.currentThread().isInterrupted()) return;
                    Route dayRoute = TripPlanner.routeForDay(route, day);
                    loadedForecasts.add(RouteForecastLoader.load(dayRoute, departureFor(requestedDays.indexOf(day))));
                    loadedDaylight.add(DaylightClient.fetch(dayRoute.points.get(0)));
                }
                fetchedAt = System.currentTimeMillis();
                main.post(() -> {
                    if (isDestroyed() || generation != forecastGeneration) return;
                    forecasts.clear();
                    forecasts.addAll(loadedForecasts);
                    daylight.clear();
                    daylight.addAll(loadedDaylight);
                    findViewById(R.id.trip_progress).setVisibility(View.GONE);
                    renderDays();
                    updateAge();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (isDestroyed() || generation != forecastGeneration) return;
                    findViewById(R.id.trip_progress).setVisibility(View.GONE);
                    renderDays();
                    message(error.getMessage());
                });
            }
        });
    }

    private void renderDays() {
        LinearLayout list = findViewById(R.id.trip_days_list);
        list.removeAllViews();
        for (int i = 0; i < days.size(); i++) {
            DaySplitter.Day day = days.get(i);
            MaterialCardView card = new MaterialCardView(this);
            card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                    card,com.google.android.material.R.attr.colorSurfaceContainerLow));
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(14);
            body.setPadding(pad, pad, pad, pad);
            TextView title = text(android.R.style.TextAppearance_Material_Large);
            title.setText(getString(R.string.trip_day_title, i + 1,
                    new SimpleDateFormat("EEE d MMM", Locale.getDefault())
                            .format(new Date(startDayMillis + i * 86_400_000L))));
            LinearLayout header = new LinearLayout(this);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.VERTICAL);
            heading.addView(title);
            header.addView(heading,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
            body.addView(header);
            RideAnalysis analysis = analysisFor(i);
            if (analysis == null) {
                TextView outside = text(android.R.style.TextAppearance_Material_Body1);
                outside.setText(R.string.outside_forecast);
                body.addView(outside);
            } else {
                WindEffect verdict = io.github.jamerlybob.windroute.wind.WindPresentation.verdict(analysis.wind);
                int words = verdict == WindEffect.HEADWIND ? R.string.headline_headwind
                        : verdict == WindEffect.TAILWIND ? R.string.headline_tailwind
                        : verdict == WindEffect.CROSSWIND ? R.string.headline_crosswind : R.string.headline_calm;
                TextView compact = text(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                compact.setText(dayHeadlineSummary(day,analysis));
                heading.addView(compact);
                WindBadgeView badge = new WindBadgeView(this,null);
                int color = verdict == WindEffect.HEADWIND ? R.color.wind_headwind
                        : verdict == WindEffect.TAILWIND ? R.color.wind_tailwind
                        : verdict == WindEffect.CROSSWIND ? R.color.wind_crosswind : R.color.wind_calm;
                badge.setWind(ContextCompat.getColor(this,color),
                        io.github.jamerlybob.windroute.wind.WindPresentation.routeArrow(analysis.route,analysis.wind),
                        new UnitText(this,settings).windSpeed(analysis.wind.averageWindKmh));
                badge.setContentDescription(getString(R.string.trip_badge_description,getString(words),badge.getContentDescription()));
                header.addView(badge,new LinearLayout.LayoutParams(dp(76),dp(76)));
                RouteForecastLoader.Result cached = forecasts.get(i);
                io.github.jamerlybob.windroute.trip.DayAdvice advice = io.github.jamerlybob.windroute.trip.DayAdvice.compare(
                        analysis.route, analysis.wind, analysis.weather, cached.sampleIndexes, cached.forecasts,
                        analysis.departureEpochSeconds, settings.calmBelowKmh);
                if (advice.badDay) {
                    card.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                            card, com.google.android.material.R.attr.colorErrorContainer));
                    TextView suggestion = text(android.R.style.TextAppearance_Material_Body1);
                    UnitText units = new UnitText(this, settings);
                    if (advice.suggestion == io.github.jamerlybob.windroute.trip.DayAdvice.Suggestion.LESS_HEADWIND) {
                        suggestion.setText(getString(R.string.later_wind,
                                units.windSpeed(analysis.wind.averageHeadwindKmh), units.windSpeed(advice.laterHeadwindKmh)));
                    } else if (advice.suggestion == io.github.jamerlybob.windroute.trip.DayAdvice.Suggestion.LESS_RAIN) {
                        suggestion.setText(getString(R.string.later_rain, analysis.weather.wettestRainMm, advice.laterRainMm));
                    } else {
                        suggestion.setText(R.string.compare_trip_day);
                    }
                    body.addView(suggestion);
                }
                body.addView(windBar(analysis));
                TextView summary = text(android.R.style.TextAppearance_Material_Body1);
                summary.setText(daySummary(i, day, analysis));
                body.addView(summary);
            }
            if (i < days.size() - 1 && findViewById(R.id.trip_more).getVisibility()==View.VISIBLE) body.addView(boundaryControls(i));
            final int dayIndex = i;
            card.setOnClickListener(v -> openDay(dayIndex));
            card.addView(body);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, dp(8), 0, 0);
            list.addView(card, cardParams);
        }
        drawTripMap();
        renderBestStart();
        renderPlaceGaps();
    }

    private View windBar(RideAnalysis analysis) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setMinimumHeight(dp(10));
        addShare(bar, analysis.wind.share(WindEffect.HEADWIND), R.color.wind_headwind);
        addShare(bar, analysis.wind.share(WindEffect.CROSSWIND), R.color.wind_crosswind);
        addShare(bar, analysis.wind.share(WindEffect.TAILWIND), R.color.wind_tailwind);
        addShare(bar, analysis.wind.share(WindEffect.CALM), R.color.wind_calm);
        return bar;
    }

    private void addShare(LinearLayout bar, double share, int color) {
        View segment = new View(this);
        segment.setBackgroundColor(ContextCompat.getColor(this, color));
        bar.addView(segment, new LinearLayout.LayoutParams(0, dp(10), (float) share));
    }

    private String dayHeadlineSummary(DaySplitter.Day day, RideAnalysis analysis) {
        int heightFormat = settings.elevationUnit == Settings.ElevationUnit.FEET ? R.string.height_feet : R.string.height_metres;
        String ascent = getString(heightFormat,UnitFormatter.elevation(day.ascentMeters,settings.elevationUnit));
        int weather = Double.isNaN(analysis.weather.wettestRainMm) ? R.string.weather_unknown_badge
                : analysis.weather.wettestRainMm >= .1 ? R.string.weather_rain_badge : R.string.weather_dry_badge;
        return io.github.jamerlybob.windroute.trip.DayCardText.summary(getString(R.string.day_card_summary),
                new UnitText(this,settings).distance(day.distanceMeters),ascent,getString(weather));
    }

    private String daySummary(int index, DaySplitter.Day day, RideAnalysis analysis) {
        UnitText units = new UnitText(this, settings);
        String wind = analysis.wind.averageHeadwindKmh > 0.5
                ? getString(R.string.net_headwind, units.windSpeed(analysis.wind.averageHeadwindKmh))
                : analysis.wind.averageHeadwindKmh < -0.5
                ? getString(R.string.net_tailwind, units.windSpeed(-analysis.wind.averageHeadwindKmh))
                : getString(R.string.net_neutral);
        String cost = windCost(analysis.power.differenceMinutes);
        String rain = !Double.isNaN(analysis.weather.chanceOfAnyRainPercent)
                && analysis.weather.chanceOfAnyRainPercent >= 1
                ? io.github.jamerlybob.windroute.weather.RainDisplay.showAmount(analysis.weather.wettestRainMm)
                ? getString(R.string.trip_rain, analysis.weather.chanceOfAnyRainPercent,
                analysis.weather.wettestRainMm)
                : getString(R.string.trip_rain_chance, analysis.weather.chanceOfAnyRainPercent)
                : getString(R.string.trip_dry);
        String temperature;
        if (Double.isNaN(analysis.weather.coldestC)
                || Double.isNaN(analysis.weather.warmestC)) {
            temperature = getString(R.string.weather_unknown_temperature);
        } else {
            temperature = TemperatureRangeFormatter.format(
                    UnitFormatter.temperature(analysis.weather.coldestC,
                            settings.temperatureUnit),
                    UnitFormatter.temperature(analysis.weather.warmestC,
                            settings.temperatureUnit),
                    getString(R.string.temperature_single),
                    getString(R.string.temperature_range));
        }
        DaylightForecast light = daylightFor(index);
        String sunrise = light == null ? getString(R.string.unavailable_short)
                : time(light.sunriseEpochSeconds);
        String sunset = light == null ? getString(R.string.unavailable_short)
                : time(light.sunsetEpochSeconds);
        int intoWind = 0;
        for (ClimbWind climb : analysis.climbWinds) if (climb.isClimbIntoHeadwind) intoWind++;
        String warning = intoWind == 0 ? "" : "\n" + getResources().getQuantityString(
                R.plurals.climbs_into_wind, intoWind, intoWind);
        String weatherDetails = analysis.weather.chanceOfAnyRainPercent >= 20
                || io.github.jamerlybob.windroute.weather.RainDisplay.showAmount(analysis.weather.wettestRainMm)
                ? getString(R.string.trip_weather_details,rain,temperature) : temperature;
        return io.github.jamerlybob.windroute.trip.DayCardText.details(getString(R.string.day_card_details),
                wind,cost,weatherDetails,sunrise,sunset,warning);
    }

    private RideAnalysis analysisFor(int index) {
        if (index >= forecasts.size()) return null;
        Route dayRoute = TripPlanner.routeForDay(route, days.get(index));
        RouteForecastLoader.Result result = forecasts.get(index);
        long departure = departureFor(index);
        long duration = UnitFormatter.ridingDurationSeconds(dayRoute.distanceMeters,
                dayRoute.durationSeconds, settings.ridingSpeedKmh);
        if (!RideAnalysis.forecastCovers(result.forecasts, departure, duration)) return null;
        return RideAnalysis.build(dayRoute, result.sampleIndexes, result.forecasts, departure,
                settings, elevationFor(days.get(index)), departure);
    }

    private ElevationProfile elevationFor(DaySplitter.Day day) {
        if (elevation == null) return null;
        double[] routeDistances = GeoMath.cumulativeMeters(route.points);
        double start = routeDistances[day.startPointIndex];
        double end = routeDistances[day.endPointIndex];
        List<Double> distances = new ArrayList<>();
        List<Double> heights = new ArrayList<>();
        distances.add(0.0);
        heights.add(elevationAt(start));
        for (int i = 0; i < elevation.size(); i++) {
            double distance = elevation.distanceMeters[i];
            if (distance > start && distance < end) {
                distances.add(distance - start);
                heights.add(elevation.rawElevationMeters[i]);
            }
        }
        distances.add(end - start);
        heights.add(elevationAt(end));
        return new ElevationProfile(toArray(distances), toArray(heights));
    }

    private LinearLayout boundaryControls(int boundary) {
        LinearLayout controls = new LinearLayout(this);
        com.google.android.material.button.MaterialButton minus = outlinedButton(R.string.nudge_earlier);
        com.google.android.material.button.MaterialButton plus = outlinedButton(R.string.nudge_later);
        minus.setOnClickListener(v -> nudge(boundary, -5000));
        plus.setOnClickListener(v -> nudge(boundary, 5000));
        controls.addView(minus);
        controls.addView(plus);
        return controls;
    }

    private void nudge(int boundary, double delta) {
        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        boundaries = TripPlanner.nudge(boundaries, boundary, delta,
                cumulative[cumulative.length - 1]);
        days = DaySplitter.atDistances(GeoMath.cumulativeMeters(route.points), elevation, boundaries);
        refreshForecasts();
    }

    private void shift(int days) {
        Calendar earliest = Calendar.getInstance();
        zeroTime(earliest);
        long candidate = startDayMillis + days * 86_400_000L;
        long latest = earliest.getTimeInMillis() + 7 * 86_400_000L;
        startDayMillis = Math.max(earliest.getTimeInMillis(), Math.min(latest, candidate));
        updateDateButtons();
        renderDays();
    }

    private void renderBestStart() {
        long bestDay = startDayMillis;
        double bestScore = Double.MAX_VALUE;
        boolean bestDry = false;
        Calendar today = Calendar.getInstance();
        zeroTime(today);
        for (int offset = 0; offset <= 7; offset++) {
            long candidate = today.getTimeInMillis() + offset * 86_400_000L;
            long old = startDayMillis;
            startDayMillis = candidate;
            double score = 0;
            boolean complete = true;
            boolean dry = true;
            for (int i = 0; i < days.size(); i++) {
                RideAnalysis analysis = analysisFor(i);
                if (analysis == null) { complete = false; break; }
                dry &= analysis.weather.wettestRainMm < 0.05
                        && analysis.weather.chanceOfAnyRainPercent < 20;
                score += analysis.departures.isEmpty() ? 0 : analysis.departures.get(0).score;
            }
            startDayMillis = old;
            if (complete && score < bestScore) {
                bestScore = score; bestDay = candidate; bestDry = dry;
            }
        }
        com.google.android.material.button.MaterialButton best = findViewById(R.id.best_trip);
        final long chosenDay = bestDay;
        if (bestScore == Double.MAX_VALUE) {
            best.setText(R.string.best_trip_unavailable);
            best.setOnClickListener(null);
            best.setEnabled(false);
            return;
        }
        best.setEnabled(true);
        best.setText(getString(R.string.best_trip_start_reason,
                new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(bestDay)),
                getString(bestDry ? R.string.best_trip_dry_reason : R.string.best_trip_balanced_reason)));
        best.setOnClickListener(v -> {
            startDayMillis = chosenDay;
            updateDateButtons();
            renderDays();
        });
    }

    private void chooseDate() {
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(startDayMillis);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(year, month, day);
            zeroTime(selected);
            startDayMillis = selected.getTimeInMillis();
            updateDateButtons();
            renderDays();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        Calendar today = Calendar.getInstance();
        zeroTime(today);
        picker.getDatePicker().setMinDate(today.getTimeInMillis());
        picker.getDatePicker().setMaxDate(today.getTimeInMillis() + 7 * 86_400_000L);
        picker.show();
    }

    private void chooseTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            startHour = hour;
            startMinute = minute;
            updateDateButtons();
            renderDays();
        }, startHour, startMinute, android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void updateDateButtons() {
        String date = new SimpleDateFormat("EEE d MMM", Locale.getDefault())
                .format(new Date(startDayMillis));
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, startHour);
        time.set(Calendar.MINUTE, startMinute);
        ((TextView) findViewById(R.id.trip_start_date)).setText(getString(R.string.start_date, date));
        ((TextView) findViewById(R.id.trip_start_time)).setText(getString(R.string.daily_start,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(time.getTime())));
    }

    private long departureFor(int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startDayMillis + day * 86_400_000L);
        calendar.set(Calendar.HOUR_OF_DAY, startHour);
        calendar.set(Calendar.MINUTE, startMinute);
        return calendar.getTimeInMillis() / 1000;
    }

    private DaylightForecast daylightFor(int day) {
        if (day >= daylight.size()) return null;
        long wanted = (startDayMillis + day * 86_400_000L) / 1000;
        DaylightForecast best = null;
        long gap = Long.MAX_VALUE;
        for (DaylightForecast candidate : daylight.get(day)) {
            long candidateGap = Math.abs(candidate.dayEpochSeconds - wanted);
            if (candidateGap < gap) { gap = candidateGap; best = candidate; }
        }
        return gap <= 86_400 ? best : null;
    }

    private void openDay(int index) {
        Route dayRoute = TripPlanner.routeForDay(route, days.get(index));
        RouteStore routeStore = new RouteStore(this);
        routeStore.saveRoute(getString(R.string.trip_day_compact, index + 1, days.size(),
                        new UnitText(this, settings).distance(days.get(index).distanceMeters)),
                getString(R.string.gpx_finish), dayRoute);
        ElevationProfile dayElevation = elevationFor(days.get(index));
        if (dayElevation != null) routeStore.saveElevations(dayElevation.rawElevationMeters);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("trip_departure", departureFor(index));
        startActivity(intent);
    }

    private void findPlaces() {
        if (!findViewById(R.id.trip_find_places).isEnabled()) return;
        findViewById(R.id.trip_find_places).setEnabled(false);
        findViewById(R.id.trip_places_progress).setVisibility(View.VISIBLE);
        TextView status = findViewById(R.id.trip_places_status);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.places_busy);
        background.execute(() -> {
            try {
                List<Poi> found = OverpassClient.fetch(route.points,
                        Arrays.asList(PoiKind.WATER, PoiKind.FOOD, PoiKind.CAMPING, PoiKind.TOWN), 500);
                List<PoiAlongRoute> located = PoiAlongRoute.locate(route.points, found);
                main.post(() -> {
                    if (isDestroyed()) return;
                    places = located;
                    double[] snapped = TripPlanner.snapBoundaries(route, boundaries, places);
                    if (!Arrays.equals(snapped, boundaries)) {
                        boundaries = snapped;
                        days = DaySplitter.atDistances(GeoMath.cumulativeMeters(route.points), elevation, boundaries);
                        refreshForecasts();
                    }
                    findViewById(R.id.trip_places_progress).setVisibility(View.GONE);
                    findViewById(R.id.trip_find_places).setEnabled(true);
                    status.setText(places.isEmpty() ? getString(R.string.places_none, 500)
                            : getString(R.string.places_results, places.size()));
                    findViewById(R.id.trip_places_credit).setVisibility(View.VISIBLE);
                    renderPlaceGaps();
                    drawTripMap();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (isDestroyed()) return;
                    findViewById(R.id.trip_places_progress).setVisibility(View.GONE);
                    findViewById(R.id.trip_find_places).setEnabled(true);
                    status.setText(R.string.places_error);
                });
            }
        });
    }

    private void renderPlaceGaps() {
        LinearLayout list = findViewById(R.id.trip_places_list);
        list.removeAllViews();
        if (places.isEmpty()) return;
        UnitText units = new UnitText(this, settings);
        double threshold;
        try { threshold = Double.parseDouble(((EditText) findViewById(
                R.id.gap_threshold)).getText().toString()) * 1000; }
        catch (NumberFormatException ignored) { threshold = 40_000; }
        double start = 0;
        for (int i = 0; i < days.size(); i++) {
            double end = start + days.get(i).distanceMeters;
            double water = PoiAlongRoute.longestGapBetween(places, PoiKind.WATER, start, end);
            double food = PoiAlongRoute.longestGapBetween(places, PoiKind.FOOD, start, end);
            TextView row = text(android.R.style.TextAppearance_Material_Body1);
            row.setText(getString(R.string.gap_line, i + 1, units.distance(water),
                    units.distance(food), Math.max(water, food) > threshold
                            ? getString(R.string.gap_warning) : ""));
            if (Math.max(water, food) > threshold) row.setTextColor(
                    ContextCompat.getColor(this, R.color.wind_headwind));
            list.addView(row);
            start = end;
        }
        PlacesListController.append(this, list, places, settings, (located, name, kind) ->
                new AlertDialog.Builder(this).setTitle(name)
                    .setMessage(kind + (located.poi.openingHours == null ? ""
                            : "\n" + located.poi.openingHours))
                    .setPositiveButton(R.string.add_to_export, (dialog, which) -> {
                        new RouteStore(this).addExportWaypoint(
                                new io.github.jamerlybob.windroute.gpx.GpxParser.Waypoint(
                                        located.poi.position, name, null));
                        message(getString(R.string.added_to_export));
                    })
                    .setNegativeButton(android.R.string.cancel, null).show());
    }

    private void saveTrip() {
        try {
            List<Poi> rawPlaces = new ArrayList<>();
            for (PoiAlongRoute place : places) rawPlaces.add(place.poi);
            String name = getString(R.string.trip_name,
                    new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(startDayMillis)));
            savedId = store.save(new TripStore.SavedTrip(savedId, name, route,
                    elevation == null ? null : elevation.distanceMeters,
                    elevation == null ? null : elevation.rawElevationMeters,
                    boundaries, rawPlaces, fetchedAt, new ArrayList<>(forecasts),
                    copyDaylight(), startDayMillis, startHour, startMinute));
            message(getString(R.string.trip_saved));
        } catch (Exception error) {
            message(error.getMessage());
        }
    }

    private List<List<DaylightForecast>> copyDaylight() {
        List<List<DaylightForecast>> result = new ArrayList<>();
        for (List<DaylightForecast> day : daylight) result.add(new ArrayList<>(day));
        return result;
    }

    private double elevationAt(double distance) {
        for (int i = 1; i < elevation.size(); i++) {
            if (distance <= elevation.distanceMeters[i]) {
                double before = elevation.distanceMeters[i - 1];
                double length = elevation.distanceMeters[i] - before;
                double fraction = length <= 0 ? 0 : (distance - before) / length;
                return elevation.rawElevationMeters[i - 1] + fraction
                        * (elevation.rawElevationMeters[i]
                        - elevation.rawElevationMeters[i - 1]);
            }
        }
        return elevation.rawElevationMeters[elevation.size() - 1];
    }

    private static double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private String poiKind(PoiKind kind) {
        switch (kind) {
            case TOWN: return getString(R.string.poi_town);
            case WATER: return getString(R.string.poi_water);
            case FOOD: return getString(R.string.poi_food);
            case CAMPING: return getString(R.string.poi_camping);
            case BIKE_SHOP: return getString(R.string.poi_bike_shop);
            case TOILETS: return getString(R.string.poi_toilets);
            default: return getString(R.string.poi_shelter);
        }
    }

    private void updateAge() {
        TextView age = findViewById(R.id.forecast_age);
        if (fetchedAt == 0) { age.setText(R.string.forecast_fresh); return; }
        if (io.github.jamerlybob.windroute.weather.ForecastAge.justUpdated(fetchedAt,System.currentTimeMillis())) {
            age.setText(R.string.forecast_just_updated);
            return;
        }
        long minutes = Math.max(0, (System.currentTimeMillis() - fetchedAt) / 60_000);
        String amount = minutes < 120 ? getString(R.string.age_minutes, minutes)
                : minutes < 2880 ? getString(R.string.age_hours, minutes / 60)
                : getString(R.string.age_days, minutes / 1440);
        age.setText(getString(R.string.forecast_age, amount));
    }

    private TextView text(int appearance) {
        TextView view = new TextView(this);
        view.setTextAppearance(appearance);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private com.google.android.material.button.MaterialButton button(int text) {
        com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
        if (text != 0) button.setText(text);
        return button;
    }

    private com.google.android.material.button.MaterialButton outlinedButton(int text) {
        com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String time(long epochSeconds) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(epochSeconds * 1000));
    }

    private String windCost(double minutes) {
        return WindCostText.format(this, minutes, false);
    }

    private void message(String text) {
        Snackbar.make(findViewById(R.id.trip_root), text == null ? getString(R.string.trip_error) : text,
                Snackbar.LENGTH_LONG).show();
    }

    private static void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    @Override protected void onDestroy() {
        background.shutdownNow();
        super.onDestroy();
    }
}
