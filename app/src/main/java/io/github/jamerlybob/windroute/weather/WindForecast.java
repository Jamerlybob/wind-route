package io.github.jamerlybob.windroute.weather;

/** An hourly wind forecast for one place. The arrays line up index for index. */
public final class WindForecast {
    /** Start of each hour, in seconds since 1970 UTC. */
    public final long[] epochSeconds;
    public final double[] speedKmh;
    /** Direction the wind blows FROM, compass degrees. */
    public final double[] directionDeg;
    public final double[] gustKmh;

    public WindForecast(long[] epochSeconds, double[] speedKmh, double[] directionDeg,
                        double[] gustKmh) {
        this.epochSeconds = epochSeconds;
        this.speedKmh = speedKmh;
        this.directionDeg = directionDeg;
        this.gustKmh = gustKmh;
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
