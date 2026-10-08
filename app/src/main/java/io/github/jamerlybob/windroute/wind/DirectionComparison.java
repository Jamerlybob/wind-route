package io.github.jamerlybob.windroute.wind;

/**
 * Decides whether the no-network reverse-direction estimate is worth showing.
 * Presentation code receives one clear decision instead of duplicating the
 * comparison rule, and no second Google route is requested for the estimate.
 */
public final class DirectionComparison {
    /** Smaller share changes tend to read as noise rather than useful advice. */
    public static final double MEANINGFUL_SHARE_CHANGE = 0.10;

    public final WindEffect effect;
    public final double share;
    public final boolean meaningful;

    private DirectionComparison(WindEffect effect, double share, boolean meaningful) {
        this.effect = effect;
        this.share = share;
        this.meaningful = meaningful;
    }

    public static DirectionComparison compare(RouteWind outward, RouteWind reverse) {
        WindEffect strongest = WindEffect.HEADWIND;
        double strongestShare = -1;
        for (WindEffect effect : WindEffect.values()) {
            double candidate = reverse.share(effect);
            if (candidate > strongestShare) {
                strongest = effect;
                strongestShare = candidate;
            }
        }
        double change = Math.abs(strongestShare - outward.share(strongest));
        return new DirectionComparison(strongest, strongestShare,
                change >= MEANINGFUL_SHARE_CHANGE);
    }
}
