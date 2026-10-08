package io.github.jamerlybob.windroute;

import android.content.Context;
import io.github.jamerlybob.windroute.nav.DurationFormatter;

/** Resource wording shared by the route sheet and trip screen. */
final class WindCostText {
    private WindCostText() { }

    static String format(Context context, double minutes, boolean compact) {
        long rounded = Math.round(Math.abs(minutes));
        if (rounded < 1) return context.getString(compact
                ? R.string.wind_no_difference_short : R.string.wind_no_difference);
        if (!compact && rounded < 60) return context.getResources().getQuantityString(
                minutes > 0 ? R.plurals.wind_adds : R.plurals.wind_saves, (int) rounded, rounded);
        String duration = DurationFormatter.format(minutes, context.getString(R.string.duration_min),
                context.getString(R.string.duration_h_min));
        return context.getString(compact ? (minutes > 0 ? R.string.wind_adds_duration_short
                : R.string.wind_saves_duration_short) : (minutes > 0 ? R.string.wind_adds_duration
                : R.string.wind_saves_duration), duration);
    }
}
