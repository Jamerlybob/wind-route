package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import io.github.jamerlybob.windroute.nav.RouteProgress;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;

/** Projecting a live position onto the route, including crossings. */
public class RouteProgressTest {

    @Test
    public void projectsOntoTheMiddleOfASegmentNotJustAVertex() {
        List<GeoPoint> route = Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0.01));
        RouteProgress progress = RouteProgress.locate(route, new GeoPoint(0.001, 0.005));
        double total = GeoMath.distanceMeters(route.get(0), route.get(1));
        assertEquals(total / 2, progress.distanceAlongMeters, 0.5);
        assertEquals(total / 2, progress.distanceRemainingMeters, 0.5);
        assertEquals(111.2, progress.distanceOffRouteMeters, 0.5);
        assertTrue(progress.offRoute);
        assertEquals(0.005, progress.nearestPoint.lng, 1e-9);
    }

    @Test
    public void fortyMetresOrLessIsOnRoute() {
        List<GeoPoint> route = Arrays.asList(new GeoPoint(0, 0), new GeoPoint(0, 0.01));
        RouteProgress progress = RouteProgress.locate(route, new GeoPoint(0.0003, 0.005));
        assertFalse(progress.offRoute);
    }

    @Test
    public void previousProgressSelectsTheLaterArmAtACrossing() {
        List<GeoPoint> crossing = Arrays.asList(
                new GeoPoint(-0.001, -0.001),
                new GeoPoint(0.001, 0.001),
                new GeoPoint(0.001, -0.001),
                new GeoPoint(-0.001, 0.001));
        GeoPoint centre = new GeoPoint(0, 0);
        assertEquals(0, RouteProgress.locate(crossing, centre).segmentIndex);
        RouteProgress later = RouteProgress.locate(crossing, centre, 350);
        assertEquals(2, later.segmentIndex);
        assertTrue(later.distanceAlongMeters > 400);
    }

    @Test
    public void onePointRouteStillReportsDistanceOffRoute() {
        RouteProgress progress = RouteProgress.locate(
                Arrays.asList(new GeoPoint(0, 0)), new GeoPoint(0, 0.001));
        assertEquals(0, progress.distanceAlongMeters, 0);
        assertEquals(0, progress.distanceRemainingMeters, 0);
        assertTrue(progress.offRoute);
    }
}
