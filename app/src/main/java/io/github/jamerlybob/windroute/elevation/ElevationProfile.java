package io.github.jamerlybob.windroute.elevation;

import java.util.Arrays;
import java.util.List;

import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** Distances, smoothed heights and gradients along a route. */
public final class ElevationProfile {

    public static final int SMOOTHING_RADIUS = 1;
    public static final double ELEVATION_HYSTERESIS_METERS = 2.0;

    public final double[] distanceMeters;
    public final double[] rawElevationMeters;
    public final double[] elevationMeters;
    /** Gradient ending at each sample. The first sample has gradient zero. */
    public final double[] gradientPercent;
    public final double totalAscentMeters;
    public final double totalDescentMeters;

    public ElevationProfile(List<GeoPoint> points, double[] elevations) {
        this(GeoMath.cumulativeMeters(points), elevations);
    }

    public ElevationProfile(double[] distances, double[] elevations) {
        if (distances.length != elevations.length) {
            throw new IllegalArgumentException("Each elevation needs a matching distance.");
        }
        if (!isNonDecreasing(distances)) {
            throw new IllegalArgumentException("Profile distances must be in route order.");
        }
        this.distanceMeters = Arrays.copyOf(distances, distances.length);
        this.rawElevationMeters = Arrays.copyOf(elevations, elevations.length);
        this.elevationMeters = smooth(elevations);
        this.gradientPercent = new double[elevations.length];

        for (int i = 1; i < elevations.length; i++) {
            double run = distanceMeters[i] - distanceMeters[i - 1];
            double rise = elevationMeters[i] - elevationMeters[i - 1];
            gradientPercent[i] = run > 0 ? rise * 100.0 / run : 0;
        }
        this.totalAscentMeters = accumulatedChange(elevationMeters, true);
        this.totalDescentMeters = accumulatedChange(elevationMeters, false);
    }

    public int size() {
        return distanceMeters.length;
    }

    /** Ascent between two distances, including a proportional part of edge segments. */
    public double ascentBetween(double startMeters, double endMeters) {
        if (endMeters <= startMeters || distanceMeters.length < 2) {
            return 0;
        }
        double clippedStart = Math.max(startMeters, distanceMeters[0]);
        double clippedEnd = Math.min(endMeters, distanceMeters[distanceMeters.length - 1]);
        if (clippedEnd <= clippedStart) {
            return 0;
        }

        ChangeCounter counter = new ChangeCounter(heightAt(clippedStart));
        for (int i = 1; i < distanceMeters.length && distanceMeters[i] <= clippedEnd; i++) {
            if (distanceMeters[i] > clippedStart) {
                counter.add(elevationMeters[i]);
            }
        }
        if (clippedEnd < distanceMeters[distanceMeters.length - 1]
                && !containsDistance(clippedEnd)) {
            counter.add(heightAt(clippedEnd));
        }
        return counter.ascentMeters;
    }

    private static double accumulatedChange(double[] heights, boolean ascent) {
        if (heights.length == 0) {
            return 0;
        }
        ChangeCounter counter = new ChangeCounter(heights[0]);
        for (int i = 1; i < heights.length; i++) {
            counter.add(heights[i]);
        }
        return ascent ? counter.ascentMeters : counter.descentMeters;
    }

    /**
     * Counts a move only after it gets outside a two-metre noise band.
     * A slow steady rise eventually crosses that band, at which point the whole
     * move is counted and the new height becomes the reference. Small up-and-down
     * wiggles never cross it, so they do not create imaginary climbing.
     */
    private static final class ChangeCounter {
        private double referenceHeight;
        private double ascentMeters;
        private double descentMeters;

        ChangeCounter(double referenceHeight) {
            this.referenceHeight = referenceHeight;
        }

        void add(double height) {
            double change = height - referenceHeight;
            if (change >= ELEVATION_HYSTERESIS_METERS) {
                ascentMeters += change;
                referenceHeight = height;
            } else if (change <= -ELEVATION_HYSTERESIS_METERS) {
                descentMeters -= change;
                referenceHeight = height;
            }
        }
    }

    private boolean containsDistance(double meters) {
        for (double distance : distanceMeters) {
            if (distance == meters) {
                return true;
            }
        }
        return false;
    }

    private double heightAt(double meters) {
        for (int i = 1; i < distanceMeters.length; i++) {
            if (meters <= distanceMeters[i]) {
                double length = distanceMeters[i] - distanceMeters[i - 1];
                if (length <= 0) {
                    return elevationMeters[i];
                }
                double fraction = (meters - distanceMeters[i - 1]) / length;
                return elevationMeters[i - 1]
                        + fraction * (elevationMeters[i] - elevationMeters[i - 1]);
            }
        }
        return elevationMeters[elevationMeters.length - 1];
    }

    private static double[] smooth(double[] elevations) {
        double[] result = Arrays.copyOf(elevations, elevations.length);
        // A three-sample moving average removes single-grid spikes. Keep the two
        // route ends unchanged because averaging them inward would erase genuine
        // height gained between the start and finish.
        for (int i = SMOOTHING_RADIUS; i < elevations.length - SMOOTHING_RADIUS; i++) {
            double sum = 0;
            int count = 0;
            for (int offset = -SMOOTHING_RADIUS; offset <= SMOOTHING_RADIUS; offset++) {
                sum += elevations[i + offset];
                count++;
            }
            result[i] = sum / count;
        }
        return result;
    }

    private static boolean isNonDecreasing(double[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) {
                return false;
            }
        }
        return true;
    }
}
