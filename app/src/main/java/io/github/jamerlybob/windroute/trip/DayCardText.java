package io.github.jamerlybob.windroute.trip;

import java.util.Locale;

/** Keep the one-line summary separate from details so distance is not repeated. */
public final class DayCardText {
    private DayCardText() { }

    public static String summary(String template, String distance, String ascent, String weather) {
        return String.format(Locale.getDefault(),template,distance,ascent,weather);
    }

    public static String details(String template, String wind, String cost, String temperature,
                                 String sunrise, String sunset, String warning) {
        return String.format(Locale.getDefault(),template,wind,cost,temperature,sunrise,sunset,warning);
    }
}
