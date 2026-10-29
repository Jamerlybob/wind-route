package io.github.jamerlybob.windroute.trip;

import io.github.jamerlybob.windroute.wind.*;

/** Presentation decisions; translated sentences are supplied by the screen. */
public final class DayVerdict {
    private DayVerdict() { }
    public static String sentence(String template, String distance, String wind, String rain) {
        return String.format(java.util.Locale.getDefault(),template,distance,wind,rain);
    }
    public static WindEffect dominant(RouteWind wind) {
        WindEffect best = WindEffect.CALM;
        for (WindEffect effect : WindEffect.values()) if (wind.share(effect)>wind.share(best)) best=effect;
        return best;
    }
    public static boolean bad(double headwind, double rainMm) { return headwind >= 20 || rainMm >= 2; }
    public static boolean suggestDrier(double today, double tomorrow) { return today >= 2 && tomorrow <= today / 2; }
    public static boolean suggestLater(double today, double tomorrow) { return today >= 15 && today-tomorrow >= 5; }
}
