package io.github.jamerlybob.windroute.places;

import java.util.Locale;

/** Saved imports have a track name; searched rides have two place names. */
public final class LastRouteText {
    private LastRouteText() { }

    public static String format(String routeTemplate, String trackTemplate, String origin,
                                String destination, boolean imported) {
        if (imported) {
            return String.format(Locale.getDefault(),trackTemplate,origin == null ? "" : origin.trim());
        }
        return String.format(Locale.getDefault(),routeTemplate,
                PlaceNameFormatter.endpoint(origin,false,""),
                PlaceNameFormatter.endpoint(destination,false,""));
    }
}
