package io.github.jamerlybob.windroute;

import java.util.Locale;

/** Formats a rounded temperature range without repeating an identical value. */
public final class TemperatureRangeFormatter {
    private TemperatureRangeFormatter() {
    }

    public static String format(double low, double high, String single, String range) {
        long roundedLow = Math.round(low);
        long roundedHigh = Math.round(high);
        if (roundedLow == roundedHigh) {
            return String.format(Locale.getDefault(), single, roundedLow);
        }
        return String.format(Locale.getDefault(), range, low, high);
    }
}
