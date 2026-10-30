package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import java.util.List;
import io.github.jamerlybob.windroute.poi.PoiAlongRoute;
import io.github.jamerlybob.windroute.gpx.GpxParser;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;

/** Shared place card and reversible export selection for both route screens. */
final class PlaceDetailsController {
    private PlaceDetailsController() { }
    static void show(Activity activity, GoogleMap map, PoiAlongRoute place, String name,
                     String kind, Settings settings, List<GpxParser.Waypoint> waypoints,
                     Runnable save) {
        if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(place.poi.position.lat, place.poi.position.lng), Math.max(14, map.getCameraPosition().zoom)));
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad,pad,pad,pad);
        TextView title = new TextView(activity);
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        title.setText(name); body.addView(title);
        UnitText units = new UnitText(activity, settings);
        String off = place.distanceOffRouteMeters < 1000
                ? activity.getString(R.string.height_metres, place.distanceOffRouteMeters)
                : units.distance(place.distanceOffRouteMeters);
        TextView facts = new TextView(activity);
        String distances = activity.getString(R.string.place_facts, kind,
                units.distance(place.distanceAlongRouteMeters), off);
        facts.setText(place.poi.openingHours == null || place.poi.openingHours.isEmpty() ? distances
                : activity.getString(R.string.place_hours_details, distances, place.poi.openingHours));
        body.addView(facts);
        MaterialButton toggle = new MaterialButton(activity);
        boolean selected = false;
        for (GpxParser.Waypoint point : waypoints) if (GeoMath.distanceMeters(point.point, place.poi.position) < 1) selected = true;
        final boolean remove = selected;
        toggle.setText(remove ? R.string.remove_export : R.string.add_to_export);
        toggle.setOnClickListener(v -> {
            if (remove) waypoints.removeIf(point -> GeoMath.distanceMeters(point.point, place.poi.position) < 1);
            else waypoints.add(new GpxParser.Waypoint(place.poi.position, name, null));
            save.run(); dialog.dismiss();
        });
        body.addView(toggle); dialog.setContentView(body); dialog.show();
    }
}
