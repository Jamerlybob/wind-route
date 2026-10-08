package io.github.jamerlybob.windroute.trip;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ElevationProfile;

/** Divides one long route into consecutive riding days. */
public final class DaySplitter {

    /** A common touring rule of thumb: 100 m climbed feels like 1 extra kilometre. */
    public static final double EFFORT_METERS_PER_ASCENT_METER = 10.0;
    public static final double MIN_LAST_DAY_FRACTION = 1.0 / 3.0;

    private DaySplitter() {
    }

    public static final class Day {
        /** Endpoints are shared: one day's end point is the next day's start point. */
        public final int startPointIndex;
        public final int endPointIndex;
        public final double distanceMeters;
        public final double ascentMeters;

        Day(int startPointIndex, int endPointIndex, double distanceMeters,
            double ascentMeters) {
            this.startPointIndex = startPointIndex;
            this.endPointIndex = endPointIndex;
            this.distanceMeters = distanceMeters;
            this.ascentMeters = ascentMeters;
        }
    }

    public static List<Day> byDistance(double[] cumulativeMeters, double targetMeters) {
        return byDistance(cumulativeMeters, null, targetMeters);
    }

    public static List<Day> byDistance(double[] cumulativeMeters, ElevationProfile profile,
                                       double targetMeters) {
        validate(cumulativeMeters, targetMeters);
        double total = last(cumulativeMeters);
        List<Double> boundaries = automaticBoundaries(total, targetMeters);
        return makeDays(cumulativeMeters, profile, boundaries);
    }

    public static List<Day> byEffort(double[] cumulativeMeters, ElevationProfile profile,
                                     double targetEffortMeters) {
        if (profile == null) {
            throw new IllegalArgumentException("An elevation profile is needed for effort splits.");
        }
        validate(cumulativeMeters, targetEffortMeters);
        double[] effort = new double[cumulativeMeters.length];
        for (int i = 1; i < effort.length; i++) {
            effort[i] = cumulativeMeters[i]
                    + EFFORT_METERS_PER_ASCENT_METER
                    * profile.ascentBetween(0, cumulativeMeters[i]);
        }
        List<Double> effortBoundaries = automaticBoundaries(last(effort), targetEffortMeters);
        List<Double> distanceBoundaries = new ArrayList<>();
        for (double boundary : effortBoundaries) {
            int index = nearestIndex(effort, boundary, 0, effort.length - 1);
            distanceBoundaries.add(cumulativeMeters[index]);
        }
        return makeDays(cumulativeMeters, profile, distanceBoundaries);
    }

    public static List<Day> atDistances(double[] cumulativeMeters, ElevationProfile profile,
                                        double[] splitDistances) {
        if (cumulativeMeters.length == 0) {
            return new ArrayList<>();
        }
        requireOrdered(cumulativeMeters);
        List<Double> boundaries = new ArrayList<>();
        double previous = 0;
        double total = last(cumulativeMeters);
        for (double split : splitDistances) {
            if (split <= previous || split >= total) {
                throw new IllegalArgumentException(
                        "Hand-picked split distances must be increasing and inside the route.");
            }
            boundaries.add(split);
            previous = split;
        }
        return makeDays(cumulativeMeters, profile, boundaries);
    }

    public static List<Day> byHandPickedDistances(double[] cumulativeMeters,
                                                   ElevationProfile profile,
                                                   double[] splitDistances) {
        return atDistances(cumulativeMeters, profile, splitDistances);
    }

    private static List<Double> automaticBoundaries(double total, double target) {
        List<Double> boundaries = new ArrayList<>();
        if (total <= 0) {
            return boundaries;
        }
        int fullDays = (int) Math.floor(total / target);
        double remainder = total - fullDays * target;
        if (fullDays > 0 && remainder > 0 && remainder < target * MIN_LAST_DAY_FRACTION) {
            // A tiny final day is not useful on a tour. Keep the smaller number
            // of days and spread their boundaries evenly across the whole route.
            for (int day = 1; day < fullDays; day++) {
                boundaries.add(total * day / fullDays);
            }
        } else {
            for (double boundary = target; boundary < total; boundary += target) {
                boundaries.add(boundary);
            }
        }
        return boundaries;
    }

    private static List<Day> makeDays(double[] cumulative, ElevationProfile profile,
                                      List<Double> boundaryDistances) {
        List<Day> days = new ArrayList<>();
        if (cumulative.length == 0) {
            return days;
        }
        int start = 0;
        for (double boundary : boundaryDistances) {
            int end = nearestIndex(cumulative, boundary, start + 1, cumulative.length - 1);
            if (end > start && end < cumulative.length - 1) {
                days.add(day(start, end, cumulative, profile));
                start = end;
            }
        }
        days.add(day(start, cumulative.length - 1, cumulative, profile));
        return days;
    }

    private static Day day(int start, int end, double[] cumulative,
                           ElevationProfile profile) {
        double startMeters = cumulative[start];
        double endMeters = cumulative[end];
        double ascent = profile == null ? 0 : profile.ascentBetween(startMeters, endMeters);
        return new Day(start, end, endMeters - startMeters, ascent);
    }

    private static int nearestIndex(double[] values, double wanted, int first, int last) {
        first = Math.max(0, Math.min(first, values.length - 1));
        last = Math.max(first, Math.min(last, values.length - 1));
        int best = first;
        double bestGap = Math.abs(values[first] - wanted);
        for (int i = first + 1; i <= last; i++) {
            double gap = Math.abs(values[i] - wanted);
            if (gap < bestGap) {
                best = i;
                bestGap = gap;
            }
        }
        return best;
    }

    private static void validate(double[] cumulative, double target) {
        if (target <= 0 || !Double.isFinite(target)) {
            throw new IllegalArgumentException("The daily target must be greater than zero.");
        }
        requireOrdered(cumulative);
    }

    private static void requireOrdered(double[] cumulative) {
        for (int i = 1; i < cumulative.length; i++) {
            if (cumulative[i] < cumulative[i - 1]) {
                throw new IllegalArgumentException("Route distances must be in route order.");
            }
        }
    }

    private static double last(double[] values) {
        return values.length == 0 ? 0 : values[values.length - 1];
    }
}
