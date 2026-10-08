package io.github.jamerlybob.windroute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ClimbDetector;
import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.weather.RideWeather;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.DepartureScorer;
import io.github.jamerlybob.windroute.wind.GustWarnings;
import io.github.jamerlybob.windroute.wind.PowerModel;
import io.github.jamerlybob.windroute.wind.RouteWind;

/** One internally consistent snapshot used by every part of the ride screen. */
public final class RideAnalysis {
    public final Route route;
    public final long departureEpochSeconds;
    public final RouteWind wind;
    public final RouteWind reverseWind;
    public final RideWeather weather;
    public final List<DepartureScorer.Entry> departures;
    public final PowerModel.Result power;
    public final List<GustWarnings.Warning> gustWarnings;
    public final ElevationProfile elevation;
    public final List<ClimbDetector.Climb> climbs;
    public final List<ClimbWind> climbWinds;

    private RideAnalysis(Route route, long departureEpochSeconds, RouteWind wind,
                         RouteWind reverseWind, RideWeather weather,
                         List<DepartureScorer.Entry> departures, PowerModel.Result power,
                         List<GustWarnings.Warning> gustWarnings, ElevationProfile elevation,
                         List<ClimbDetector.Climb> climbs, List<ClimbWind> climbWinds) {
        this.route = route;
        this.departureEpochSeconds = departureEpochSeconds;
        this.wind = wind;
        this.reverseWind = reverseWind;
        this.weather = weather;
        this.departures = departures;
        this.power = power;
        this.gustWarnings = gustWarnings;
        this.elevation = elevation;
        this.climbs = climbs;
        this.climbWinds = climbWinds;
    }

    public static RideAnalysis build(Route originalRoute, List<Integer> sampleIndexes,
                                     List<WindForecast> forecasts, long departureEpochSeconds,
                                     Settings settings, ElevationProfile elevation) {
        return build(originalRoute, sampleIndexes, forecasts, departureEpochSeconds,
                settings, elevation, departureEpochSeconds);
    }

    /** Allows the screen's next-24-hours strip to stay anchored at the present. */
    public static RideAnalysis build(Route originalRoute, List<Integer> sampleIndexes,
                                     List<WindForecast> forecasts, long departureEpochSeconds,
                                     Settings settings, ElevationProfile elevation,
                                     long firstComparisonEpochSeconds) {
        long duration = UnitFormatter.ridingDurationSeconds(originalRoute.distanceMeters,
                originalRoute.durationSeconds, settings.ridingSpeedKmh);
        // This effective immutable route is the single source of timing for
        // wind, weather, scoring, gusts and the power estimate.
        Route route = originalRoute.withDuration(duration);
        RouteWind wind = RouteWind.analyze(route, sampleIndexes, forecasts,
                departureEpochSeconds, duration, settings.calmBelowKmh);
        RouteWind reverse = RouteWind.analyzeReversed(route, sampleIndexes, forecasts,
                departureEpochSeconds, duration, settings.calmBelowKmh);
        // Weather uses the same arrival time at every sample as wind. Separate
        // timing could describe rain from one hour beside wind from another.
        RideWeather weather = RideWeather.analyze(route, sampleIndexes, forecasts,
                departureEpochSeconds);
        long firstHour = firstComparisonEpochSeconds - firstComparisonEpochSeconds % 3600;
        List<DepartureScorer.Entry> departures = DepartureScorer.score(route, sampleIndexes,
                forecasts, firstHour, DepartureScorer.DEFAULT_HOURS, settings.calmBelowKmh);
        List<GustWarnings.Warning> gusts = GustWarnings.find(wind, route, sampleIndexes,
                forecasts, departureEpochSeconds);
        List<ClimbDetector.Climb> climbs = elevation == null
                ? Collections.emptyList() : ClimbDetector.detect(elevation);
        List<ClimbWind> climbWinds = new ArrayList<>();
        // Preserve list order: the screen pairs climb i with climb-wind i when
        // colouring the profile and building its text list.
        for (ClimbDetector.Climb climb : climbs) {
            climbWinds.add(ClimbWind.analyze(climb, wind));
        }
        return new RideAnalysis(route, departureEpochSeconds, wind, reverse, weather,
                departures, PowerModel.estimate(route, wind), gusts, elevation,
                Collections.unmodifiableList(climbs), Collections.unmodifiableList(climbWinds));
    }

    /** True only when every sample covers both leaving and finishing the ride. */
    public static boolean forecastCovers(List<WindForecast> forecasts,
                                         long departureEpochSeconds, long durationSeconds) {
        if (forecasts == null || forecasts.isEmpty()) {
            return false;
        }
        long arrival = departureEpochSeconds + Math.max(0, durationSeconds);
        for (WindForecast forecast : forecasts) {
            if (forecast.epochSeconds.length == 0
                    || departureEpochSeconds < forecast.epochSeconds[0]
                    || arrival > forecast.epochSeconds[forecast.epochSeconds.length - 1] + 3600) {
                return false;
            }
        }
        return true;
    }
}
