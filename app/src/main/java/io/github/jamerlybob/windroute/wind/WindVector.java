package io.github.jamerlybob.windroute.wind;

/** Wind velocity in km/h, pointing towards where the air goes. */
public final class WindVector {
    public final double east, north;
    public WindVector(double east, double north) { this.east = east; this.north = north; }
    public static WindVector from(double speed, double fromDegrees) {
        double angle = Math.toRadians(fromDegrees);
        return new WindVector(-speed * Math.sin(angle), -speed * Math.cos(angle));
    }
    public double speed() { return Math.hypot(east, north); }
    public double fromDegrees() { return (Math.toDegrees(Math.atan2(-east, -north)) + 360) % 360; }
    public WindVector mix(WindVector other, double fraction) {
        return new WindVector(east + (other.east - east) * fraction,
                north + (other.north - north) * fraction);
    }
}
