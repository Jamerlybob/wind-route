package io.github.jamerlybob.windroute.elevation;

/** Zero-based terrain axes with a little air above even the highest peak. */
public final class ProfileScale {
    public final double low, high;
    private ProfileScale(double low,double high) { this.low=low; this.high=high; }
    public static ProfileScale from(double[] heights) {
        double low=0, peak=0;
        for (double height : heights) if (Double.isFinite(height)) {
            low=Math.min(low,height); peak=Math.max(peak,height);
        }
        // At least ten metres prevents a flat coast from consuming the graph.
        // Round upwards, never to the nearest tick (which can cut a peak off).
        double padded=peak+Math.max(10,(peak-low)*.1);
        double step=Math.pow(10,Math.floor(Math.log10(Math.max(10,padded-low))))/5;
        return new ProfileScale(low,Math.ceil(padded/step)*step);
    }
    public double fraction(double height) {
        if (!Double.isFinite(height)) return 0;
        return Math.max(0,Math.min(1,(height-low)/(high-low)));
    }
}
