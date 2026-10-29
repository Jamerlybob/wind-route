package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import io.github.jamerlybob.windroute.places.LastRouteText;

public class LastRouteTextTest {
    @Test public void importsShowOnlyTheirTrackName() {
        assertEquals("Last: Auckland to Hamilton",LastRouteText.format(
                "Last: %1$s to %2$s","Last: %1$s","Auckland to Hamilton","Finish",true));
    }

    @Test public void searchedRoutesUseBothShortPlaceNames() {
        assertEquals("Last: Mission Bay to Auckland Domain",LastRouteText.format(
                "Last: %1$s to %2$s","Last: %1$s","Mission Bay, Auckland, NZ","Auckland Domain, Auckland",false));
    }

    @Test public void importedTrackNamesKeepTheirCommas() {
        assertEquals("Last: North Island, day 2",LastRouteText.format(
                "Last: %1$s to %2$s","Last: %1$s"," North Island, day 2 ","Finish",true));
    }
}
