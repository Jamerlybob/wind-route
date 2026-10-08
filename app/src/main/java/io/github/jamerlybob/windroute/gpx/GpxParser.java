package io.github.jamerlybob.windroute.gpx;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import io.github.jamerlybob.windroute.route.GeoPoint;

/** Reads GPX tracks, routes and named waypoints without depending on Android. */
public final class GpxParser {

    private GpxParser() {
    }

    public static final class Waypoint {
        public final GeoPoint point;
        public final String name;
        public final Double elevationMeters;

        Waypoint(GeoPoint point, String name, Double elevationMeters) {
            this.point = point;
            this.name = name;
            this.elevationMeters = elevationMeters;
        }
    }

    public static final class Result {
        public final List<GeoPoint> points;
        /** One entry per point; Double.NaN means the GPX omitted that elevation. */
        public final List<Double> elevations;
        public final List<Waypoint> waypoints;

        Result(List<GeoPoint> points, List<Double> elevations, List<Waypoint> waypoints) {
            this.points = points;
            this.elevations = elevations;
            this.waypoints = waypoints;
        }
    }

    public static Result parse(String xml) throws IOException {
        return parse(new InputSource(new StringReader(xml)));
    }

    public static Result parse(InputStream input) throws IOException {
        return parse(new InputSource(input));
    }

    private static Result parse(InputSource input) throws IOException {
        try {
            DocumentBuilderFactory factory = secureFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // GPX files arrive from outside the app. Even if a parser ignores one
            // of the feature flags above, this resolver prevents an XML entity
            // from reading a file or URL from the rider's device.
            builder.setEntityResolver((publicId, systemId) ->
                    new InputSource(new StringReader("")));
            Document document = builder.parse(input);
            Element root = document.getDocumentElement();
            if (root == null || !"gpx".equals(localName(root))) {
                throw new IOException("This is not a GPX file.");
            }

            List<GeoPoint> points = new ArrayList<>();
            List<Double> elevations = new ArrayList<>();
            NodeList trackPoints = document.getElementsByTagNameNS("*", "trkpt");
            if (trackPoints.getLength() > 0) {
                addRoutePoints(trackPoints, points, elevations);
            } else {
                addRoutePoints(document.getElementsByTagNameNS("*", "rtept"),
                        points, elevations);
            }
            if (points.isEmpty()) {
                throw new IOException("This GPX file does not contain a track or route.");
            }

            List<Waypoint> waypoints = new ArrayList<>();
            NodeList waypointNodes = document.getElementsByTagNameNS("*", "wpt");
            for (int i = 0; i < waypointNodes.getLength(); i++) {
                Element element = (Element) waypointNodes.item(i);
                waypoints.add(new Waypoint(pointOf(element), childText(element, "name"),
                        optionalNumber(element, "ele")));
            }
            return new Result(points, elevations, waypoints);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Could not read this GPX file.", e);
        } catch (NumberFormatException e) {
            throw new IOException("This GPX file contains an invalid coordinate or elevation.", e);
        }
    }

    private static DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // DTDs and external entities are unnecessary in GPX and are dangerous:
        // a crafted imported file could otherwise ask the XML parser for local files.
        // Android and the desktop JVM ship different XML parsers. Android knows
        // very few Xerces/SAX features and can reject calls which work in a unit
        // test, so every extra hardening switch must be best-effort. The entity
        // resolver installed on the builder remains the portable last defence.
        setFeatureIfSupported(factory,
                "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory,
                "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory,
                "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
            // This hardening option is not part of Android's parser implementation.
        }
        try {
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException ignored) {
            // The empty entity resolver still prevents external entity loading.
        }
        return factory;
    }

    static void setFeatureIfSupported(DocumentBuilderFactory factory,
                                      String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException | UnsupportedOperationException ignored) {
            // An unsupported optional switch must not make every GPX import fail.
        }
    }

    private static void addRoutePoints(NodeList nodes, List<GeoPoint> points,
                                       List<Double> elevations) throws IOException {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            points.add(pointOf(element));
            Double elevation = optionalNumber(element, "ele");
            elevations.add(elevation == null ? Double.NaN : elevation);
        }
    }

    private static GeoPoint pointOf(Element element) throws IOException {
        if (!element.hasAttribute("lat") || !element.hasAttribute("lon")) {
            throw new IOException("A point in this GPX file is missing its coordinates.");
        }
        double lat = Double.parseDouble(element.getAttribute("lat"));
        double lng = Double.parseDouble(element.getAttribute("lon"));
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IOException("A point in this GPX file has coordinates outside the Earth.");
        }
        return new GeoPoint(lat, lng);
    }

    private static Double optionalNumber(Element parent, String childName) {
        String text = childText(parent, childName);
        return text.isEmpty() ? null : Double.parseDouble(text);
    }

    private static String childText(Element parent, String wantedName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && wantedName.equals(localName(child))) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private static String localName(Node node) {
        String local = node.getLocalName();
        if (local != null) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
