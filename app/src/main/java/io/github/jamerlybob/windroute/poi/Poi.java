package io.github.jamerlybob.windroute.poi;

import io.github.jamerlybob.windroute.route.GeoPoint;

/** One useful place returned by OpenStreetMap. */
public final class Poi {
    public final PoiKind kind;
    public final String name;
    public final GeoPoint position;
    public final String openingHours;

    public Poi(PoiKind kind, String name, GeoPoint position, String openingHours) {
        this.kind = kind;
        this.name = name;
        this.position = position;
        this.openingHours = openingHours;
    }
}
