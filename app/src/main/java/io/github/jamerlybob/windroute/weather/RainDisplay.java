package io.github.jamerlybob.windroute.weather;

/** Both rain summaries use one rounding boundary for their one-decimal display. */
public final class RainDisplay {
    private RainDisplay() { }

    public static boolean showAmount(double millimeters) {
        // A trace which displays as 0.0 should not acquire a misleading time
        // label. Unknown remains distinct from a confident dry forecast.
        return Double.isFinite(millimeters) && Math.round(millimeters * 10) > 0;
    }
}
