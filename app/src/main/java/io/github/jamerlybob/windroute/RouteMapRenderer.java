package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.route.CyclingWarnings;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.wind.DirectionComparison;
import io.github.jamerlybob.windroute.wind.GustWarnings;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Draws the route, gust outlines, summary, and tappable stretch facts. */
public final class RouteMapRenderer {
    private static final float ROUTE_WIDTH_PX = 16f;
    private static final float CASING_WIDTH_PX = 24f;

    private final Activity activity;
    private final GoogleMap map;
    private final View root;
    private final View searchCard;
    private final View summaryCard;
    private RideAnalysis analysis;
    private Settings settings;
    private List<GeoPoint> lastFittedPoints;

    public RouteMapRenderer(Activity activity, GoogleMap map, View root,
                            View searchCard, View summaryCard) {
        this.activity = activity;
        this.map = map;
        this.root = root;
        this.searchCard = searchCard;
        this.summaryCard = summaryCard;
        map.setOnPolylineClickListener(this::showStretchFacts);
        map.setOnMapClickListener(point -> activity.findViewById(R.id.stretch_card)
                .setVisibility(View.GONE));
    }

    public void show(RideAnalysis analysis, Settings settings, String extraDetails,
                     int collapsedSheetHeight) {
        this.analysis = analysis;
        this.settings = settings;
        Route route = analysis.route;
        map.clear();
        List<LatLng> all = new ArrayList<>();
        for (GeoPoint point : route.points) {
            all.add(new LatLng(point.lat, point.lng));
        }
        map.addPolyline(new PolylineOptions().addAll(all)
                .color(ContextCompat.getColor(activity, R.color.route_casing))
                .width(CASING_WIDTH_PX).jointType(JointType.ROUND)
                .startCap(new RoundCap()).endCap(new RoundCap()));
        drawGustOutlines(all, analysis.gustWarnings, route);
        drawWindRuns(all, analysis.wind);

        map.addMarker(new MarkerOptions().position(all.get(0))
                .title(activity.getString(R.string.marker_start)));
        map.addMarker(new MarkerOptions().position(all.get(all.size() - 1))
                .title(activity.getString(R.string.marker_end)));
        fillSummary(route, analysis.wind, analysis.reverseWind, settings,
                route.durationSeconds, extraDetails);
        summaryCard.setVisibility(View.VISIBLE);
        if (lastFittedPoints != route.points) {
            lastFittedPoints = route.points;
            fitRoute(all, collapsedSheetHeight);
        } else {
            updatePadding(collapsedSheetHeight);
        }
    }

