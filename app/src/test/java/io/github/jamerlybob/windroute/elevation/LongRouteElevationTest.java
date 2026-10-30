package io.github.jamerlybob.windroute.elevation;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class LongRouteElevationTest {
    @Test public void highestPointHasHeadroomAboveZeroAxis() {
        ProfileScale scale=ProfileScale.from(new double[]{0,15,250,1020,400,0});
        assertEquals(0,scale.low,0); assertTrue(scale.high>1020);
        assertTrue(scale.fraction(1020)<.95); assertTrue(scale.fraction(1020)>.7);
        assertEquals(0,scale.fraction(0),0);
    }
    @Test public void flatAndBelowSeaLevelProfilesHaveFiniteRange() {
        ProfileScale flat=ProfileScale.from(new double[]{0,0,0});
        assertTrue(flat.high>=10); assertTrue(Double.isFinite(flat.fraction(0)));
        ProfileScale below=ProfileScale.from(new double[]{-50,-10,25});
        assertEquals(-50,below.low,0); assertTrue(below.high>25);
    }
    @Test public void biggestThreeRetainOriginalIndexesAndWindPairing() {
        List<ClimbDetector.Climb> climbs=new ArrayList<>();
        for (double gain : new double[]{15,100,30,80,50}) climbs.add(new ClimbDetector.Climb(climbs.size()*1000,500,gain,5,8));
        assertEquals(Arrays.asList(1,3,4),ClimbSelection.biggest(climbs,3));
        assertEquals(15,climbs.get(0).gainMeters,0);
        assertEquals(5,ClimbSelection.biggest(climbs,20).size());
        assertTrue(ClimbSelection.biggest(Collections.emptyList(),3).isEmpty());
    }
}
