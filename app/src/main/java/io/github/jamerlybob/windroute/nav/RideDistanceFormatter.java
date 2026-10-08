package io.github.jamerlybob.windroute.nav;

import java.util.Locale;
import io.github.jamerlybob.windroute.settings.Settings;

/** Shared rounding for speech and screen; formats differ only in suffixes. */
public final class RideDistanceFormatter {
    private RideDistanceFormatter() { }

    /** Formats are metres, miles, feet and kilometres, in that order. */
    public static String format(double meters, Settings.DistanceUnit unit, String[] formats) {
        meters = Math.max(0, meters);
        if (unit == Settings.DistanceUnit.MILES) {
            double miles = meters / 1609.344;
            if (miles >= 0.2) return String.format(Locale.getDefault(), formats[1], miles);
            return String.format(Locale.getDefault(), formats[2], rounded(meters / 0.3048));
        }
        if (meters < 1000) return String.format(Locale.getDefault(), formats[0], rounded(meters));
        return String.format(Locale.getDefault(), formats[3], meters / 1000);
    }

    private static int rounded(double value) {
        // Fifty-unit increments are easy to hear. Only arrival may show zero.
        return value == 0 ? 0 : Math.max(50, (int) Math.round(value / 50) * 50);
    }
}
