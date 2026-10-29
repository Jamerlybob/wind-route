package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.wind.*;

public class MockupPolishTest {
    private static final long START = 1800000000L;

    private List<DepartureScorer.Entry> departures() {
        Route route = new Route(Arrays.asList(new GeoPoint(0,0),new GeoPoint(.01,0)),1112,600,Collections.emptyList());
        long[] times = new long[25];
        double[] speeds = new double[25], directions = new double[25], gusts = new double[25];
        for (int i=0; i<25; i++) { times[i]=START+i*3600; speeds[i]=25-i; gusts[i]=30; }
        WindForecast forecast = new WindForecast(times,speeds,directions,gusts);
        return DepartureScorer.score(route,Collections.singletonList(0),Collections.singletonList(forecast),START,24);
    }

    @Test public void labelMarkerAndTapAllUseTheSameBestSlotAcrossMidnight() {
        List<DepartureScorer.Entry> entries = departures();
        int best = DepartureSlots.bestIndex(entries);
        assertEquals(23,best);
        assertEquals(START+23*3600,entries.get(best).departureEpochSeconds);
        for(int i=0;i<entries.size();i++) {
            float center = DepartureSlots.center(i,12,1056,entries.size());
            assertEquals(i,DepartureSlots.indexAt(center,12,1056,entries.size()));
        }
        assertEquals(best,DepartureSlots.indexAt(DepartureSlots.center(best,12,1056,24),12,1056,24));
    }

    @Test public void leavingNowKeepsTheCurrentHourSelectedEvenAfterHalfPast() {
        List<DepartureScorer.Entry> entries=departures();
        assertEquals(0,DepartureSlots.selectedIndex(entries,START+40*60));
        assertEquals(1,DepartureSlots.selectedIndex(entries,START+3600));
        assertEquals(23,DepartureSlots.selectedIndex(entries,START+28*3600));
        assertEquals(0,DepartureSlots.indexAt(-20,12,1056,24));
        assertEquals(23,DepartureSlots.indexAt(1200,12,1056,24));
        assertEquals(-1,DepartureSlots.bestIndex(Collections.emptyList()));
    }

    @Test public void badgesPointToWhereAirTravelsRelativeToTheRider() {
        assertEquals(180,WindPresentation.relativeArrow(0,0),0);
        assertEquals(0,WindPresentation.relativeArrow(180,0),0);
        assertEquals(270,WindPresentation.relativeArrow(0,270),0);
        Route route=new Route(Arrays.asList(new GeoPoint(0,0),new GeoPoint(.01,0)),1112,600,Collections.emptyList());
        WindForecast forecast=new WindForecast(new long[]{START,START+3600},new double[]{20,20},new double[]{0,0},new double[]{25,25});
        RouteWind wind=RouteWind.analyze(route,Collections.singletonList(0),Collections.singletonList(forecast),START);
        assertEquals(WindEffect.HEADWIND,WindPresentation.verdict(wind));
        assertEquals(180,WindPresentation.routeArrow(route,wind),1e-8);
    }

    @Test public void ordinaryWindIsWarmAgainstTheSeaAndTheRampClamps() {
        int color=WindPalette.color(33);
        assertTrue(((color>>16)&255)>(color&255));
        assertNotEquals(WindPalette.color(20),WindPalette.color(33));
        assertNotEquals(WindPalette.color(33),WindPalette.color(50));
        assertEquals(WindPalette.color(60),WindPalette.color(100));
    }

    @Test public void quietParticlesRespectTheChosenCalmThreshold() {
        assertTrue(WindPresentation.particleOpacity(4,5)<.04);
        assertTrue(WindPresentation.particleOpacity(10,12)<.04);
        assertEquals(1,WindPresentation.particleOpacity(30,5),0);
        assertTrue(WindPresentation.particleOpacity(15,5)<WindPresentation.particleOpacity(25,5));
    }

    @Test public void outlookUsesWholeHoursAndCallsSmallChangesSteady() {
        long time=START+38*60;
        assertEquals(0,WindOutlook.nextWholeHour(time)%3600);
        assertTrue(WindOutlook.nextWholeHour(time)>time);
        assertEquals(START+3600,WindOutlook.nextWholeHour(START));
        assertTrue(WindOutlook.steady(33,34));
        assertFalse(WindOutlook.steady(33,38));
        assertTrue(WindOutlook.easing(33,27));
    }
}
