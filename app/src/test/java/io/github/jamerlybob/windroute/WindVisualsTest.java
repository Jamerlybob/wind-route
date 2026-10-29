package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import io.github.jamerlybob.windroute.wind.*;
import io.github.jamerlybob.windroute.weather.*;
import io.github.jamerlybob.windroute.route.*;

public class WindVisualsTest {
    private WindForecast forecast(double from) {
        return new WindForecast(new long[]{0,3600},new double[]{20,20},new double[]{from,from},new double[]{25,25});
    }
    @Test public void northWrapInterpolatesAcrossNorth() {
        WindForecast forecast=new WindForecast(new long[]{0,3600},new double[]{20,20},
                new double[]{350,10},new double[]{25,25});
        WindVector midpoint=forecast.windAt(1800);
        assertEquals(0,midpoint.east,1e-9);
        assertTrue(midpoint.north<0);
        assertEquals(0,midpoint.fromDegrees(),1e-8);
    }
    @Test public void fieldMixesBothAxesAndTime() {
        WindField field=new WindField(0,0,2,2,2,2,Arrays.asList(forecast(350),forecast(10),forecast(350),forecast(10)));
        assertEquals(0,field.at(1,1,1800).east,1e-9);
        assertTrue(field.at(1,1,1800).north<0);
        assertTrue(field.covers(2,2)); assertFalse(field.covers(3,1));
    }
    @Test public void fieldCrossesTheDateLine() {
        WindField field = new WindField(-10,170,10,190,2,2,
                Arrays.asList(forecast(270),forecast(270),forecast(270),forecast(270)));
        assertTrue(field.covers(0,-175));
        assertFalse(field.covers(0,-140));
        assertEquals(20,field.at(0,-175,0).east,1e-8);
        for (GeoPoint point : WindGridClient.points(-10,170,10,190)) {
            assertTrue(point.lng >= -180 && point.lng <= 180);
        }
    }
    @Test public void fieldUsesBilinearWeights() {
        WindForecast zero=new WindForecast(new long[]{0},new double[]{0},new double[]{0},new double[]{0});
        WindForecast east=new WindForecast(new long[]{0},new double[]{40},new double[]{270},new double[]{40});
        WindField field=new WindField(0,0,2,2,2,2,Arrays.asList(zero,zero,zero,east));
        assertEquals(10,field.at(1,1,0).east,1e-8);
        assertEquals(30,field.at(2,1.5,0).east,1e-8);
    }
    @Test public void quarterHoursFallBackToHourlyBeyondCoverage() {
        WindForecast hourly=forecast(0);
        hourly.quarterHourlyWind=new WindForecast(new long[]{0,900},new double[]{10,30},new double[]{90,90},new double[]{10,30});
        assertEquals(20,hourly.windAt(450).speed(),1e-8);
        assertEquals(20,hourly.windAt(1800).speed(),1e-8);
        assertEquals(0,hourly.windAt(1800).fromDegrees(),1e-8);
    }
    @Test public void particlesMoveAndRespawnAtLifetimeOrBounds() {
        WindParticle particle=new WindParticle();particle.x=10;particle.y=10;particle.lifetime=2;
        particle.step(4,2,.5);assertEquals(12,particle.x,0);assertEquals(9,particle.y,0);
        assertFalse(particle.needsRespawn(100,100));particle.step(0,0,2);assertTrue(particle.needsRespawn(100,100));
        particle.age=0;particle.x=-1;assertTrue(particle.needsRespawn(100,100));
    }
    @Test public void paletteClampsAndStrengthens() {
        assertEquals(WindPalette.color(0),WindPalette.color(-1));
        assertEquals(WindPalette.color(60),WindPalette.color(200));
        assertNotEquals(WindPalette.color(5),WindPalette.color(30));
        assertEquals(0xff000000,WindPalette.color(10)&0xff000000);
    }
    @Test public void routeSamplingIsDenseAndBounded() {
        List<GeoPoint> points=new ArrayList<>();
        for(int i=0;i<3000;i++)points.add(new GeoPoint(0,i*.001));
        List<Integer> samples=RouteSamples.indexes(points);
        assertTrue(samples.size()<=100);assertEquals(Integer.valueOf(0),samples.get(0));
        assertEquals(Integer.valueOf(2999),samples.get(samples.size()-1));
        List<Integer> shortRoute=RouteSamples.indexes(points.subList(0,501));
        assertTrue(shortRoute.size()>=25);
    }
    @Test public void routeInterpolatesAcrossAnHourBoundary() {
        Route route=new Route(Arrays.asList(new GeoPoint(0,0),new GeoPoint(0,.02)),2224,3600,Collections.emptyList());
        WindForecast a=new WindForecast(new long[]{0,3600},new double[]{10,30},new double[]{270,270},new double[]{30,30});
        RouteWind wind=RouteWind.analyze(route,Arrays.asList(0,1),Arrays.asList(a,a),1800);
        assertEquals(30,wind.averageWindKmh,1e-6);
    }
    @Test public void outlookCompassWraps() {
        assertEquals(0,WindOutlook.compass(350));assertEquals(0,WindOutlook.compass(10));
        assertEquals(5,WindOutlook.compass(225));assertTrue(WindOutlook.easing(22,12));
        assertFalse(WindOutlook.easing(12,22));
    }
    @Test public void outlookSentenceUsesTranslatedTemplate() {
        assertEquals("SW 22 easing 12 3pm NE",WindOutlook.sentence("%1$s %2$s %3$s %4$s %5$s %6$s","SW","22","easing","12","3pm","NE"));
    }
    @Test public void gridUrlIsOneHundredLocationsAndHourlyOnly() {
        String url=WindGridClient.buildUrl(-37,174,-36,175);
        assertEquals(100,WindGridClient.points(-37,174,-36,175).size());
        assertTrue(url.contains("timezone=GMT"));assertTrue(url.contains("timeformat=unixtime"));
        assertFalse(url.contains("minutely_15"));
    }
    @Test public void savedGridReplyParses() throws Exception {
        String reply;
        try (java.io.InputStream input = getClass().getResourceAsStream("/wind-grid.json")) {
            assertNotNull(input);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            reply = bytes.toString("UTF-8");
        }
        assertEquals(0,WindGridClient.parse(reply,0,0,1,1).at(.5,.5,1800).east,1e-9);
    }
}
