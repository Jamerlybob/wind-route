package io.github.jamerlybob.windroute.wind;

/**
 * The trigonometry at the heart of the app.
 *
 * <p>Plain Java with no Android imports, so it runs in an ordinary unit test.
 *
 * <p>Two angles go in, both in compass degrees (0 = north, 90 = east, clockwise):
 * <ul>
 *   <li><b>heading</b>: the direction the rider is travelling <i>towards</i>.</li>
 *   <li><b>windFrom</b>: the direction the wind is blowing <i>from</i>. This is the
 *       meteorological convention and it is what Open-Meteo returns. A "northerly"
 *       is wind from the north, 0 degrees, pushing things south.</li>
 * </ul>
 *
 * <p>Because one angle is a "towards" and the other is a "from", a rider heading
 * north (0) into a northerly (0) has a relative angle of 0, which is a pure headwind.
 * That is why no 180 degree flip appears anywhere below.
 */
public final class WindMath {

    /** Below this wind speed the direction is not worth colouring. */
    public static final double CALM_BELOW_KMH = 5.0;

    /**
     * Half-width of the headwind and tailwind cones, in degrees. Wind within 60
     * degrees of dead ahead counts as a headwind, within 60 of dead astern as a
     * tailwind, and the two 60 degree slices in between as crosswind.
     *
     * <p>60 is where cos(angle) = 0.5: inside the cone at least half the wind's
     * speed is working directly for or against you.
     */
    public static final double CONE_HALF_WIDTH_DEG = 60.0;

    private WindMath() {
    }

    /** Wraps any angle into the range [0, 360). */
    public static double normalize360(double degrees) {
        double wrapped = degrees % 360.0;
        return wrapped < 0 ? wrapped + 360.0 : wrapped;
    }

    /** Wraps any angle into the range (-180, 180]. */
    public static double normalize180(double degrees) {
        double wrapped = normalize360(degrees);
        return wrapped > 180.0 ? wrapped - 360.0 : wrapped;
    }

    /**
     * Angle of the wind relative to the rider, in (-180, 180].
     * 0 is dead ahead, 180 is dead astern, positive is from the rider's right.
     */
    public static double relativeAngle(double headingDeg, double windFromDeg) {
        return normalize180(windFromDeg - headingDeg);
    }

    /**
     * The part of the wind blowing straight along the road.
     * Positive is a headwind, negative a tailwind, in the same unit as windSpeed.
     */
    public static double headwindComponent(double headingDeg, double windFromDeg,
                                           double windSpeed) {
        return windSpeed * Math.cos(Math.toRadians(relativeAngle(headingDeg, windFromDeg)));
    }

    /**
     * The part of the wind blowing across the road.
     * Positive is from the rider's right, negative from the left.
     */
    public static double crosswindComponent(double headingDeg, double windFromDeg,
                                            double windSpeed) {
        return windSpeed * Math.sin(Math.toRadians(relativeAngle(headingDeg, windFromDeg)));
    }

    public static WindEffect classify(double headingDeg, double windFromDeg,
                                      double windSpeedKmh) {
        return classify(headingDeg, windFromDeg, windSpeedKmh, CALM_BELOW_KMH);
    }

    /** Classifies wind using the calm threshold chosen in settings. */
    public static WindEffect classify(double headingDeg, double windFromDeg,
                                      double windSpeedKmh, double calmBelowKmh) {
        if (windSpeedKmh < calmBelowKmh) {
            return WindEffect.CALM;
        }
        double offNose = Math.abs(relativeAngle(headingDeg, windFromDeg));
        if (offNose <= CONE_HALF_WIDTH_DEG) {
            return WindEffect.HEADWIND;
        }
        if (offNose >= 180.0 - CONE_HALF_WIDTH_DEG) {
            return WindEffect.TAILWIND;
        }
        return WindEffect.CROSSWIND;
    }
}
