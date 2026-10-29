package io.github.jamerlybob.windroute.weather;

/** Both rain summaries use one rounding boundary for their one-decimal display. */
public final class RainDisplay {
    private RainDisplay() { }

    public enum Outlook { UNKNOWN, DRY, POSSIBLE, LIKELY }

    public static Outlook outlook(double chancePercent, double millimeters) {
        if (Double.isFinite(chancePercent)) {
            if (chancePercent >= 60) return Outlook.LIKELY;
            if (chancePercent >= 20) return Outlook.POSSIBLE;
            return Outlook.DRY;
        }
        if (!Double.isFinite(millimeters)) return Outlook.UNKNOWN;
        return millimeters > 0 ? Outlook.POSSIBLE : Outlook.DRY;
    }

    public static boolean showAmount(double millimeters) {
        // A trace which displays as 0.0 should not acquire a misleading time
        // label. Unknown remains distinct from a confident dry forecast.
        return Double.isFinite(millimeters) && Math.round(millimeters * 10) > 0;
    }
}
