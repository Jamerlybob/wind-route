package io.github.jamerlybob.windroute;

import android.app.Application;

import io.github.jamerlybob.windroute.settings.SettingsStore;

/** Applies the saved light/dark choice before any Activity draws its views. */
public final class WindRouteApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SettingsStore.applyTheme(SettingsStore.load(this));
    }
}
