package io.github.jamerlybob.windroute.poi;

import java.util.*;
import io.github.jamerlybob.windroute.route.GeoMath;

/** Display names are supplied by resources; only identity and geometry live here. */
public final class PlacePresentation {
    private PlacePresentation() { }
    public static String name(Poi poi, String kindName) {
        return poi.name == null || poi.name.trim().isEmpty() ? kindName : poi.name.trim();
    }
    public static List<PoiAlongRoute> collapse(List<PoiAlongRoute> places) {
        List<PoiAlongRoute> result = new ArrayList<>();
        for (PoiAlongRoute place : places) {
            boolean duplicate = false;
            if (place.poi.name == null || place.poi.name.trim().isEmpty()) {
                for (PoiAlongRoute kept : result) {
                    // Named businesses and different opening hours remain separate.
                    if (kept.poi.kind == place.poi.kind
                            && (kept.poi.name == null || kept.poi.name.trim().isEmpty())
                            && Objects.equals(kept.poi.openingHours, place.poi.openingHours)
                            && GeoMath.distanceMeters(kept.poi.position, place.poi.position) <= 50) {
                        duplicate = true; break;
                    }
                }
            }
            if (!duplicate) result.add(place);
        }
        return result;
    }
}
