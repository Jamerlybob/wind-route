package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.elevation.ClimbDetector;
import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.elevation.ElevationClient;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.RouteWind;

/** Elevation requests, profile cleanup, climb detection and climb wind. */
public class ElevationLogicTest {

    @Test
    public void elevationReplyAndErrorAreReadable() throws IOException {
        assertEquals(38.0, ElevationClient.parse("{\"elevation\":[38.0,42.5]}")[0], 0);
        IOException error = assertThrows(IOException.class, () -> ElevationClient.parse(
                "{\"error\":true,\"reason\":\"Latitude is invalid.\"}"));
        assertEquals("Latitude is invalid.", error.getMessage());
    }

    @Test
    public void elevationUrlUsesDotsInEveryLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            String url = ElevationClient.buildUrl(Arrays.asList(
                    new GeoPoint(52.52, 13.405), new GeoPoint(-36.85, 174.76)));
            assertTrue(url.contains("latitude=52.520000,-36.850000"));
            assertTrue(url.contains("longitude=13.405000,174.760000"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void routeSamplesAreEvenAndCappedAtFiveRequests() {
        List<GeoPoint> oneKilometre = Arrays.asList(
                new GeoPoint(0, 0), new GeoPoint(0.009, 0));
        List<GeoPoint> samples = ElevationClient.sampleRoute(oneKilometre);
        assertEquals(12, samples.size());
        assertEquals(0.0, samples.get(0).lat, 0);
        assertEquals(0.009, samples.get(samples.size() - 1).lat, 1e-9);
        double firstGap = GeoMath.distanceMeters(samples.get(0), samples.get(1));
        double lastGap = GeoMath.distanceMeters(samples.get(10), samples.get(11));
        assertEquals(firstGap, lastGap, 0.1);

        List<GeoPoint> veryLong = Arrays.asList(
                new GeoPoint(0, 0), new GeoPoint(1, 0));
        assertEquals(500, ElevationClient.sampleRoute(veryLong).size());
    }

    @Test
    public void profileSmoothsASpikeAndIgnoresTinyWiggles() {
        ElevationProfile profile = new ElevationProfile(
                new double[]{0, 100, 200, 300, 400},
                new double[]{0, 10, 31, 20, 20.5});
        assertEquals((10 + 31 + 20) / 3.0, profile.elevationMeters[2], 1e-9);
        assertEquals(23.83, profile.totalAscentMeters, 0.01);
        assertEquals(3.33, profile.totalDescentMeters, 0.01);
        assertEquals(13.67, profile.gradientPercent[1], 0.01);
    }

    @Test
    public void profileCountsAContinuousGentleRiseButNotFlatRoadNoise() {
        double[] distance = new double[101];
        double[] gentleRise = new double[101];
        double[] flatNoise = new double[101];
        for (int i = 0; i < distance.length; i++) {
            distance[i] = i * 100.0;
            gentleRise[i] = i * 0.8;
            flatNoise[i] = i % 2 == 0 ? 0.5 : -0.5;
        }

        ElevationProfile rise = new ElevationProfile(distance, gentleRise);
        assertEquals(80, rise.totalAscentMeters, 1.0);
        assertEquals(rise.totalAscentMeters, rise.ascentBetween(0, 10_000), 0.01);

        ElevationProfile flat = new ElevationProfile(distance, flatNoise);
        assertEquals(0, flat.totalAscentMeters, 0);
        assertEquals(0, flat.totalDescentMeters, 0);
    }

    @Test
    public void detectsAClimbAcrossAShortDip() {
        double[] distance = {0, 100, 200, 300, 400, 500, 600, 700};
        double[] height = {0, 5, 10, 15, 14, 20, 25, 30};
        List<ClimbDetector.Climb> climbs =
                ClimbDetector.detect(new ElevationProfile(distance, height));
        assertEquals(1, climbs.size());
        ClimbDetector.Climb climb = climbs.get(0);
        assertEquals(700, climb.lengthMeters, 0.01);
        assertEquals(30, climb.gainMeters, 0.01);
        assertEquals(30.0 / 7.0, climb.averageGradientPercent, 0.01);
        assertTrue(climb.steepest100mGradientPercent >= climb.averageGradientPercent);
    }

    @Test
    public void rejectsShortOrGentleRises() {
        ElevationProfile shortRise = new ElevationProfile(
                new double[]{0, 100, 200, 300}, new double[]{0, 10, 20, 30});
        ElevationProfile gentle = new ElevationProfile(
                new double[]{0, 100, 200, 300, 400, 500},
                new double[]{0, 2, 4, 6, 8, 10});
        assertTrue(ClimbDetector.detect(shortRise).isEmpty());
        assertTrue(ClimbDetector.detect(gentle).isEmpty());
    }

    @Test
    public void gentleApproachIsNotIncludedInTheSteepClimb() {
        ElevationProfile profile = profileFromSections(
                new double[]{2_000, 1_000}, new double[]{1, 6});

        List<ClimbDetector.Climb> climbs = ClimbDetector.detect(profile);
        assertEquals(1, climbs.size());
        assertEquals(1_000, climbs.get(0).lengthMeters, 150);
        assertEquals(6, climbs.get(0).averageGradientPercent, 0.5);
    }

    @Test
    public void shortFlatJoinsTwoPartsOfAClimb() {
        ElevationProfile profile = profileFromSections(
                new double[]{1_000, 150, 1_000}, new double[]{5, 0, 5});

        List<ClimbDetector.Climb> climbs = ClimbDetector.detect(profile);
        assertEquals(1, climbs.size());
        assertEquals(2_150, climbs.get(0).lengthMeters, 150);
    }

    @Test
    public void longDescentSeparatesTwoClimbs() {
        ElevationProfile profile = profileFromSections(
                new double[]{1_000, 1_000, 1_000}, new double[]{5, -5, 5});

        List<ClimbDetector.Climb> climbs = ClimbDetector.detect(profile);
        assertEquals(2, climbs.size());
    }

    @Test
    public void longOnePercentRiseIsNotAClimb() {
        ElevationProfile profile = profileFromSections(
                new double[]{10_000}, new double[]{1});
        assertTrue(ClimbDetector.detect(profile).isEmpty());
    }

    private static ElevationProfile profileFromSections(double[] lengths, double[] gradients) {
        List<Double> distances = new ArrayList<>();
        List<Double> heights = new ArrayList<>();
        distances.add(0.0);
        heights.add(0.0);
        double distance = 0;
        double height = 0;
        for (int section = 0; section < lengths.length; section++) {
            double sectionEnd = distance + lengths[section];
            while (distance < sectionEnd) {
                double step = Math.min(50, sectionEnd - distance);
                distance += step;
                height += step * gradients[section] / 100.0;
                distances.add(distance);
                heights.add(height);
            }
        }

        double[] distanceArray = new double[distances.size()];
        double[] heightArray = new double[heights.size()];
        for (int i = 0; i < distances.size(); i++) {
            distanceArray[i] = distances.get(i);
            heightArray[i] = heights.get(i);
        }
        return new ElevationProfile(distanceArray, heightArray);
    }

    @Test
    public void climbWindIsWeightedOnlyAcrossTheClimb() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            points.add(new GeoPoint(i * 0.001, 0));
        }
        double[] cumulative = GeoMath.cumulativeMeters(points);
        Route route = new Route(points, cumulative[cumulative.length - 1], 600,
                Collections.<String>emptyList());
        WindForecast northerly = new WindForecast(new long[]{1_800_000_000L},
                new double[]{12}, new double[]{0}, new double[]{15});
        RouteWind wind = RouteWind.analyze(route, Collections.singletonList(0),
                Collections.singletonList(northerly), 1_800_000_000L);
        ClimbDetector.Climb climb = ClimbDetector.detect(new ElevationProfile(cumulative,
                new double[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50})).get(0);

        ClimbWind result = ClimbWind.analyze(climb, wind);
        assertEquals(12, result.averageHeadwindKmh, 0.01);
        assertTrue(result.isClimbIntoHeadwind);

        WindForecast southerly = new WindForecast(new long[]{1_800_000_000L},
                new double[]{12}, new double[]{180}, new double[]{15});
        RouteWind tailwind = RouteWind.analyze(route, Collections.singletonList(0),
                Collections.singletonList(southerly), 1_800_000_000L);
        assertFalse(ClimbWind.analyze(climb, tailwind).isClimbIntoHeadwind);
    }
}
