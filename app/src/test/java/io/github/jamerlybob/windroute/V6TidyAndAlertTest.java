package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import io.github.jamerlybob.windroute.poi.*;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.weather.ForecastAge;
import io.github.jamerlybob.windroute.alerts.RideAlert;

public class V6TidyAndAlertTest {
    private PoiAlongRoute place(PoiKind kind, String name, double latitude, String hours) {
        return new PoiAlongRoute(new Poi(kind, name, new GeoPoint(latitude, 174), hours), 100, 5);
    }
    @Test public void unnamedUsesKindAndNamedKeepsName() {
        assertEquals("Drinking water", PlacePresentation.name(place(PoiKind.WATER,"  ",0,null).poi,"Drinking water"));
        assertEquals("Cafe", PlacePresentation.name(place(PoiKind.FOOD," Cafe ",0,null).poi,"Food"));
        assertEquals("Toilets", PlacePresentation.name(place(PoiKind.TOILETS,null,0,null).poi,"Toilets"));
    }
    @Test public void onlyIdenticalNearbyUnnamedCollapse() {
        List<PoiAlongRoute> places = Arrays.asList(place(PoiKind.WATER,"",0,null),
                place(PoiKind.WATER,null,.0002,null), place(PoiKind.WATER,"",.001,null),
                place(PoiKind.FOOD,"",0,null), place(PoiKind.WATER,"Named",0,null),
                place(PoiKind.WATER,"",0,"9-5"));
        assertEquals(5, PlacePresentation.collapse(places).size());
        assertEquals(6, places.size());
    }
    @Test public void savedAgeSaysThreeHours() {
        assertEquals("Forecast from 3 h ago", ForecastAge.wording(1000,1000+3*3600000L,
                "Forecast from %s ago", "%d min", "%d h", "%d days"));
        assertEquals("2 days", ForecastAge.wording(1000,1000+48*3600000L,"%s","%d min","%d h","%d days"));
        assertTrue(ForecastAge.justUpdated(1000, 900));
    }
    @Test public void goodRequiresKnownDryWeatherAndModestWind() {
        assertEquals(RideAlert.Decision.GOOD,RideAlert.decide(-5,20,0,10,false));
        assertEquals(RideAlert.Decision.NONE,RideAlert.decide(0,20,Double.NaN,10,false));
        assertEquals(RideAlert.Decision.NONE,RideAlert.decide(0,45,0,10,false));
        assertEquals(RideAlert.Decision.NONE,RideAlert.decide(6,20,0,10,false));
        assertEquals(RideAlert.Decision.NONE,RideAlert.decide(0,20,.1,10,false));
        assertEquals(RideAlert.Decision.NONE,RideAlert.decide(0,20,0,20,false));
    }
    @Test public void badDayIsOptInAndCanUseEitherKnownRainOrWind() {
        assertEquals(RideAlert.Decision.NONE,RideAlert.decide(20,20,0,0,false));
        assertEquals(RideAlert.Decision.BAD,RideAlert.decide(20,20,0,0,true));
        assertEquals(RideAlert.Decision.BAD,RideAlert.decide(0,20,2,0,true));
        assertEquals(RideAlert.Decision.BAD,RideAlert.decide(0,20,Double.NaN,60,true));
    }
    @Test public void sentenceIncludesOnlyKnownTemperature() {
        assertEquals("Tomorrow 7 AM looks good: mostly tailwind, dry, 14 C",
                RideAlert.sentence("Tomorrow %s looks good: %s","7 AM","mostly tailwind, dry","14 C",", %s"));
        assertEquals("Tomorrow 07:30 looks difficult: rain likely",
                RideAlert.sentence("Tomorrow %s looks difficult: %s","07:30","rain likely","",", %s"));
    }
    @Test public void calendarUsesLocalTomorrowAcrossDaylightSaving() {
        TimeZone zone = TimeZone.getTimeZone("Pacific/Auckland");
        Calendar now = Calendar.getInstance(zone); now.clear(); now.set(2026, Calendar.SEPTEMBER,26,19,0);
        Calendar tomorrow = Calendar.getInstance(zone);
        tomorrow.setTimeInMillis(RideAlert.tomorrow(now.getTimeInMillis(),7,30,zone)*1000);
        assertEquals(27,tomorrow.get(Calendar.DAY_OF_MONTH));
        assertEquals(7,tomorrow.get(Calendar.HOUR_OF_DAY));
        assertEquals(30,tomorrow.get(Calendar.MINUTE));
        assertEquals(23*3600000L, RideAlert.nextEvening(now.getTimeInMillis(),zone)-now.getTimeInMillis());
    }
}
