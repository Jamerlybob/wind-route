package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import io.github.jamerlybob.windroute.places.PlaceNameFormatter;
import io.github.jamerlybob.windroute.trip.DayCardText;
import io.github.jamerlybob.windroute.weather.ForecastAge;
import io.github.jamerlybob.windroute.weather.RainDisplay;
import io.github.jamerlybob.windroute.route.MarkerLayout;
import io.github.jamerlybob.windroute.nav.DurationFormatter;

public class V4FixesTest {
    @Test public void summaryListsDistanceAscentAndWeatherOnce() {
        String summary=DayCardText.summary("%1$s / %2$s up / %3$s","57.8 km","503 m","dry");
        assertEquals("57.8 km / 503 m up / dry",summary);
        String details=DayCardText.details("%1$s / %2$s / %3$s / %4$s / %5$s%6$s",
                "Wind roughly cancels out","wind saves 10 min","18 C","6 AM","8 PM","");
        assertTrue(details.startsWith("Wind roughly cancels out"));
        assertFalse(details.contains("57.8 km"));
        assertFalse(details.contains("503 m"));
        assertFalse(details.contains("dry"));
    }

    @Test public void newlyFetchedForecastHasATwoMinuteGracePeriod() {
        long fetched=1000000;
        assertTrue(ForecastAge.justUpdated(fetched,fetched));
        assertTrue(ForecastAge.justUpdated(fetched,fetched+119999));
        assertFalse(ForecastAge.justUpdated(fetched,fetched+120000));
        assertFalse(ForecastAge.justUpdated(0,fetched));
        assertTrue(ForecastAge.justUpdated(fetched,fetched-1000));
    }

    @Test public void endpointUsesTheFirstPlaceAndGpxUsesStartOrFinish() {
        assertEquals("Mission Bay",PlaceNameFormatter.endpoint(" Mission Bay, Auckland, NZ ",false,"Start"));
        assertEquals("Auckland Domain",PlaceNameFormatter.endpoint("Auckland Domain",false,"Finish"));
        assertEquals("Start",PlaceNameFormatter.endpoint("Auckland to Hamilton",true,"Start"));
        assertEquals("Finish",PlaceNameFormatter.endpoint("Auckland to Hamilton",true,"Finish"));
        assertEquals("Start",PlaceNameFormatter.endpoint(null,false,"Start"));
        assertEquals("Finish",PlaceNameFormatter.endpoint(", Auckland",false,"Finish"));
    }

    @Test public void labelsMustFitAndAvoidThePillAndEachOther() {
        MarkerLayout.Box viewport=new MarkerLayout.Box(8,8,352,792);
        MarkerLayout.Box search=new MarkerLayout.Box(12,24,348,80);
        MarkerLayout.Box sheet=new MarkerLayout.Box(0,600,360,800);
        MarkerLayout.Box label=new MarkerLayout.Box(100,200,240,227);
        assertTrue(MarkerLayout.labelFits(label,viewport,search,sheet,null));
        assertFalse(MarkerLayout.labelFits(new MarkerLayout.Box(-20,200,120,227),viewport,search,sheet,null));
        assertFalse(MarkerLayout.labelFits(new MarkerLayout.Box(100,60,240,87),viewport,search,sheet,null));
        assertFalse(MarkerLayout.labelFits(label,viewport,search,sheet,new MarkerLayout.Box(120,205,260,232)));
        assertFalse(MarkerLayout.labelFits(new MarkerLayout.Box(100,610,240,637),viewport,search,sheet,null));
    }

    @Test public void arrowsKeepFortyPixelsOfMarkerClearance() {
        assertTrue(MarkerLayout.nearMarker(0,0,24,32,40));
        assertFalse(MarkerLayout.nearMarker(0,0,0,41,40));
    }

    @Test public void rainLikelihoodUsesTheSameSixtyAndTwentyPercentBoundaries() {
        assertEquals(RainDisplay.Outlook.LIKELY,RainDisplay.outlook(100,0));
        assertEquals(RainDisplay.Outlook.LIKELY,RainDisplay.outlook(60,0));
        assertEquals(RainDisplay.Outlook.POSSIBLE,RainDisplay.outlook(59.9,0));
        assertEquals(RainDisplay.Outlook.POSSIBLE,RainDisplay.outlook(20,0));
        assertEquals(RainDisplay.Outlook.DRY,RainDisplay.outlook(19.9,0));
        assertEquals(RainDisplay.Outlook.UNKNOWN,RainDisplay.outlook(Double.NaN,Double.NaN));
        assertEquals(RainDisplay.Outlook.POSSIBLE,RainDisplay.outlook(Double.NaN,1));
    }

    @Test public void stopDurationDoesNotRoundTheHoursSeparately() {
        assertEquals("2 h 5 min",DurationFormatter.format(125,"%d min","%d h %d min"));
        assertEquals("1 h 35 min",DurationFormatter.format(95,"%d min","%d h %d min"));
        assertEquals("2 h 0 min",DurationFormatter.format(119.6,"%d min","%d h %d min"));
    }
}
