package io.github.jamerlybob.windroute.settings;

import java.util.Objects;

/**
 * One immutable snapshot of every user choice. Keeping this model free of
 * Android makes defaults, equality and calculations straightforward to test.
 */
public final class Settings {
    public enum DistanceUnit { KILOMETERS, MILES }
    public enum WindSpeedUnit { KMH, MPH, METERS_PER_SECOND, KNOTS }
    public enum TemperatureUnit { CELSIUS, FAHRENHEIT }
    public enum ElevationUnit { METERS, FEET }
    public enum Theme { SYSTEM, LIGHT, DARK }

    public static final int USE_GOOGLE_RIDING_SPEED = 0;

    public final DistanceUnit distanceUnit;
    public final WindSpeedUnit windSpeedUnit;
    /** Zero means use Google's estimate; otherwise this is kilometres per hour. */
    public final int ridingSpeedKmh;
    public final Theme theme;
    public final int calmBelowKmh;
    public final TemperatureUnit temperatureUnit;
    public final ElevationUnit elevationUnit;

    public Settings(DistanceUnit distanceUnit, WindSpeedUnit windSpeedUnit,
                    int ridingSpeedKmh, Theme theme, int calmBelowKmh) {
        this(distanceUnit, windSpeedUnit, ridingSpeedKmh, theme, calmBelowKmh,
                TemperatureUnit.CELSIUS, ElevationUnit.METERS);
    }

    public Settings(DistanceUnit distanceUnit, WindSpeedUnit windSpeedUnit,
                    int ridingSpeedKmh, Theme theme, int calmBelowKmh,
                    TemperatureUnit temperatureUnit, ElevationUnit elevationUnit) {
        this.distanceUnit = distanceUnit;
        this.windSpeedUnit = windSpeedUnit;
        this.ridingSpeedKmh = ridingSpeedKmh;
        this.theme = theme;
        this.calmBelowKmh = calmBelowKmh;
        this.temperatureUnit = temperatureUnit;
        this.elevationUnit = elevationUnit;
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
                && calmBelowKmh == that.calmBelowKmh
                && temperatureUnit == that.temperatureUnit
                && elevationUnit == that.elevationUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(distanceUnit, windSpeedUnit, ridingSpeedKmh, theme, calmBelowKmh,
                temperatureUnit, elevationUnit);
    }
}
