package io.github.jamerlybob.windroute.units;

import android.content.Context;

import java.util.Locale;

import io.github.jamerlybob.windroute.R;
import io.github.jamerlybob.windroute.settings.Settings;

/**
 * Connects the pure unit converter to localized Android string resources.
 * This small boundary prevents Context from leaking into calculation code.
 */
public final class UnitText {
    private final Context context;
    private final Settings settings;

    public UnitText(Context context, Settings settings) {
        this.context = context;
        this.settings = settings;
    }

    public String distance(double meters) {
        int format = settings.distanceUnit == Settings.DistanceUnit.MILES
                ? R.string.distance_miles : R.string.distance_km;
        return UnitFormatter.distance(meters, settings.distanceUnit,
                context.getString(format), Locale.getDefault());
    }

    public String windSpeed(double kmh) {
        return speed(kmh, settings.windSpeedUnit);
    }

    public String speed(double kmh, Settings.WindSpeedUnit unit) {
        int format;
        switch (unit) {
            case MPH:
                format = R.string.speed_mph;
                break;
            case METERS_PER_SECOND:
                format = R.string.speed_meters_per_second;
                break;
            case KNOTS:
                format = R.string.speed_knots;
                break;
            default:
                format = R.string.speed_kmh;
        }
        return UnitFormatter.speed(kmh, unit, context.getString(format), Locale.getDefault());
    }
}
