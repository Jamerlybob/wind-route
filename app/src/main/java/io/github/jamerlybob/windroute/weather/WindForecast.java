package io.github.jamerlybob.windroute.weather;

import java.util.Arrays;

/** An hourly weather forecast for one place. The arrays line up index for index. */
public final class WindForecast {
    /** Start of each hour, in seconds since 1970 UTC. */
    public final long[] epochSeconds;
    public final double[] speedKmh;
    /** Direction the wind blows FROM, compass degrees. */
    public final double[] directionDeg;
    public final double[] gustKmh;
    /** Degrees Celsius. {@link Double#NaN} means Open-Meteo did not supply the hour. */
    public final double[] temperatureC;
    /** Millimetres in the hour. {@link Double#NaN} means unknown, not dry. */
    public final double[] precipitationMm;
    /** Percentage from 0 to 100. {@link Double#NaN} means unknown. */
    public final double[] precipitationProbabilityPercent;

    /**
     * Kept for callers that only have wind. The other weather values are
     * unknown rather than zero, because zero rain and zero degrees are real data.
     */
    public WindForecast(long[] epochSeconds, double[] speedKmh, double[] directionDeg,
                        double[] gustKmh) {
        this(epochSeconds, speedKmh, directionDeg, gustKmh,
                unknowns(epochSeconds.length), unknowns(epochSeconds.length),
                unknowns(epochSeconds.length));
    }

    public WindForecast(long[] epochSeconds, double[] speedKmh, double[] directionDeg,
                        double[] gustKmh, double[] temperatureC,
                        double[] precipitationMm, double[] precipitationProbabilityPercent) {
        this.epochSeconds = epochSeconds;
        this.speedKmh = speedKmh;
        this.directionDeg = directionDeg;
        this.gustKmh = gustKmh;
        this.temperatureC = temperatureC;
        this.precipitationMm = precipitationMm;
        this.precipitationProbabilityPercent = precipitationProbabilityPercent;
    }

    private static double[] unknowns(int length) {
        double[] values = new double[length];
        Arrays.fill(values, Double.NaN);
        return values;
    }

    /**
     * Index of the forecast hour closest to a moment in time. A moment before
     * the first hour or after the last gets the nearest end, which is the best
     * the data can offer.
     */
    public int indexAt(long momentEpochSeconds) {
        int best = 0;
        long bestGap = Long.MAX_VALUE;
        for (int i = 0; i < epochSeconds.length; i++) {
            long gap = Math.abs(epochSeconds[i] - momentEpochSeconds);
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }
}
