package io.github.jamerlybob.windroute.wind;

/** Wording decisions stay separate from translated Android resources. */
public final class WindOutlook {
    private WindOutlook() { }
    public static int compass(double degrees) {
        return (int) Math.floor((((degrees % 360) + 360) % 360 + 22.5) / 45) % 8;
    }
    public static String sentence(String template, String from, String speed,
                                  String trend, String later, String clock, String home) {
        return String.format(java.util.Locale.getDefault(), template, from, speed, trend, later, clock, home);
    }
    public static long nextWholeHour(long epochSeconds) {
        return Math.floorDiv(epochSeconds, 3600) * 3600 + 3600;
    }
    public static boolean steady(double now, double later) { return Math.abs(now - later) < 5; }
    public static boolean easing(double now, double later) { return later <= now - 5; }
}
