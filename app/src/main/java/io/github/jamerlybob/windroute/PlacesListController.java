package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.List;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import io.github.jamerlybob.windroute.poi.PoiKind;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;

/** Compact grouped rows, without hundreds of competing primary buttons. */
final class PlacesListController {
    interface Listener { void selected(PoiAlongRoute place, String name, String kind); }
    private PlacesListController() { }

    static void append(Activity activity, LinearLayout list, List<PoiAlongRoute> places,
                       Settings settings, Listener listener) {
        UnitText units = new UnitText(activity, settings);
        for (PoiKind kind : PoiKind.values()) {
            List<PoiAlongRoute> group = new ArrayList<>();
            for (PoiAlongRoute place : places) if (place.poi.kind == kind) group.add(place);
            if (group.isEmpty()) continue;
            String label = activity.getString(kindText(kind));
            TextView heading = text(activity);
            heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            heading.setText(activity.getString(R.string.places_heading, label, group.size()));
            list.addView(heading);
            LinearLayout rows = new LinearLayout(activity);
            rows.setOrientation(LinearLayout.VERTICAL);
            list.addView(rows);
            // Build hidden rows only after the rider asks. A large lookup stays
            // cheap to lay out, and each section remains independently expanded.
            for (int i = 0; i < Math.min(5, group.size()); i++) addRow(
                    activity, rows, group.get(i), label, units, listener);
            if (group.size() > 5) {
                TextView more = text(activity);
                more.setText(activity.getString(R.string.places_show_all, group.size()));
                rows.addView(more);
                clickable(activity, more);
                more.setOnClickListener(v -> {
                    rows.removeView(more);
                    for (int i = 5; i < group.size(); i++) addRow(
                            activity, rows, group.get(i), label, units, listener);
                });
            }
        }
    }

    private static void addRow(Activity activity, LinearLayout list, PoiAlongRoute place,
                               String kind, UnitText units, Listener listener) {
        String name = place.poi.name == null || place.poi.name.isEmpty()
                ? activity.getString(R.string.place_unnamed, kind) : place.poi.name;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(activity);
        title.setText(name);
        TextView facts = text(activity);
        facts.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        // Off-route distances need metre precision, not a misleading "0.0 km".
        String off = place.distanceOffRouteMeters < 1000
                ? activity.getString(R.string.height_metres, place.distanceOffRouteMeters)
                : units.distance(place.distanceOffRouteMeters);
        facts.setText(activity.getString(R.string.place_facts, kind,
                units.distance(place.distanceAlongRouteMeters), off));
        row.addView(title);
        row.addView(facts);
        clickable(activity, row);
        row.setOnClickListener(v -> listener.selected(place, name, kind));
        list.addView(row);
    }

    private static TextView text(Activity activity) {
        TextView text = new TextView(activity);
        text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        int padding = Math.round(8 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        return text;
    }

    private static void clickable(Activity activity, View row) {
        TypedValue background = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        row.setBackgroundResource(background.resourceId);
        row.setMinimumHeight(Math.round(48 * activity.getResources().getDisplayMetrics().density));
        row.setFocusable(true);
    }

    private static int kindText(PoiKind kind) {
        switch (kind) {
            case WATER: return R.string.poi_water;
            case FOOD: return R.string.poi_food;
            case CAMPING: return R.string.poi_camping;
            case BIKE_SHOP: return R.string.poi_bike_shop;
            case TOILETS: return R.string.poi_toilets;
            default: return R.string.poi_shelter;
        }
    }
}
