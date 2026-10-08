package io.github.jamerlybob.windroute.units;

import java.util.Locale;

import io.github.jamerlybob.windroute.settings.Settings;

/** Unit conversion and number formatting, kept independent of Android. */
public final class UnitFormatter {
    private static final double METERS_PER_MILE = 1609.344;
    private static final double KMH_PER_MPH = 1.609344;
    private static final double KMH_PER_KNOT = 1.852;

    private UnitFormatter() {
    }

    public static String distance(double meters, Settings.DistanceUnit unit,
                                  String localizedFormat, Locale locale) {
        double value = unit == Settings.DistanceUnit.MILES
                ? meters / METERS_PER_MILE : meters / 1000.0;
        return String.format(locale, localizedFormat, value);
    }

    public static String speed(double kmh, Settings.WindSpeedUnit unit,
                               String localizedFormat, Locale locale) {
        double value;
        switch (unit) {
            case MPH:
                value = kmh / KMH_PER_MPH;
                break;
            case METERS_PER_SECOND:
                value = kmh / 3.6;
                break;
            case KNOTS:
                value = kmh / KMH_PER_KNOT;
                break;
            default:
                value = kmh;
        }
        return String.format(locale, localizedFormat, value);
    }

    /** Returns Google's duration unless the rider has supplied a usual speed. */
    public static long ridingDurationSeconds(double distanceMeters, long googleSeconds,
                                             int ridingSpeedKmh) {
        if (ridingSpeedKmh <= 0) {
            return googleSeconds;
        }
        return Math.round(distanceMeters / 1000.0 / ridingSpeedKmh * 3600.0);
    }
}
