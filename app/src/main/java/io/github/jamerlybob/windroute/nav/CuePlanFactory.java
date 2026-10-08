package io.github.jamerlybob.windroute.nav;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.RideAnalysis;
import io.github.jamerlybob.windroute.elevation.ClimbDetector;
import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.route.RouteStep;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.wind.GustWarnings;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Builds the complete immutable cue plan before Android starts the service. */
public final class CuePlanFactory {
    private CuePlanFactory() { }

    public static List<CuePlanner.Event> build(RideAnalysis ride, Settings settings,
                                               CueBuilder words) {
        List<CuePlanner.Event> result = new ArrayList<>();
        if (settings.cueTurns) {
            List<Double> starts = new TurnGuide(ride.route).starts;
            result.addAll(CuePlanner.turnEvents(ride.route.steps, starts,
                    words, settings.cueAheadMeters));
        }
        for (int i = 0; settings.cueClimbs && i < ride.climbs.size(); i++) {
            ClimbDetector.Climb climb = ride.climbs.get(i);
            ClimbWind wind = ride.climbWinds.get(i);
            if (settings.lessTalk && !wind.isClimbIntoHeadwind) continue;
            double ahead = CuePlanner.CLIMB_EARLY_METERS * settings.cueAheadMeters / 300.0;
            double cueAt = Math.max(0, climb.startDistanceMeters - ahead);
            result.add(new CuePlanner.Event(CuePlanner.Kind.CLIMB, cueAt,
                    climb.startDistanceMeters + 50,
                    words.climb(Math.min(ahead, climb.startDistanceMeters), climb.lengthMeters,
                            climb.averageGradientPercent, wind.isClimbIntoHeadwind)));
            result.add(new CuePlanner.Event(CuePlanner.Kind.CLIMB_TOP, climb.endDistanceMeters(),
                    climb.endDistanceMeters() + 100, words.top()));
        }
        if (settings.cueWind && !settings.lessTalk) addWind(result, ride, words);
        if (settings.cueGusts && !settings.lessTalk) {
            for (GustWarnings.Warning gust : ride.gustWarnings) {
                double cueAt = Math.max(0, gust.startMeters - settings.cueAheadMeters);
                result.add(new CuePlanner.Event(CuePlanner.Kind.GUST, cueAt,
                        gust.startMeters + gust.lengthMeters,
                        words.gust(gust.side == GustWarnings.Side.LEFT, gust.lengthMeters)));
            }
        }
        return result;
    }

    private static void addWind(List<CuePlanner.Event> result, RideAnalysis ride,
                                CueBuilder words) {
        double start = 0;
        WindEffect previous = null;
        for (RouteWind.Stretch stretch : ride.wind.stretches) {
            if (stretch.effect != previous && stretch.effect != WindEffect.CALM) {
                double length = continuousLength(ride.wind.stretches, stretch);
                if (length >= CuePlanner.MIN_WIND_LENGTH_METERS) {
                    boolean toFinish = stretch.effect == WindEffect.TAILWIND
                            && start + length >= ride.wind.totalMeters - 50;
                    result.add(new CuePlanner.Event(CuePlanner.Kind.WIND, start,
                            start + length, words.wind(words.windLabel(
                                    stretch.effect == WindEffect.HEADWIND ? 0
                                            : stretch.effect == WindEffect.TAILWIND ? 1 : 2),
                                    length, toFinish)));
                }
            }
            previous = stretch.effect;
            start += stretch.lengthMeters;
        }
    }

    private static double continuousLength(List<RouteWind.Stretch> stretches,
                                           RouteWind.Stretch first) {
        int index = stretches.indexOf(first);
        double length = 0;
        for (int i = index; i < stretches.size(); i++) {
            RouteWind.Stretch next = stretches.get(i);
            if (next.effect != first.effect) break;
            length += next.lengthMeters;
        }
        return length;
    }

}
