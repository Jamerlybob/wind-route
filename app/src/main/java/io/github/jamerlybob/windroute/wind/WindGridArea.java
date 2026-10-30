package io.github.jamerlybob.windroute.wind;

import io.github.jamerlybob.windroute.route.*;

/** Camera bounds, not route bounds: a fixed 10 x 10 lattice becomes coarser on zoom-out. */
public final class WindGridArea {
    public final double south, west, north, east;
    public final boolean supported;
    private WindGridArea(double south, double west, double north, double east, boolean supported) {
        this.south=south; this.west=west; this.north=north; this.east=east; this.supported=supported;
    }
    public static WindGridArea camera(double south, double west, double north, double east) {
        if (east < west) east += 360;
        // Beyond a thousand kilometres the coarse lattice stops being useful for
        // ride planning. This still covers a 300 km-wide portrait viewport easily.
        double middle = (south+north)/2;
        boolean supported = Double.isFinite(south) && Double.isFinite(north)
                && Double.isFinite(west) && Double.isFinite(east) && north > south && east > west
                && south >= -85 && north <= 85 && east-west < 180
                && GeoMath.distanceMeters(new GeoPoint(south,west),new GeoPoint(north,west)) <= 1000000
                && GeoMath.distanceMeters(new GeoPoint(middle,west),new GeoPoint(middle,east)) <= 1000000;
        double latPad = Math.max(.02,(north-south)*.2);
        double lngPad = Math.max(.02,(east-west)*.2);
        return new WindGridArea(Math.max(-85,south-latPad),west-lngPad,
                Math.min(85,north+latPad),east+lngPad,supported);
    }
    public boolean coveredBy(WindField field) {
        return field != null && field.covers(south,west) && field.covers(north,east);
    }
}
