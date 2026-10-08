package io.github.jamerlybob.windroute.nav;

import java.util.List;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.WindEffect;
import io.github.jamerlybob.windroute.wind.WindMath;

/** Reads cached hourly wind for the rider's actual position and current time. */
public final class RideWind {
    public final WindEffect effect;
    public final double speedKmh;
    public final double headwindKmh;
    /** Rotation of an upward arrow showing where air is travelling, rider-relative. */
    public final double arrowDegrees;

    private RideWind(WindEffect effect, double speed, double headwind, double arrow) {
        this.effect = effect;
        speedKmh = speed;
        headwindKmh = headwind;
        arrowDegrees = arrow;
    }

    public static RideWind at(Route route, List<Integer> indexes, List<WindForecast> forecasts,
                              double progressMeters, double headingDegrees,
                              long epochSeconds, double calmBelowKmh) {
        if (indexes == null || forecasts == null || indexes.isEmpty()
                || indexes.size() != forecasts.size()) return null;
        double[] cumulative = GeoMath.cumulativeMeters(route.points);
        int best = 0;
        for (int i = 1; i < indexes.size(); i++) {
            if (Math.abs(cumulative[indexes.get(i)] - progressMeters)
                    < Math.abs(cumulative[indexes.get(best)] - progressMeters)) best = i;
        }
        WindForecast forecast = forecasts.get(best);
        if (forecast.epochSeconds.length == 0 || epochSeconds < forecast.epochSeconds[0]
                || epochSeconds >= forecast.epochSeconds[forecast.epochSeconds.length - 1] + 3600) {
            return null;
        }
        int hour = forecast.indexAt(epochSeconds);
        double from = forecast.directionDeg[hour];
        double speed = forecast.speedKmh[hour];
        // Forecast direction means FROM. Add 180 degrees to show where the
        // air moves, then subtract the rider heading because the map is heading up.
        double arrow = (from + 180 - headingDegrees + 720) % 360;
        return new RideWind(WindMath.classify(headingDegrees, from, speed, calmBelowKmh),
                speed, WindMath.headwindComponent(headingDegrees, from, speed), arrow);
    }
}
