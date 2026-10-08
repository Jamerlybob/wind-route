package io.github.jamerlybob.windroute.nav;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** Tests camera movement against its last target, so small movements accumulate. */
public final class CameraFollow {
    private CameraFollow() { }

    public static boolean shouldMove(GeoPoint previous, float previousBearing,
                                     GeoPoint position, float bearing, boolean animating) {
        if (animating) return false;
        if (previous == null) return true;
        // Circular difference avoids treating 359 -> 1 degrees as a full turn.
        double angle = Math.abs((bearing - previousBearing + 540) % 360 - 180);
        return GeoMath.distanceMeters(previous, position) > 3 || angle > 5;
    }
}
