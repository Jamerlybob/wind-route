package io.github.jamerlybob.windroute.wind;

import java.util.List;

/** One source of truth for the strip's labels, marker, selection and tap targets. */
public final class DepartureSlots {
    private DepartureSlots() { }

    public static int bestIndex(List<DepartureScorer.Entry> entries) {
        if (entries.isEmpty()) return -1;
        int best = 0;
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).score < entries.get(best).score) best = i;
        }
        return best;
    }

    public static int selectedIndex(List<DepartureScorer.Entry> entries, long time) {
        if (entries.isEmpty()) return -1;
        int selected = 0;
        // The first bar is the current hour, even at 40 minutes past. Rounding
        // to the nearest hour made a ride leaving now highlight the next bar.
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).departureEpochSeconds > time) break;
            selected = i;
        }
        return selected;
    }

    public static int indexAt(float x, float left, float right, int count) {
        if (count <= 0) return -1;
        int index = (int) ((x - left) / Math.max(1, right - left) * count);
        return Math.max(0, Math.min(count - 1, index));
    }

    public static float center(int index, float left, float right, int count) {
        return left + (index + .5f) * (right - left) / Math.max(1, count);
    }
}
