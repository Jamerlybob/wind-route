package io.github.jamerlybob.windroute.weather;

/** A short grace period avoids awkward "0 minutes ago" just after a refresh. */
public final class ForecastAge {
    private ForecastAge() { }

    public static boolean justUpdated(long fetchedAtMillis, long nowMillis) {
        return fetchedAtMillis > 0 && Math.max(0,nowMillis-fetchedAtMillis) < 120000;
    }
}
