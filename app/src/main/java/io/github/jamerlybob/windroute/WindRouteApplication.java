package io.github.jamerlybob.windroute;

import android.app.Application;

import io.github.jamerlybob.windroute.settings.SettingsStore;

/**
 * Applies the saved light/dark choice before any Activity draws its views.
 * Application is the one process-wide starting point early enough to prevent
 * a screen briefly appearing in the wrong theme.
 */
public final class WindRouteApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SettingsStore.applyTheme(SettingsStore.load(this));
    }
}
