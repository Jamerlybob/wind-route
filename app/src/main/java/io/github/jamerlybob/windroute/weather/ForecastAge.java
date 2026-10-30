package io.github.jamerlybob.windroute.weather;

/** A short grace period avoids awkward "0 minutes ago" just after a refresh. */
public final class ForecastAge {
    private ForecastAge() { }

    public static long minutes(long fetchedAtMillis, long nowMillis) {
        return Math.max(0, nowMillis - fetchedAtMillis) / 60000;
    }
    public static String wording(long fetchedAtMillis, long nowMillis, String template,
                                 String minutesTemplate, String hoursTemplate, String daysTemplate) {
        long minutes = minutes(fetchedAtMillis, nowMillis);
        String amount = String.format(java.util.Locale.getDefault(), minutes < 120 ? minutesTemplate
                : minutes < 2880 ? hoursTemplate : daysTemplate,
                minutes < 120 ? minutes : minutes < 2880 ? minutes / 60 : minutes / 1440);
        return String.format(java.util.Locale.getDefault(), template, amount);
    }
    public static boolean justUpdated(long fetchedAtMillis, long nowMillis) {
        return fetchedAtMillis > 0 && Math.max(0,nowMillis-fetchedAtMillis) < 120000;
    }
}
