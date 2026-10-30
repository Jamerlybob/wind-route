package io.github.jamerlybob.windroute.elevation;

import java.util.*;

/** Return original indexes so climb numbers and their wind facts stay paired. */
public final class ClimbSelection {
    private ClimbSelection() { }
    public static List<Integer> biggest(List<ClimbDetector.Climb> climbs,int limit) {
        List<Integer> indexes=new ArrayList<>();
        for (int i=0;i<climbs.size();i++) indexes.add(i);
        indexes.sort((a,b)->Double.compare(climbs.get(b).gainMeters,climbs.get(a).gainMeters));
        return new ArrayList<>(indexes.subList(0,Math.min(Math.max(0,limit),indexes.size())));
    }
}
