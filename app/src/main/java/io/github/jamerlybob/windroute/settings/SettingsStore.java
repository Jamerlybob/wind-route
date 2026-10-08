package io.github.jamerlybob.windroute.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Loads, validates and saves settings using Android's small key-value store.
 * Centralising both persistence and theme mapping keeps Android details out of
 * the immutable Settings model and protects callers from damaged old values.
 */
public final class SettingsStore {
    public static final String PREFERENCES_NAME = "windroute";

    private static final String DISTANCE = "settings_distance";
    private static final String WIND_SPEED = "settings_wind_speed";
    private static final String RIDING_SPEED = "settings_riding_speed";
    private static final String THEME = "settings_theme";
    private static final String CALM_BELOW = "settings_calm_below";
    private static final String TEMPERATURE = "settings_temperature";
    private static final String ELEVATION = "settings_elevation";
    private static final String CUE_TURNS = "settings_cue_turns";
    private static final String CUE_CLIMBS = "settings_cue_climbs";
    private static final String CUE_WIND = "settings_cue_wind";
    private static final String CUE_GUSTS = "settings_cue_gusts";
    private static final String CUE_AHEAD = "settings_cue_ahead";
    private static final String LESS_TALK = "settings_less_talk";

    private SettingsStore() {
    }

    public static Settings load(Context context) {
        // SharedPreferences is appropriate for a handful of primitive values;
        // MODE_PRIVATE keeps them inside this app's sandbox.
        SharedPreferences values = context.getSharedPreferences(PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        Settings defaults = Settings.defaults();
        Settings.DistanceUnit distance = readDistanceUnit(
                values.getString(DISTANCE, defaults.distanceUnit.name()),
                defaults.distanceUnit);
        Settings.WindSpeedUnit wind = readWindSpeedUnit(
                values.getString(WIND_SPEED, defaults.windSpeedUnit.name()),
                defaults.windSpeedUnit);
        Settings.Theme theme = readTheme(values.getString(THEME, defaults.theme.name()),
                defaults.theme);
        int riding = values.getInt(RIDING_SPEED, defaults.ridingSpeedKmh);
        if (riding != Settings.USE_GOOGLE_RIDING_SPEED && (riding < 10 || riding > 40)) {
            riding = defaults.ridingSpeedKmh;
        }
        int calm = values.getInt(CALM_BELOW, defaults.calmBelowKmh);
        if (calm < 0 || calm > 15) {
            calm = defaults.calmBelowKmh;
        }
        Settings.TemperatureUnit temperature = readTemperatureUnit(
                values.getString(TEMPERATURE, defaults.temperatureUnit.name()),
                defaults.temperatureUnit);
        Settings.ElevationUnit elevation = readElevationUnit(
                values.getString(ELEVATION, defaults.elevationUnit.name()),
                defaults.elevationUnit);
        int ahead = values.getInt(CUE_AHEAD, 300);
        if (ahead != 200 && ahead != 300 && ahead != 400) ahead = 300;
        return new Settings(distance, wind, riding, theme, calm, temperature, elevation,
                values.getBoolean(CUE_TURNS, true), values.getBoolean(CUE_CLIMBS, true),
                values.getBoolean(CUE_WIND, true), values.getBoolean(CUE_GUSTS, true),
                ahead, values.getBoolean(LESS_TALK, false));
    }

    public static void save(Context context, Settings settings) {
        // apply() persists asynchronously so a spinner tap never blocks drawing.
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(DISTANCE, settings.distanceUnit.name())
                .putString(WIND_SPEED, settings.windSpeedUnit.name())
                .putInt(RIDING_SPEED, settings.ridingSpeedKmh)
                .putString(THEME, settings.theme.name())
                .putInt(CALM_BELOW, settings.calmBelowKmh)
                .putString(TEMPERATURE, settings.temperatureUnit.name())
                .putString(ELEVATION, settings.elevationUnit.name())
                .putBoolean(CUE_TURNS, settings.cueTurns)
                .putBoolean(CUE_CLIMBS, settings.cueClimbs)
                .putBoolean(CUE_WIND, settings.cueWind)
                .putBoolean(CUE_GUSTS, settings.cueGusts)
                .putInt(CUE_AHEAD, settings.cueAheadMeters)
                .putBoolean(LESS_TALK, settings.lessTalk)
                .apply();
    }

    public static void applyTheme(Settings settings) {
        int mode;
        switch (settings.theme) {
            case LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static Settings.DistanceUnit readDistanceUnit(
            String value, Settings.DistanceUnit fallback) {
        try {
            return Settings.DistanceUnit.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static Settings.WindSpeedUnit readWindSpeedUnit(
            String value, Settings.WindSpeedUnit fallback) {
        try {
            return Settings.WindSpeedUnit.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static Settings.Theme readTheme(String value, Settings.Theme fallback) {
        try {
            return Settings.Theme.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static Settings.TemperatureUnit readTemperatureUnit(
            String value, Settings.TemperatureUnit fallback) {
        try {
            return Settings.TemperatureUnit.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static Settings.ElevationUnit readElevationUnit(
            String value, Settings.ElevationUnit fallback) {
        try {
            return Settings.ElevationUnit.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
