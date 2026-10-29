package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import io.github.jamerlybob.windroute.trip.*;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.wind.*;
import io.github.jamerlybob.windroute.weather.*;
import io.github.jamerlybob.windroute.poi.*;

public class EasyBikepackingTest {
    @Test public void stopPositionFollowsRoad() {
        List<GeoPoint> route=Arrays.asList(new GeoPoint(0,0),new GeoPoint(0,1),new GeoPoint(1,1));
        double first=GeoMath.distanceMeters(route.get(0),route.get(1));
        GeoPoint stop=StopPicker.pointAt(route,first/2);
        assertEquals(0,stop.lat,0);assertEquals(.5,stop.lng,1e-6);
        assertEquals(1,StopPicker.pointAt(route,1e9).lat,0);
    }
    @Test public void snapPrefersCampingAndHonoursFiveKmLimit() {
        Poi food=new Poi(PoiKind.FOOD,"Food",new GeoPoint(0,0),null);
        Poi camp=new Poi(PoiKind.CAMPING,"Camp",new GeoPoint(0,0),null);
        List<PoiAlongRoute> places=Arrays.asList(new PoiAlongRoute(food,50100,0),new PoiAlongRoute(camp,54000,0));
        assertEquals(54000,StopPicker.snap(50000,100000,places),0);
        assertEquals("Camp",StopPicker.nameAt(54000,places));
        assertEquals(70000,StopPicker.snap(70000,100000,places),0);
        assertEquals(100000,StopPicker.snap(200000,100000,Collections.emptyList()),0);
    }
    @Test public void townNeedsFoodAndRuralCafeAloneIsNotAnOvernightStop() {
        Poi town = new Poi(PoiKind.TOWN,"Town",new GeoPoint(0,.45),null);
        Poi food = new Poi(PoiKind.FOOD,"Store",new GeoPoint(0,.46),null);
        PoiAlongRoute settlement = new PoiAlongRoute(town,51000,0);
        PoiAlongRoute store = new PoiAlongRoute(food,52000,0);
        assertEquals(50000,StopPicker.snap(50000,100000,Collections.singletonList(store)),0);
        assertEquals(50000,StopPicker.snap(50000,100000,Collections.singletonList(settlement)),0);
        assertEquals(51000,StopPicker.snap(50000,100000,Arrays.asList(settlement,store)),0);
    }
    @Test public void windTimingAndTimeTargetAreConsistent() {
        Route route=new Route(Arrays.asList(new GeoPoint(0,0),new GeoPoint(0,.9)),100000,20000,Collections.emptyList());
        WindForecast weather=new WindForecast(new long[]{0,86400},new double[]{25,25},new double[]{90,90},new double[]{25,25});
        RouteWind wind=RouteWind.analyze(route,Arrays.asList(0,1),Arrays.asList(weather,weather),0);
        double minutes=StopPicker.minutesTo(50000,route,wind);
        assertTrue(minutes>route.durationSeconds/120.0);
        assertEquals(50000,StopPicker.distanceForMinutes(minutes,route,wind),.01);
    }
    @Test public void dayAdviceUsesOnlyCoveredForecasts() {
        Route route = new Route(Arrays.asList(new GeoPoint(0,0),new GeoPoint(.2,0)),22000,3600,Collections.emptyList());
        List<Integer> indexes = Arrays.asList(0,1);
        WindForecast forecast = new WindForecast(new long[]{0,3600,86400,90000},
                new double[]{25,25,10,10},new double[]{0,0,0,0},new double[]{25,25,10,10});
        List<WindForecast> forecasts = Arrays.asList(forecast,forecast);
        RouteWind today = RouteWind.analyze(route,indexes,forecasts,0);
        RideWeather weather = RideWeather.analyze(route,indexes,forecasts,0);
        DayAdvice advice = DayAdvice.compare(route,today,weather,indexes,forecasts,0,5);
        assertTrue(advice.badDay);
        assertEquals(DayAdvice.Suggestion.LESS_HEADWIND,advice.suggestion);
        assertEquals(10,advice.laterHeadwindKmh,.01);
        assertEquals(DayAdvice.Suggestion.NONE,DayAdvice.compare(route,today,weather,indexes,forecasts,86400,5).suggestion);
    }
    @Test public void snappedDayBoundariesStayOrdered() {
        Route route = new Route(Arrays.asList(new GeoPoint(0,0),new GeoPoint(0,1)),100000,20000,Collections.emptyList());
        Poi camp = new Poi(PoiKind.CAMPING,"Camp",new GeoPoint(0,.5),null);
        double[] boundaries = TripPlanner.snapBoundaries(route,new double[]{50000,52000},
                Collections.singletonList(new PoiAlongRoute(camp,51000,0)));
        assertTrue(boundaries[0] < boundaries[1]);
        assertEquals(51000,boundaries[0],0);
    }
    @Test public void daySentenceUsesTranslatedTemplate() {
        assertEquals("68 km / tailwind / dry",DayVerdict.sentence("%1$s / %2$s / %3$s","68 km","tailwind","dry"));
        assertTrue(DayVerdict.suggestDrier(5,1));assertFalse(DayVerdict.suggestDrier(5,4));
    }
    @Test public void lateDepartureStillUsesTodaysSunset() {
        List<DaylightForecast> lights=Arrays.asList(
                new DaylightForecast(0,6*3600,18*3600),
                new DaylightForecast(86400,30*3600,42*3600));
        assertEquals(Long.valueOf(18*3600),StopPicker.sunsetFor(lights,23*3600));
        assertEquals(Long.valueOf(42*3600),StopPicker.sunsetFor(lights,25*3600));
        assertNull(StopPicker.sunsetFor(lights,7*86400));
    }
    @Test public void campingLookupStaysInASmallBox() {
        String query=OverpassClient.buildCampingBoxQuery(new GeoPoint(-37,175));
        assertTrue(query.contains("camp_site|caravan_site"));
        assertTrue(query.contains("out center tags"));
        assertFalse(query.contains("around:"));
    }
    @Test public void sunsetAndLaterSuggestionHaveClearThresholds() {
        assertEquals(40,StopPicker.minutesAfterSunset(4800,2400));
        assertEquals(0,StopPicker.minutesAfterSunset(1200,2400));
        assertTrue(DayVerdict.bad(25,0));assertTrue(DayVerdict.bad(0,3));assertFalse(DayVerdict.bad(5,0));
        assertTrue(DayVerdict.suggestLater(25,10));assertFalse(DayVerdict.suggestLater(10,9));
    }
}
