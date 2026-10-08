package io.github.jamerlybob.windroute.wind;

import io.github.jamerlybob.windroute.route.Route;

/**
 * Estimates the time cost of wind with the standard flat-road cycling equation.
 *
 * <p>The defaults are assumptions for an everyday rider, not measurements:
 * 1.225 kg/m3 air density, 0.40 m2 upright drag area, 0.005 rolling resistance,
 * and 85 kg for rider plus bike. The UI should repeat these assumptions.
 */
public final class PowerModel {

    /** Air density at sea level in ordinary conditions, kg/m3. */
    public static final double AIR_DENSITY_KG_M3 = 1.225;
    /** Upright commuter/touring position, drag coefficient times frontal area, m2. */
    public static final double DRAG_AREA_M2 = 0.40;
    /** Typical road tyres on ordinary sealed roads. */
    public static final double ROLLING_RESISTANCE = 0.005;
    /** An illustrative 72 kg rider and 13 kg bike plus luggage. */
    public static final double RIDER_AND_BIKE_KG = 85.0;
    public static final double GRAVITY_M_S2 = 9.80665;

    /** Downhill and strong-tailwind details are outside this flat-road model. */
    public static final double MAX_SPEED_M_S = 20.0; // 72 km/h
    private static final int BISECTION_STEPS = 80;

    public static final class Result {
        public final double stillAirMinutes;
        public final double windyMinutes;
        public final double differenceMinutes;
        /** Power inferred from Google's duration and the flat-road assumptions. */
        public final double assumedPowerWatts;

        private Result(double stillAirMinutes, double windyMinutes,
                       double assumedPowerWatts) {
            this.stillAirMinutes = stillAirMinutes;
            this.windyMinutes = windyMinutes;
            this.differenceMinutes = windyMinutes - stillAirMinutes;
            this.assumedPowerWatts = assumedPowerWatts;
        }
    }

    private PowerModel() {
    }

    public static Result estimate(Route route, RouteWind routeWind) {
        double stillAirMinutes = route.durationSeconds / 60.0;
        if (route.durationSeconds <= 0 || route.distanceMeters <= 0
                || routeWind.totalMeters <= 0) {
            return new Result(Math.max(0, stillAirMinutes),
                    Math.max(0, stillAirMinutes), 0);
        }

        double stillAirSpeed = route.distanceMeters / route.durationSeconds;
        double riderPower = powerWatts(stillAirSpeed, 0);
        double windySeconds = 0;
        for (RouteWind.Stretch stretch : routeWind.stretches) {
            if (stretch.lengthMeters <= 0) {
                continue;
            }
            double headwindMps = stretch.headwindKmh / 3.6;
            double speed = speedForPower(riderPower, headwindMps);

            // Google's distance can differ slightly from the decoded polyline.
            // Preserve each stretch's share while making no-wind time exactly
            // equal Google's duration.
            double scaledLength = route.distanceMeters
                    * stretch.lengthMeters / routeWind.totalMeters;
            windySeconds += scaledLength / speed;
        }
        return new Result(stillAirMinutes, windySeconds / 60.0, riderPower);
    }

    private static double powerWatts(double groundSpeedMps, double headwindMps) {
        double airSpeed = groundSpeedMps + headwindMps;
        double aerodynamic = 0.5 * AIR_DENSITY_KG_M3 * DRAG_AREA_M2
                * airSpeed * airSpeed * groundSpeedMps;
        double rolling = ROLLING_RESISTANCE * RIDER_AND_BIKE_KG
                * GRAVITY_M_S2 * groundSpeedMps;
        return aerodynamic + rolling;
    }

    private static double speedForPower(double targetPowerWatts, double headwindMps) {
        // With a tailwind faster than the bike, the written flat-road equation
        // does not model the wind pushing the rider. Start at wind speed, where
        // relative air speed is zero, rather than selecting the equation's slow,
        // unphysical root. This also gives a range where power rises monotonically.
        double low = Math.min(MAX_SPEED_M_S, Math.max(0, -headwindMps));
        if (low >= MAX_SPEED_M_S || powerWatts(low, headwindMps) >= targetPowerWatts) {
            return low;
        }
        if (powerWatts(MAX_SPEED_M_S, headwindMps) <= targetPowerWatts) {
            // A sufficiently strong tailwind makes the simple equation predict
            // ever higher speeds. Cap it where road safety and other drag would
            // dominate instead of pretending the flat-road model still applies.
            return MAX_SPEED_M_S;
        }

        double high = MAX_SPEED_M_S;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double middle = (low + high) / 2;
            // In the useful solution range required power rises with speed, so
            // each comparison discards half the remaining range. Bisection is
            // longer to write than a cubic formula, but much easier to verify.
            if (powerWatts(middle, headwindMps) < targetPowerWatts) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) / 2;
    }
}
