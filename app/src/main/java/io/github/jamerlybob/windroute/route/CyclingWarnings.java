package io.github.jamerlybob.windroute.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the required fixed cycling notice without repeating Google text. */
public final class CyclingWarnings {
    private CyclingWarnings() {
    }

    public static List<String> combine(String fixedNotice, List<String> googleWarnings) {
        List<String> result = new ArrayList<>();
        addIfNew(result, fixedNotice);
        for (String warning : googleWarnings) {
            addIfNew(result, warning);
        }
        return result;
    }

    private static void addIfNew(List<String> result, String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return;
        }
        String clean = candidate.trim();
        for (String existing : result) {
            if (normalize(existing).equals(normalize(clean))) {
                return;
            }
        }
        result.add(clean);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
