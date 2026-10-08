package io.github.jamerlybob.windroute.settings;

import java.util.Objects;

/** All user choices, with no Android dependency so calculations can be tested. */
public final class Settings {
    public enum DistanceUnit { KILOMETERS, MILES }
    public enum WindSpeedUnit { KMH, MPH, METERS_PER_SECOND, KNOTS }
    public enum Theme { SYSTEM, LIGHT, DARK }

    public static final int USE_GOOGLE_RIDING_SPEED = 0;

    public final DistanceUnit distanceUnit;
    public final WindSpeedUnit windSpeedUnit;
    /** Zero means use Google's estimate; otherwise this is kilometres per hour. */
    public final int ridingSpeedKmh;
    public final Theme theme;
    public final int calmBelowKmh;

    public Settings(DistanceUnit distanceUnit, WindSpeedUnit windSpeedUnit,
                    int ridingSpeedKmh, Theme theme, int calmBelowKmh) {
        this.distanceUnit = distanceUnit;
        this.windSpeedUnit = windSpeedUnit;
        this.ridingSpeedKmh = ridingSpeedKmh;
        this.theme = theme;
        this.calmBelowKmh = calmBelowKmh;
    }

    public static Settings defaults() {
        return new Settings(DistanceUnit.KILOMETERS, WindSpeedUnit.KMH,
                USE_GOOGLE_RIDING_SPEED, Theme.SYSTEM, 5);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Settings)) {
            return false;
        }
        Settings that = (Settings) other;
        return distanceUnit == that.distanceUnit
                && windSpeedUnit == that.windSpeedUnit
                && ridingSpeedKmh == that.ridingSpeedKmh
                && theme == that.theme
                && calmBelowKmh == that.calmBelowKmh;
    }

    @Override
    public int hashCode() {
        return Objects.hash(distanceUnit, windSpeedUnit, ridingSpeedKmh, theme, calmBelowKmh);
    }
}
