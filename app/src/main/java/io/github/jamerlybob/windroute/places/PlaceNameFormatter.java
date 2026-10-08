package io.github.jamerlybob.windroute.places;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns inconsistent Geocoder fields into one readable line. It is a separate
 * pure class because Address is an Android type, while the fallback and
 * de-duplication rules can be tested using ordinary strings.
 */
public final class PlaceNameFormatter {
    private PlaceNameFormatter() {
    }

    public static String format(List<String> addressLines, String featureName,
                                String locality, String adminArea, String countryName) {
        for (String line : addressLines) {
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        }

        List<String> parts = new ArrayList<>();
        addDistinct(parts, featureName);
        addDistinct(parts, locality);
        addDistinct(parts, adminArea);
        addDistinct(parts, countryName);
        return String.join(", ", parts);
    }

    private static void addDistinct(List<String> parts, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        String clean = value.trim();
        for (String part : parts) {
            if (part.equalsIgnoreCase(clean)) {
                return;
            }
        }
        parts.add(clean);
    }
}
