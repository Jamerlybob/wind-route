package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.jamerlybob.windroute.nav.CueBuilder;
import io.github.jamerlybob.windroute.nav.CuePlanner;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.RouteStep;
import io.github.jamerlybob.windroute.settings.Settings;

/** Exact words and ordering for the safety-relevant spoken guidance rules. */
public final class CuePlannerTest {
    static CueBuilder words(Settings.DistanceUnit unit) {
        return new CueBuilder(new String[]{"%1$d metres", "%1$.1f miles", "%1$d feet",
                "%1$.1f kilometres", "In %1$s, %2$s", "Climb in %1$s. %2$s at %3$d percent.",
                "Into a headwind.", "%1$s for the next %2$s.", "%1$s from here to the finish.",
                "Strong crosswind from the %1$s for the next %2$s.", "left", "right",
                "%1$s, then %2$s", "Top of the climb.", "Continue", "Headwind", "Tailwind",
                "Crosswind", "Turn left", "Turn right"}, unit);
    }
    @Test public void distancesSoundLikeHumanSizedSteps() {
        assertEquals("350 metres", words(Settings.DistanceUnit.KILOMETERS).distance(326));
        assertEquals("1.2 kilometres", words(Settings.DistanceUnit.KILOMETERS).distance(1240));
        assertEquals("0.8 miles", words(Settings.DistanceUnit.MILES).distance(1280));
    }

    @Test public void simulatedRideSpeaksTurnsInExactOrder() {
        RouteStep left = new RouteStep(500, "TURN_LEFT", "Turn left onto Queen Street",
                new GeoPoint(0, 0), new GeoPoint(0, 0.01));
        List<CuePlanner.Event> events = CuePlanner.turnEvents(Collections.singletonList(left),
                Collections.singletonList(1000.0), words(Settings.DistanceUnit.KILOMETERS), 300);
        CuePlanner planner = new CuePlanner(events);
        assertNull(planner.next(699, 0));
        assertEquals("In 300 metres, turn left onto Queen Street", planner.next(700, 1));
        assertEquals("Turn left", planner.next(960, 2));
        assertNull(planner.next(1100, 3));
    }

    @Test public void turnsBeatNonTurnsAndNonTurnsWaitTenSeconds() {
        CuePlanner planner = new CuePlanner(Arrays.asList(
                new CuePlanner.Event(CuePlanner.Kind.WIND, 100, 2000, "Headwind."),
                new CuePlanner.Event(CuePlanner.Kind.TURN_NOW, 100, 150, "Turn right"),
                new CuePlanner.Event(CuePlanner.Kind.GUST, 110, 1000, "Crosswind.")));
        assertEquals("Turn right", planner.next(100, 100));
        assertEquals("Headwind.", planner.next(101, 101));
        assertNull(planner.next(110, 105));
        assertEquals("Crosswind.", planner.next(110, 111));
    }

    @Test public void staleQueuedCueIsDropped() {
        CuePlanner planner = new CuePlanner(Collections.singletonList(
                new CuePlanner.Event(CuePlanner.Kind.CLIMB, 100, 120, "Climb.")));
        assertNull(planner.next(121, 20));
        assertNull(planner.next(122, 40));
    }

    @Test public void closeTurnsAreChainedOnlyOnceAtEachStage() {
        GeoPoint point = new GeoPoint(0, 0);
        List<RouteStep> steps = Arrays.asList(
                new RouteStep(30, "TURN_LEFT", "Turn left onto Queen Street", point, point),
                new RouteStep(500, "TURN_RIGHT", "Turn right onto King Street", point, point));
        CuePlanner planner = new CuePlanner(CuePlanner.turnEvents(steps,
                Arrays.asList(1000.0, 1030.0), words(Settings.DistanceUnit.KILOMETERS), 300));
        assertEquals("In 300 metres, turn left onto Queen Street, then turn right onto King Street",
                planner.next(700, 0));
        assertEquals("Turn left, then turn right", planner.next(960, 1));
        assertNull(planner.next(990, 2));
    }

    @Test public void aLateFirstFixSkipsTheEarlyTurnAndSpeaksImmediate() {
        GeoPoint point = new GeoPoint(0, 0);
        CuePlanner planner = new CuePlanner(CuePlanner.turnEvents(Collections.singletonList(
                new RouteStep(500, "TURN_LEFT", "Turn left", point, point)),
                Collections.singletonList(1000.0), words(Settings.DistanceUnit.KILOMETERS), 300));
        assertEquals("Turn left", planner.next(980, 0));
        assertNull(planner.next(990, 1));
    }
}
