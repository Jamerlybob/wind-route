package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ClimbDetector;
import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.weather.RideWeather;
import io.github.jamerlybob.windroute.wind.DepartureScorer;
import io.github.jamerlybob.windroute.wind.GustWarnings;

/** Renders the expandable details sheet from an already-computed snapshot. */
public final class RideSheetController {
    private final Activity activity;
    private final View sheet;
    private final BottomSheetBehavior<View> behavior;
    private final DepartureStripView strip;
    private final ElevationProfileView profileView;

    public RideSheetController(Activity activity) {
        this.activity = activity;
        sheet = activity.findViewById(R.id.summary_card);
        behavior = BottomSheetBehavior.from(sheet);
        behavior.setHideable(false);
        behavior.setFitToContents(false);
        // Only a floor for the moment before the summary has been measured.
        // The real peek height is the summary's own height, set below; a large
        // floor here would leave an empty band under the cycling notice.
        int minimumPeek = Math.round(160
                * activity.getResources().getDisplayMetrics().density);
        behavior.setPeekHeight(minimumPeek, false);
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        strip = activity.findViewById(R.id.departure_strip);
        profileView = activity.findViewById(R.id.elevation_profile);
        activity.findViewById(R.id.wind_cost).setOnClickListener(v ->
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.wind_cost_info)
                        .setMessage(R.string.wind_cost_assumptions)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
        LinearLayout collapsed = activity.findViewById(R.id.summary_collapsed);
        collapsed.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            // BottomSheetBehavior may measure this container with only the old
            // peek height available. Summing its measured children avoids that
            // circular constraint and keeps every visible summary row on-screen.
            int contentHeight = collapsed.getPaddingTop() + collapsed.getPaddingBottom();
            for (int i = 0; i < collapsed.getChildCount(); i++) {
                View child = collapsed.getChildAt(i);
                if (child.getVisibility() == View.GONE) continue;
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        child.getLayoutParams();
                contentHeight += child.getMeasuredHeight()
                        + params.topMargin + params.bottomMargin;
            }
            int measuredPeek = Math.max(minimumPeek, contentHeight);
            if (behavior.getPeekHeight() != measuredPeek) {
                // Material ignores an offset change made while it is laying
                // out the sheet. The next frame is early enough to look
                // immediate but late enough for the new offset to take hold.
                collapsed.post(() -> behavior.setPeekHeight(measuredPeek, false));
            }
        });
    }

    public void show(RideAnalysis analysis, Settings settings,
                     DepartureStripView.Listener departureListener,
                     boolean elevationLoading, boolean elevationFailed) {
        // Rebuild every section from one immutable snapshot; views never make
        // independent timing or weather decisions that could disagree.
        UnitText units = new UnitText(activity, settings);
        fillBestTime(analysis, units, departureListener);
        fillWeather(analysis.weather, settings);
        String cost = windCost(analysis.power.differenceMinutes);
        ((TextView) activity.findViewById(R.id.wind_cost)).setText(cost);
        fillGusts(analysis.gustWarnings, units);
        fillHills(analysis, settings, units, elevationLoading, elevationFailed);
        sheet.setVisibility(View.VISIBLE);
    }

    public String windCost(double minutes) {
        long rounded = Math.round(Math.abs(minutes));
        if (rounded < 1) {
            return activity.getString(R.string.wind_no_difference);
        }
        return activity.getResources().getQuantityString(minutes > 0
                ? R.plurals.wind_adds : R.plurals.wind_saves, (int) rounded, rounded);
    }

    /** Compact wording keeps the collapsed wind facts on one scan line. */
    public String shortWindCost(double minutes) {
        long rounded = Math.round(Math.abs(minutes));
        if (rounded < 1) return activity.getString(R.string.wind_no_difference_short);
        return activity.getString(minutes > 0 ? R.string.wind_adds_short
                : R.string.wind_saves_short, rounded);
    }

    public String extraDetails(RideAnalysis analysis, Settings settings) {
        List<String> details = new ArrayList<>();
        details.add(windCost(analysis.power.differenceMinutes));
        if (!Double.isNaN(analysis.weather.chanceOfAnyRainPercent)
                && analysis.weather.chanceOfAnyRainPercent >= 30) {
            details.add(activity.getString(R.string.rain_chance,
                    analysis.weather.chanceOfAnyRainPercent));
        }
        if (analysis.elevation != null) {
            double ascent = UnitFormatter.elevation(analysis.elevation.totalAscentMeters,
                    settings.elevationUnit);
            details.add(activity.getString(settings.elevationUnit == Settings.ElevationUnit.FEET
                    ? R.string.ascent_feet : R.string.ascent_metres, ascent));
            int intoWind = 0;
            for (ClimbWind climbWind : analysis.climbWinds) {
                if (climbWind.isClimbIntoHeadwind) {
                    intoWind++;
                }
            }
            if (intoWind > 0) {
                details.add(activity.getResources().getQuantityString(
                        R.plurals.climbs_into_wind, intoWind, intoWind));
            }
        }
        return android.text.TextUtils.join(" · ", details);
    }

    public boolean collapseForBack() {
        if (behavior.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
            behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return true;
        }
        return false;
    }

    public int collapsedHeight() {
        return behavior.getPeekHeight();
    }

    /** Keeps the fully expanded sheet below edge-to-edge system chrome. */
    public void setExpandedOffset(int statusBarInset) {
        behavior.setExpandedOffset(statusBarInset);
    }

    private void fillBestTime(RideAnalysis analysis, UnitText units,
                              DepartureStripView.Listener listener) {
        if (analysis.departures.isEmpty()) {
            strip.setVisibility(View.GONE);
            activity.findViewById(R.id.best_summary).setVisibility(View.GONE);
            return;
        }
        strip.setVisibility(View.VISIBLE);
        TextView summary = activity.findViewById(R.id.best_summary);
        summary.setVisibility(View.VISIBLE);
        DepartureScorer.Entry best = analysis.departures.get(0);
        for (DepartureScorer.Entry entry : analysis.departures) {
            if (entry.score < best.score) {
                best = entry;
            }
        }
        String wind = best.averageHeadwindKmh >= 0
                ? activity.getString(R.string.net_headwind,
                        units.windSpeed(Math.abs(best.averageHeadwindKmh)))
                : activity.getString(R.string.net_tailwind,
                        units.windSpeed(Math.abs(best.averageHeadwindKmh)));
        String wet = best.hasRainData && ((!Double.isNaN(best.wettestRainMm)
                && best.wettestRainMm > 0) || (!Double.isNaN(best.rainChancePercent)
                && best.rainChancePercent >= 30))
                ? activity.getString(R.string.rain_possible) : activity.getString(R.string.dry);
        String text = activity.getString(R.string.best_departure, time(best.departureEpochSeconds),
                wind, wet);
        summary.setText(text);
        strip.setEntries(analysis.departures, nearestDeparture(analysis), listener, text);
    }

    private long nearestDeparture(RideAnalysis analysis) {
        long nearest = analysis.departures.get(0).departureEpochSeconds;
        long gap = Long.MAX_VALUE;
        for (DepartureScorer.Entry entry : analysis.departures) {
            long candidate = Math.abs(entry.departureEpochSeconds - analysis.departureEpochSeconds);
            if (candidate < gap) {
                gap = candidate;
                nearest = entry.departureEpochSeconds;
            }
        }
        return nearest;
    }

    private void fillWeather(RideWeather weather, Settings settings) {
        TextView view = activity.findViewById(R.id.weather_summary);
        StringBuilder text = new StringBuilder();
        if (Double.isNaN(weather.coldestC) || Double.isNaN(weather.warmestC)) {
            text.append(activity.getString(R.string.weather_unknown_temperature));
        } else {
            double low = UnitFormatter.temperature(weather.coldestC, settings.temperatureUnit);
            double high = UnitFormatter.temperature(weather.warmestC, settings.temperatureUnit);
            text.append(TemperatureRangeFormatter.format(low, high,
                    activity.getString(R.string.weather_temperature_single),
                    activity.getString(R.string.weather_temperature)));
        }
        // Unknown rain is intentionally omitted. A missing forecast field must
        // not be presented to the rider as a confident dry prediction.
        boolean chance = !Double.isNaN(weather.chanceOfAnyRainPercent);
        boolean amount = !Double.isNaN(weather.wettestRainMm);
        boolean dry = chance && amount && weather.chanceOfAnyRainPercent < 1
                && weather.wettestRainMm <= 0;
        // The space between sentences is added here, not in strings.xml:
        // Android strips leading spaces from string resources.
        if (dry) {
            text.append(' ').append(activity.getString(R.string.weather_dry));
        } else if (chance && amount) {
            text.append(' ').append(activity.getString(R.string.weather_rain,
                    weather.chanceOfAnyRainPercent, weather.wettestRainMm,
                    time(weather.wettestHourEpochSeconds)));
        } else if (chance) {
            text.append(' ').append(activity.getString(R.string.weather_rain_chance_only,
                    weather.chanceOfAnyRainPercent));
        } else if (amount) {
            text.append(' ').append(activity.getString(R.string.weather_rain_amount_only,
                    weather.wettestRainMm, time(weather.wettestHourEpochSeconds)));
        }
        view.setText(text.toString());
    }

    private void fillGusts(List<GustWarnings.Warning> warnings, UnitText units) {
        View section = activity.findViewById(R.id.gust_section);
        LinearLayout list = activity.findViewById(R.id.gust_list);
        list.removeAllViews();
        section.setVisibility(warnings.isEmpty() ? View.GONE : View.VISIBLE);
        for (GustWarnings.Warning warning : warnings) {
            TextView line = lineView();
            line.setText(activity.getString(R.string.gust_warning,
                    units.windSpeed(warning.maxGustKmh),
                    activity.getString(warning.side == GustWarnings.Side.LEFT
                            ? R.string.side_left : R.string.side_right),
                    units.distance(warning.lengthMeters), units.distance(warning.startMeters)));
            list.addView(line);
        }
    }

    private void fillHills(RideAnalysis analysis, Settings settings, UnitText units,
                           boolean loading, boolean failed) {
        TextView status = activity.findViewById(R.id.hills_status);
        LinearLayout list = activity.findViewById(R.id.climbs_list);
        list.removeAllViews();
        if (analysis.elevation == null) {
            profileView.setVisibility(View.GONE);
            status.setText(loading ? R.string.hills_loading : R.string.hills_unavailable);
            return;
        }
        profileView.setVisibility(View.VISIBLE);
        profileView.setData(analysis.elevation, analysis.wind, analysis.climbs,
                analysis.climbWinds, settings);
        status.setText(analysis.climbs.isEmpty()
                ? activity.getString(R.string.hills_none) : "");
        for (int i = 0; i < analysis.climbs.size(); i++) {
            ClimbDetector.Climb climb = analysis.climbs.get(i);
            ClimbWind climbWind = analysis.climbWinds.get(i);
            double gain = UnitFormatter.elevation(climb.gainMeters, settings.elevationUnit);
            String gainText = activity.getString(settings.elevationUnit == Settings.ElevationUnit.FEET
                    ? R.string.height_feet : R.string.height_metres, gain);
            String text = activity.getString(R.string.climb_line, i + 1,
                    units.distance(climb.startDistanceMeters), units.distance(climb.lengthMeters),
                    gainText, climb.averageGradientPercent, climb.steepest100mGradientPercent);
            if (climbWind.isClimbIntoHeadwind) {
                text += activity.getString(R.string.climb_headwind,
                        units.windSpeed(climbWind.averageHeadwindKmh));
            }
            TextView line = lineView();
            line.setText(text);
            if (climbWind.isClimbIntoHeadwind) {
                line.setTextColor(ContextCompat.getColor(activity, R.color.wind_headwind));
                line.setTypeface(line.getTypeface(), Typeface.BOLD);
            }
            list.addView(line);
        }
    }

    private TextView lineView() {
        TextView view = new TextView(activity);
        int pad = Math.round(4 * activity.getResources().getDisplayMetrics().density);
        view.setPadding(0, pad, 0, pad);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        return view;
    }

    private String time(long epochSeconds) {
        return DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(epochSeconds * 1000));
    }
}
