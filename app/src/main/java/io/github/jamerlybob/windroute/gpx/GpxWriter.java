package io.github.jamerlybob.windroute.gpx;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;

/** Writes a portable GPX 1.1 track for Garmin, Wahoo and other route tools. */
public final class GpxWriter {

    private GpxWriter() {
    }

    public static String write(String name, Route route) {
        return write(name, route.points, Collections.<Double>emptyList(),
                Collections.<GpxParser.Waypoint>emptyList());
    }

    public static String write(String name, List<GeoPoint> points) {
        return write(name, points, Collections.<Double>emptyList(),
                Collections.<GpxParser.Waypoint>emptyList());
    }

    public static String write(String name, List<GeoPoint> points, List<Double> elevations) {
        return write(name, points, elevations, Collections.<GpxParser.Waypoint>emptyList());
    }

    public static String write(String name, List<GeoPoint> points, List<Double> elevations,
                               List<GpxParser.Waypoint> waypoints) {
        if (!elevations.isEmpty() && elevations.size() != points.size()) {
            throw new IllegalArgumentException("Each GPX point needs a matching elevation.");
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<gpx version=\"1.1\" creator=\"WindRoute\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
                .append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
        for (GpxParser.Waypoint waypoint : waypoints) {
            xml.append(String.format(Locale.US, "  <wpt lat=\"%.7f\" lon=\"%.7f\">",
                    waypoint.point.lat, waypoint.point.lng));
            if (waypoint.elevationMeters != null && Double.isFinite(waypoint.elevationMeters)) {
                xml.append(String.format(Locale.US, "<ele>%.1f</ele>", waypoint.elevationMeters));
            }
            xml.append("<name>").append(escape(waypoint.name)).append("</name></wpt>\n");
        }
        xml.append("  <trk>\n")
                .append("    <name>").append(escape(name)).append("</name>\n")
                .append("    <trkseg>\n");
        for (int i = 0; i < points.size(); i++) {
            GeoPoint point = points.get(i);
            xml.append(String.format(Locale.US,
                    "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", point.lat, point.lng));
            if (!elevations.isEmpty() && elevations.get(i) != null
                    && Double.isFinite(elevations.get(i))) {
                xml.append(String.format(Locale.US, "<ele>%.1f</ele>", elevations.get(i)));
            }
            xml.append("</trkpt>\n");
        }
        xml.append("    </trkseg>\n")
                .append("  </trk>\n")
                .append("</gpx>\n");
        return xml.toString();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
