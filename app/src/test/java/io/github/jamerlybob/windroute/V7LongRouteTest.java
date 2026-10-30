package io.github.jamerlybob.windroute;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import io.github.jamerlybob.windroute.poi.*;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.wind.*;
import io.github.jamerlybob.windroute.weather.*;

public class V7LongRouteTest {
    @Test public void aucklandHamiltonCameraGetsFullCoarseGrid() {
        WindGridArea area=WindGridArea.camera(-37.9,174.6,-36.7,175.6);
        assertTrue(area.supported);
        assertTrue(area.south < -37.9 && area.north > -36.7);
        assertTrue(area.west < 174.6 && area.east > 175.6);
        List<GeoPoint> grid=WindGridClient.points(area.south,area.west,area.north,area.east);
        assertEquals(100,grid.size());
        assertTrue(GeoMath.distanceMeters(grid.get(0),grid.get(1))>5000);
        List<WindForecast> forecasts=new ArrayList<>();
        for (int i=0;i<100;i++) forecasts.add(new WindForecast(new long[]{0},new double[]{20},new double[]{270},new double[]{30}));
        WindField field=new WindField(area.south,area.west,area.north,area.east,10,10,forecasts);
        assertTrue(area.coveredBy(field));
        assertTrue(field.covers(-37.9,174.6)); assertTrue(field.covers(-36.7,175.6));
        assertEquals(20,field.at(-37.3,175.1,0).speed(),.001);
        assertTrue(WindGridArea.camera(-39,173,-36,177).supported); // Around 350 km across.
        assertFalse(WindGridArea.camera(-70,-100,70,100).supported);
    }
    @Test public void cameraAcrossDateLineStaysNarrow() {
        WindGridArea area=WindGridArea.camera(-37,179.5,-36,-179.5);
        assertTrue(area.supported); assertTrue(area.east-area.west<2);
        assertTrue(WindGridArea.camera(-36.86,174.75,-36.84,174.77).supported);
    }
    @Test public void sparse115KmRouteSplitsIntoFiveContinuousPieces() {
        List<GeoPoint> route=Arrays.asList(new GeoPoint(0,0),new GeoPoint(0,1.035));
        List<List<GeoPoint>> pieces=RoutePieces.split(route);
        assertEquals(5,pieces.size());
        double total=0;
        for (int i=0;i<pieces.size();i++) {
            List<GeoPoint> piece=pieces.get(i);
            double length=GeoMath.cumulativeMeters(piece)[piece.size()-1];
            assertTrue(length<=25000.001); assertTrue(length>0); total+=length;
            if (i>0) assertEquals(0,GeoMath.distanceMeters(pieces.get(i-1).get(pieces.get(i-1).size()-1),piece.get(0)),.001);
        }
        assertEquals(GeoMath.distanceMeters(route.get(0),route.get(1)),total,.001);
    }
    @Test public void denseRouteAndDateLineDoNotLoseTheirEnds() {
        List<GeoPoint> route=new ArrayList<>();
        for (int i=0;i<=1000;i++) route.add(new GeoPoint(-37,174+i*.001));
        List<List<GeoPoint>> pieces=RoutePieces.split(route);
        assertEquals(0,GeoMath.distanceMeters(route.get(1000),pieces.get(pieces.size()-1).get(pieces.get(pieces.size()-1).size()-1)),.001);
        for (List<GeoPoint> piece:pieces) assertTrue(GeoMath.cumulativeMeters(piece)[piece.size()-1]<=25000.001);
        pieces=RoutePieces.split(Arrays.asList(new GeoPoint(0,179.5),new GeoPoint(0,-179.5)));
        assertEquals(5,pieces.size());
        for (List<GeoPoint> piece:pieces) for (GeoPoint point:piece) assertTrue(Math.abs(point.lng)>179);
        assertEquals(1,RoutePieces.split(Collections.singletonList(new GeoPoint(0,0))).size());
    }
    @Test public void successfulPiecesSurviveBusyAndNetworkErrors() throws IOException {
        int[] calls={0}, progress={0};
        Poi water=new Poi(PoiKind.WATER,"Tap",new GeoPoint(0,0),null);
        OverpassClient.Result result=OverpassClient.lookup(Arrays.asList(new GeoPoint(0,0),new GeoPoint(0,1.035)),
                Collections.singleton(PoiKind.WATER),500,(piece,total,partial)->{
                    assertEquals(5,total); assertTrue(piece>=1 && piece<=5); progress[0]++;
                },query->{
                    assertTrue(query.contains("[timeout:60]"));
                    calls[0]++;
                    if (calls[0]==2) throw new OverpassClient.BusyException();
                    if (calls[0]==4) throw new IOException("offline");
                    return Collections.singletonList(water);
                });
        assertEquals(5,calls[0]); assertEquals(10,progress[0]);
        assertEquals(1,result.places.size()); // Seam duplicates merge.
        assertEquals(1,result.busyFailures); assertEquals(1,result.networkFailures);
        assertEquals(5,result.completed); assertTrue(result.failed());
        assertTrue(OverpassClient.isBusyStatus(429)); assertTrue(OverpassClient.isBusyStatus(504));
        assertFalse(OverpassClient.isBusyStatus(500));
    }
    @Test public void runtimeTimeoutAndNodeCoordinateOutputAreHandled() throws IOException {
        try { OverpassClient.parse("{\"remark\":\"runtime error: Query timed out\",\"elements\":[]}"); fail(); }
        catch (OverpassClient.BusyException expected) { }
        List<Poi> places=OverpassClient.parse("{\"elements\":[{\"type\":\"node\",\"id\":1,\"tags\":{\"amenity\":\"drinking_water\"}},"
                +"{\"type\":\"node\",\"id\":1,\"lat\":0,\"lon\":0,\"tags\":{\"amenity\":\"drinking_water\"}}]}");
        assertEquals(1,places.size());
    }
}
