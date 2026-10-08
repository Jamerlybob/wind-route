package io.github.jamerlybob.windroute.weather;

/** Sunrise and sunset for one forecast day, as UTC epoch seconds. */
public final class DaylightForecast {
    public final long dayEpochSeconds;
    public final long sunriseEpochSeconds;
    public final long sunsetEpochSeconds;

    public DaylightForecast(long day, long sunrise, long sunset) {
        this.dayEpochSeconds = day;
        this.sunriseEpochSeconds = sunrise;
        this.sunsetEpochSeconds = sunset;
    }
}
