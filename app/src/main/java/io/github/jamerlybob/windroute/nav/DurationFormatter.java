package io.github.jamerlybob.windroute.nav;

/** Converts rounded time differences without leaving long rides in minutes. */
public final class DurationFormatter {
    private DurationFormatter() { }

    public static String format(double minutes, String minuteFormat, String hourFormat) {
        long total = Math.round(Math.abs(minutes));
        return total >= 60 ? String.format(hourFormat, total / 60, total % 60)
                : String.format(minuteFormat, total);
    }
}
