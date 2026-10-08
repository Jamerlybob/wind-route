package io.github.jamerlybob.windroute.elevation;

import io.github.jamerlybob.windroute.wind.RouteWind;

/** The wind experienced specifically while riding one climb. */
public final class ClimbWind {

    public static final double HEADWIND_THRESHOLD_KMH = 5.0;

    public final double averageHeadwindKmh;
    public final boolean isClimbIntoHeadwind;

    private ClimbWind(double averageHeadwindKmh) {
        this.averageHeadwindKmh = averageHeadwindKmh;
        this.isClimbIntoHeadwind = averageHeadwindKmh >= HEADWIND_THRESHOLD_KMH;
    }

    public static ClimbWind analyze(ClimbDetector.Climb climb, RouteWind routeWind) {
        double climbStart = climb.startDistanceMeters;
        double climbEnd = climb.endDistanceMeters();
        double stretchStart = 0;
        double covered = 0;
        double weightedHeadwind = 0;

        for (RouteWind.Stretch stretch : routeWind.stretches) {
            double stretchEnd = stretchStart + stretch.lengthMeters;
            double overlap = Math.min(climbEnd, stretchEnd) - Math.max(climbStart, stretchStart);
            if (overlap > 0) {
                covered += overlap;
                weightedHeadwind += stretch.headwindKmh * overlap;
            }
            stretchStart = stretchEnd;
        }
        return new ClimbWind(covered > 0 ? weightedHeadwind / covered : 0);
    }
}
