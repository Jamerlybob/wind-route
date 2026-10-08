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
        return new Settings(distance, wind, riding, theme, calm);
    }

    public static void save(Context context, Settings settings) {
        // apply() persists asynchronously so a spinner tap never blocks drawing.
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(DISTANCE, settings.distanceUnit.name())
                .putString(WIND_SPEED, settings.windSpeedUnit.name())
                .putInt(RIDING_SPEED, settings.ridingSpeedKmh)
                .putString(THEME, settings.theme.name())
                .putInt(CALM_BELOW, settings.calmBelowKmh)
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
}