    private void drawWindRuns(List<LatLng> all, RouteWind wind) {
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
                    .color(ContextCompat.getColor(activity, colorFor(first.effect)))
                    .width(ROUTE_WIDTH_PX).jointType(JointType.ROUND)
                    .startCap(new RoundCap()).endCap(new RoundCap()).zIndex(1f));
            runStart = i;
        }

        // Google reports which polyline was tapped but not the tapped point.
        // Transparent hit targets preserve the efficient merged drawing above
        // while still identifying each individual 400 m analysis stretch.
        double stretchStart = 0;
        for (int i = 0; i < wind.stretches.size(); i++) {
            RouteWind.Stretch stretch = wind.stretches.get(i);
            Polyline target = map.addPolyline(new PolylineOptions()
                    .addAll(all.subList(stretch.fromIndex, stretch.toIndex + 1))
                    .color(android.graphics.Color.TRANSPARENT).width(40f)
                    .zIndex(3f).clickable(true));
            target.setTag(new WindRun(i, i, stretchStart));
            stretchStart += stretch.lengthMeters;
        }
    }

    private void drawGustOutlines(List<LatLng> all, List<GustWarnings.Warning> warnings,
                                  Route route) {
        if (warnings.isEmpty()) {
            return;
        }
        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        List<PatternItem> pattern = Arrays.asList(new Dash(18), new Gap(12));
        for (GustWarnings.Warning warning : warnings) {
            int from = indexAt(cumulative, warning.startMeters);
            int to = indexAt(cumulative, warning.startMeters + warning.lengthMeters);
            to = Math.max(from + 1, Math.min(to, all.size() - 1));
            // This thicker dashed line sits under the coloured road. Only its
            // two edges remain visible, making a findable outline without
            // covering the wind verdict itself.
            map.addPolyline(new PolylineOptions().addAll(all.subList(from, to + 1))
                    .color(ContextCompat.getColor(activity, R.color.wind_headwind))
                    .width(CASING_WIDTH_PX + 5).pattern(pattern).zIndex(0.5f));
        }
    }

    private void showStretchFacts(Polyline polyline) {
        if (!(polyline.getTag() instanceof WindRun) || analysis == null) {
            return;
        }
        WindRun run = (WindRun) polyline.getTag();
        double length = 0;
        double head = 0;
        double speed = 0;
        double gust = 0;
        for (int i = run.firstStretch; i <= run.lastStretch; i++) {
            RouteWind.Stretch stretch = analysis.wind.stretches.get(i);
            length += stretch.lengthMeters;
            head += stretch.headwindKmh * stretch.lengthMeters;
            speed += stretch.windSpeedKmh * stretch.lengthMeters;
            gust = Math.max(gust, stretch.gustKmh);
        }
        head = length > 0 ? head / length : 0;
        speed = length > 0 ? speed / length : 0;
        double cross = Math.sqrt(Math.max(0, speed * speed - head * head));
        long arrival = analysis.departureEpochSeconds + Math.round(
                analysis.route.durationSeconds * run.startMeters / analysis.route.distanceMeters);
        UnitText units = new UnitText(activity, settings);
        String push = head >= 0
                ? activity.getString(R.string.net_headwind, units.windSpeed(Math.abs(head)))
                : activity.getString(R.string.net_tailwind, units.windSpeed(Math.abs(head)));
        String gradient = analysis.elevation == null ? ""
                : activity.getString(R.string.stretch_gradient,
                        gradientAt(analysis.elevation, run.startMeters));
        String facts = activity.getString(R.string.stretch_facts,
                units.distance(run.startMeters),
                activity.getString(R.string.arrive_time,
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(new Date(arrival * 1000))),
                units.windSpeed(speed), push, units.windSpeed(cross),
                units.windSpeed(gust), gradient);
        ((TextView) activity.findViewById(R.id.stretch_facts)).setText(facts);
        activity.findViewById(R.id.stretch_card).setVisibility(View.VISIBLE);
    }

    private void fillSummary(Route route, RouteWind wind, RouteWind reverse,
                             Settings settings, long durationSeconds, String extraDetails) {
        UnitText units = new UnitText(activity, settings);
        ((TextView) activity.findViewById(R.id.headline)).setText(headlineFor(wind));
        String push = wind.averageHeadwindKmh > 0.5
                ? activity.getString(R.string.net_headwind,
                        units.windSpeed(Math.abs(wind.averageHeadwindKmh)))
                : wind.averageHeadwindKmh < -0.5
                        ? activity.getString(R.string.net_tailwind,
                                units.windSpeed(Math.abs(wind.averageHeadwindKmh)))
                        : activity.getString(R.string.net_neutral);
        String base = activity.getString(R.string.details, units.distance(route.distanceMeters),
                formatDuration(durationSeconds), push, units.windSpeed(wind.maxGustKmh));
        ((TextView) activity.findViewById(R.id.details)).setText(
                activity.getString(R.string.details_with_extras, base, extraDetails));
        setShare(R.id.bar_headwind, R.id.legend_headwind, R.string.legend_headwind,
                wind.share(WindEffect.HEADWIND));
        setShare(R.id.bar_crosswind, R.id.legend_crosswind, R.string.legend_crosswind,
                wind.share(WindEffect.CROSSWIND));
        setShare(R.id.bar_tailwind, R.id.legend_tailwind, R.string.legend_tailwind,
                wind.share(WindEffect.TAILWIND));
        setShare(R.id.bar_calm, R.id.legend_calm, R.string.legend_calm,
                wind.share(WindEffect.CALM));

        TextView comparisonView = activity.findViewById(R.id.reverse_comparison);
        DirectionComparison comparison = DirectionComparison.compare(wind, reverse);
        if (comparison.meaningful) {
            comparisonView.setText(activity.getString(R.string.reverse_comparison,
                    (int) Math.round(comparison.share * 100), effectName(comparison.effect)));
            comparisonView.setVisibility(View.VISIBLE);
        } else {
            comparisonView.setVisibility(View.GONE);
        }
        List<String> notices = CyclingWarnings.combine(
                activity.getString(R.string.cycling_notice), route.warnings);
        TextView warnings = activity.findViewById(R.id.warnings);
        warnings.setText(String.join("\n", notices));
        warnings.setVisibility(View.VISIBLE);
    }

    private void fitRoute(List<LatLng> points, int collapsedSheetHeight) {
        summaryCard.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (LatLng point : points) {
                bounds.include(point);
            }
            updatePadding(collapsedSheetHeight);
            int edge = Math.round(32 * activity.getResources().getDisplayMetrics().density);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), edge));
        });
    }

    private void updatePadding(int collapsedSheetHeight) {
        int measured = activity.findViewById(R.id.summary_collapsed).getHeight();
        map.setPadding(0, searchCard.getBottom(), 0,
                Math.max(measured, collapsedSheetHeight));
    }

    private static int indexAt(double[] cumulative, double meters) {
        int closest = 0;
        double gap = Double.MAX_VALUE;
        for (int i = 0; i < cumulative.length; i++) {
            double candidate = Math.abs(cumulative[i] - meters);
            if (candidate < gap) {
                gap = candidate;
                closest = i;
            }
        }
        return closest;
    }

    private static double gradientAt(ElevationProfile profile, double meters) {
        int closest = 0;
        double gap = Double.MAX_VALUE;
        for (int i = 0; i < profile.size(); i++) {
            double candidate = Math.abs(profile.distanceMeters[i] - meters);
            if (candidate < gap) {
                gap = candidate;
                closest = i;
            }
        }
        return profile.gradientPercent[closest];
    }

    private String headlineFor(RouteWind wind) {
        if (wind.share(WindEffect.HEADWIND) > 0.5) return activity.getString(R.string.headline_headwind);
        if (wind.share(WindEffect.TAILWIND) > 0.5) return activity.getString(R.string.headline_tailwind);
        if (wind.share(WindEffect.CROSSWIND) > 0.5) return activity.getString(R.string.headline_crosswind);
        if (wind.share(WindEffect.CALM) > 0.5) return activity.getString(R.string.headline_calm);
        return activity.getString(R.string.headline_mixed);
    }

    private void setShare(int barId, int legendId, int labelRes, double share) {
        View bar = activity.findViewById(barId);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
        params.weight = (float) share;
        bar.setLayoutParams(params);
        ((TextView) activity.findViewById(legendId)).setText(
                activity.getString(labelRes, (int) Math.round(share * 100)));
    }

    private String formatDuration(long seconds) {
        long minutes = Math.round(seconds / 60.0);
        return minutes >= 60
                ? activity.getString(R.string.duration_h_min, minutes / 60, minutes % 60)
                : activity.getString(R.string.duration_min, minutes);
    }

    private String effectName(WindEffect effect) {
        switch (effect) {
            case HEADWIND: return activity.getString(R.string.effect_headwind);
            case TAILWIND: return activity.getString(R.string.effect_tailwind);
            case CROSSWIND: return activity.getString(R.string.effect_crosswind);
            default: return activity.getString(R.string.effect_calm);
        }
    }

    private static int colorFor(WindEffect effect) {
        switch (effect) {
            case HEADWIND: return R.color.wind_headwind;
            case TAILWIND: return R.color.wind_tailwind;
            case CROSSWIND: return R.color.wind_crosswind;
            default: return R.color.wind_calm;
        }
    }

    private static final class WindRun {
        final int firstStretch;
        final int lastStretch;
        final double startMeters;

        WindRun(int firstStretch, int lastStretch, double startMeters) {
            this.firstStretch = firstStretch;
            this.lastStretch = lastStretch;
            this.startMeters = startMeters;
        }
    }
}
