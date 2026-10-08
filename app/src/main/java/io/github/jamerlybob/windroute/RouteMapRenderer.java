package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.route.CyclingWarnings;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.wind.DirectionComparison;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Draws one already-computed route and its summary; it performs no I/O. */
public final class RouteMapRenderer {
    private static final float ROUTE_WIDTH_PX = 16f;
    private static final float CASING_WIDTH_PX = 24f;

    private final Activity activity;
    private final GoogleMap map;
    private final View root;
    private final View searchCard;
    private final View summaryCard;

    public RouteMapRenderer(Activity activity, GoogleMap map, View root,
                            View searchCard, View summaryCard) {
        this.activity = activity;
        this.map = map;
        this.root = root;
        this.searchCard = searchCard;
        this.summaryCard = summaryCard;
    }

    public void show(Route route, RouteWind wind, RouteWind reverse,
                     Settings settings, long durationSeconds) {
        map.clear();
        List<LatLng> all = new ArrayList<>();
        for (GeoPoint point : route.points) {
            all.add(new LatLng(point.lat, point.lng));
        }

        map.addPolyline(new PolylineOptions().addAll(all)
                .color(ContextCompat.getColor(activity, R.color.route_casing))
                .width(CASING_WIDTH_PX).jointType(JointType.ROUND)
                .startCap(new RoundCap()).endCap(new RoundCap()));

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

        map.addMarker(new MarkerOptions().position(all.get(0))
                .title(activity.getString(R.string.marker_start)));
        map.addMarker(new MarkerOptions().position(all.get(all.size() - 1))
                .title(activity.getString(R.string.marker_end)));

        fillSummary(route, wind, reverse, settings, durationSeconds);
        summaryCard.setVisibility(View.VISIBLE);
        fitRoute(all);
    }

    private void fillSummary(Route route, RouteWind wind, RouteWind reverse,
                             Settings settings, long durationSeconds) {
        UnitText units = new UnitText(activity, settings);
        ((TextView) activity.findViewById(R.id.headline)).setText(headlineFor(wind));

        String push = wind.averageHeadwindKmh > 0.5
                ? activity.getString(R.string.net_headwind,
                        units.windSpeed(Math.abs(wind.averageHeadwindKmh)))
                : wind.averageHeadwindKmh < -0.5
                        ? activity.getString(R.string.net_tailwind,
                                units.windSpeed(Math.abs(wind.averageHeadwindKmh)))
                        : activity.getString(R.string.net_neutral);
        ((TextView) activity.findViewById(R.id.details)).setText(activity.getString(
                R.string.details, units.distance(route.distanceMeters),
                formatDuration(durationSeconds), push, units.windSpeed(wind.maxGustKmh)));

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

    private void fitRoute(List<LatLng> points) {
        summaryCard.post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (LatLng point : points) {
                bounds.include(point);
            }
            map.setPadding(0, searchCard.getBottom(), 0,
                    root.getHeight() - summaryCard.getTop());
            int edge = Math.round(32 * activity.getResources().getDisplayMetrics().density);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), edge));
        });
    }

    private String headlineFor(RouteWind wind) {
        if (wind.share(WindEffect.HEADWIND) > 0.5) {
            return activity.getString(R.string.headline_headwind);
        }
        if (wind.share(WindEffect.TAILWIND) > 0.5) {
            return activity.getString(R.string.headline_tailwind);
        }
        if (wind.share(WindEffect.CROSSWIND) > 0.5) {
            return activity.getString(R.string.headline_crosswind);
        }
        if (wind.share(WindEffect.CALM) > 0.5) {
            return activity.getString(R.string.headline_calm);
        }
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
            case HEADWIND:
                return activity.getString(R.string.effect_headwind);
            case TAILWIND:
                return activity.getString(R.string.effect_tailwind);
            case CROSSWIND:
                return activity.getString(R.string.effect_crosswind);
            default:
                return activity.getString(R.string.effect_calm);
        }
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
}
