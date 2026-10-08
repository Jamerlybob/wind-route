package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Expands and collapses the route form without involving route loading. */
public final class SearchCardController {
    private final Activity activity;
    private final View expanded;
    private final View collapsed;
    private boolean hasRoute;

    public SearchCardController(Activity activity) {
        this.activity = activity;
        expanded = activity.findViewById(R.id.search_expanded);
        collapsed = activity.findViewById(R.id.search_collapsed);
        collapsed.setOnClickListener(v -> expand());
    }

    public void showRoute(String origin, String destination, long departureEpochSeconds) {
        hasRoute = true;
        String time;
        if (departureEpochSeconds <= System.currentTimeMillis() / 1000 + 60) {
            time = activity.getString(R.string.depart_now);
        } else {
            String pattern = android.text.format.DateFormat.is24HourFormat(activity)
                    ? "EEE HH:mm" : "EEE h:mm a";
            time = new SimpleDateFormat(pattern, Locale.getDefault())
                    .format(new Date(departureEpochSeconds * 1000));
        }
        ((TextView) activity.findViewById(R.id.search_collapsed_text)).setText(
                activity.getString(R.string.search_collapsed,
                        shortPlace(origin), shortPlace(destination), time));
        collapse();
    }

    public void expand() {
        expanded.setVisibility(View.VISIBLE);
        collapsed.setVisibility(View.GONE);
    }

    public void collapse() {
        // Before the first route there is no compact sentence to show, so the
        // form must remain expanded even if a caller asks it to collapse.
        if (!hasRoute) {
            return;
        }
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
    }

    public boolean collapseForBack() {
        if (hasRoute && expanded.getVisibility() == View.VISIBLE) {
            collapse();
            return true;
        }
        return false;
    }

    private static String shortPlace(String place) {
        // Geocoder results put the recognisable place first. Keeping that part
        // lets both endpoints and the departure fit on one compact line.
        int comma = place.indexOf(',');
        return comma > 0 ? place.substring(0, comma).trim() : place;
    }
}
