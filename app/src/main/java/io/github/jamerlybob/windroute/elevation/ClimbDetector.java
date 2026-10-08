package io.github.jamerlybob.windroute.elevation;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds sustained climbs in a cleaned elevation profile. Detection is kept
 * separate from fetching and display so its cycling-specific thresholds can
 * be understood and tested with small synthetic roads.
 */
public final class ClimbDetector {

    /** Wahoo Summit's documented minimum length for a detected climb. */
    public static final double MIN_LENGTH_METERS = 400.0;
    /** The project spec uses 3% to exclude long but nearly flat drags. */
    public static final double MIN_AVERAGE_GRADIENT_PERCENT = 3.0;
    /** The project spec requires enough total rise to be useful to a rider. */
    public static final double MIN_GAIN_METERS = 15.0;
    /** About 2% separates a real climb from its gentler approach road. */
    public static final double MIN_CLIMBING_GRADIENT_PERCENT = 2.0;
    /** Two typical 100 m samples may bridge short flats within one climb. */
    public static final double MAX_INTERRUPTION_METERS = 200.0;
    /** Five metres tolerates elevation noise but ends a meaningful descent. */
    public static final double MAX_INTERRUPTION_HEIGHT_LOSS_METERS = 5.0;
    /** The product reports the steepest 100 m, a useful cycling-sized effort. */
    public static final double STEEPEST_WINDOW_METERS = 100.0;

    private ClimbDetector() {
    }

    /** Measurements for one detected climb, ready for UI or wind analysis. */
    public static final class Climb {
        public final double startDistanceMeters;
        public final double lengthMeters;
        public final double gainMeters;
        public final double averageGradientPercent;
        public final double steepest100mGradientPercent;

        Climb(double startDistanceMeters, double lengthMeters, double gainMeters,
              double averageGradientPercent, double steepest100mGradientPercent) {
            this.startDistanceMeters = startDistanceMeters;
            this.lengthMeters = lengthMeters;
            this.gainMeters = gainMeters;
            this.averageGradientPercent = averageGradientPercent;
            this.steepest100mGradientPercent = steepest100mGradientPercent;
        }

        public double endDistanceMeters() {
            return startDistanceMeters + lengthMeters;
        }
    }

    public static List<Climb> detect(ElevationProfile profile) {
        List<Climb> climbs = new ArrayList<>();
        int start = -1;
        int lastClimbing = -1;
        double lowestInterruptionHeight = Double.POSITIVE_INFINITY;

        for (int i = 1; i < profile.size(); i++) {
            // A gentle approach is road leading to a climb, not part of the climb.
            // Once climbing starts, a short flat or dip can join two steep pieces,
            // provided it is both short and does not give away much height.
            if (profile.gradientPercent[i] >= MIN_CLIMBING_GRADIENT_PERCENT) {
                if (start < 0) {
                    start = i - 1;
                }
                lastClimbing = i;
                lowestInterruptionHeight = Double.POSITIVE_INFINITY;
            } else if (start >= 0) {
                lowestInterruptionHeight = Math.min(lowestInterruptionHeight,
                        profile.elevationMeters[i]);
                double interruptionLength = profile.distanceMeters[i]
                        - profile.distanceMeters[lastClimbing];
                double heightLoss = profile.elevationMeters[lastClimbing]
                        - lowestInterruptionHeight;
                if (interruptionLength > MAX_INTERRUPTION_METERS
                        || heightLoss > MAX_INTERRUPTION_HEIGHT_LOSS_METERS) {
                    addIfClimb(climbs, profile, start, lastClimbing);
                    start = -1;
                    lastClimbing = -1;
                    lowestInterruptionHeight = Double.POSITIVE_INFINITY;
                }
            }
        }
        if (start >= 0) {
            addIfClimb(climbs, profile, start, lastClimbing);
        }
        return climbs;
    }

    private static void addIfClimb(List<Climb> climbs, ElevationProfile profile,
                                   int start, int end) {
        double length = profile.distanceMeters[end] - profile.distanceMeters[start];
        double gain = profile.elevationMeters[end] - profile.elevationMeters[start];
        double average = length > 0 ? gain * 100.0 / length : 0;
        if (length < MIN_LENGTH_METERS || gain < MIN_GAIN_METERS
                || average < MIN_AVERAGE_GRADIENT_PERCENT) {
            return;
        }
        climbs.add(new Climb(profile.distanceMeters[start], length, gain, average,
                steepestWindow(profile, start, end)));
    }

    private static double steepestWindow(ElevationProfile profile, int start, int end) {
        double climbStart = profile.distanceMeters[start];
        double climbEnd = profile.distanceMeters[end];
        double steepest = 0;
        for (int i = start; i <= end; i++) {
            double windowStart = profile.distanceMeters[i];
            if (windowStart + STEEPEST_WINDOW_METERS <= climbEnd) {
                steepest = Math.max(steepest, windowGradient(profile, windowStart));
            }

            // A maximum can also occur where a 100 m window ends at a sample.
            // Checking both alignments avoids missing it on irregularly spaced data.
            windowStart = profile.distanceMeters[i] - STEEPEST_WINDOW_METERS;
            if (windowStart >= climbStart) {
                steepest = Math.max(steepest, windowGradient(profile, windowStart));
            }
        }
        return steepest;
    }

    private static double windowGradient(ElevationProfile profile, double startMeters) {
        double startHeight = heightAt(profile, startMeters);
        double endHeight = heightAt(profile, startMeters + STEEPEST_WINDOW_METERS);
        return (endHeight - startHeight) * 100.0 / STEEPEST_WINDOW_METERS;
    }

    private static double heightAt(ElevationProfile profile, double meters) {
        if (profile.size() == 0) {
            return 0;
        }
        for (int i = 1; i < profile.size(); i++) {
            if (meters <= profile.distanceMeters[i]) {
                double length = profile.distanceMeters[i] - profile.distanceMeters[i - 1];
                if (length <= 0) {
                    return profile.elevationMeters[i];
                }
                double fraction = (meters - profile.distanceMeters[i - 1]) / length;
                return profile.elevationMeters[i - 1]
                        + fraction * (profile.elevationMeters[i] - profile.elevationMeters[i - 1]);
            }
        }
        return profile.elevationMeters[profile.size() - 1];
    }
}
