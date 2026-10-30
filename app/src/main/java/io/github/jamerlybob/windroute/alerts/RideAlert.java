package io.github.jamerlybob.windroute.alerts;

import java.util.*;
import io.github.jamerlybob.windroute.wind.DepartureScorer;

/** Conservative, inspectable rules: unknown rain is never advertised as dry. */
public final class RideAlert {
    public enum Decision { GOOD, BAD, NONE }
    private RideAlert() { }
    public static Decision decide(DepartureScorer.Entry entry, boolean includeBad) {
        return decide(entry.averageHeadwindKmh, entry.maxGustKmh,
                entry.wettestRainMm, entry.rainChancePercent, includeBad);
    }
    public static Decision decide(double headwind, double gust, double rain, double chance, boolean includeBad) {
        if (includeBad && (headwind >= 20 || rain >= 2 || chance >= 60)) return Decision.BAD;
        if (Double.isFinite(headwind) && Double.isFinite(gust) && Double.isFinite(rain)
                && Double.isFinite(chance) && headwind <= 5 && gust < 45 && rain < .1 && chance < 20)
            return Decision.GOOD;
        return Decision.NONE;
    }
    /** Resource templates and already-localized facts keep this Android-free. */
    public static String sentence(String template, String time, String conditions, String temperature,
                                  String temperatureTemplate) {
        return String.format(Locale.getDefault(), template, time, conditions)
                + (temperature == null || temperature.isEmpty() ? ""
                : String.format(Locale.getDefault(), temperatureTemplate, temperature));
    }
    public static long tomorrow(long nowMillis, int hour, int minute, TimeZone zone) {
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(nowMillis); day.add(Calendar.DATE, 1);
        day.set(Calendar.HOUR_OF_DAY, hour); day.set(Calendar.MINUTE, minute);
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis() / 1000;
    }
    public static long nextEvening(long nowMillis, TimeZone zone) {
        Calendar day = Calendar.getInstance(zone); day.setTimeInMillis(nowMillis);
        day.set(Calendar.HOUR_OF_DAY, 19); day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
        if (day.getTimeInMillis() <= nowMillis) day.add(Calendar.DATE, 1);
        return day.getTimeInMillis();
    }
    public static String dayKey(long epochSeconds) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(epochSeconds * 1000));
    }
}
