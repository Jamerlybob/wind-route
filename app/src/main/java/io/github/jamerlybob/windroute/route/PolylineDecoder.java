package io.github.jamerlybob.windroute.route;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Google's "encoded polyline" format into points.
 *
 * <p>A route can be thousands of points, so Google squeezes it into one string of
 * printable characters. The scheme, for each coordinate in turn (lat, then lng):
 *
 * <ol>
 *   <li>Store the <i>difference</i> from the previous point rather than the value.
 *       Neighbouring points are close, so differences are small numbers.</li>
 *   <li>Multiply by 100,000 and round, giving an integer with 5 decimal places
 *       of precision (about one metre).</li>
 *   <li>Shift left one bit, and if the number was negative flip every bit. This
 *       moves the sign into the lowest bit so small negatives stay small.</li>
 *   <li>Chop the result into 5-bit chunks, lowest first. Set a sixth bit (0x20)
 *       on every chunk except the last, meaning "more chunks follow".</li>
 *   <li>Add 63 to each chunk so it lands on a printable ASCII character.</li>
 * </ol>
 *
 * <p>Decoding is those steps backwards.
 */
public final class PolylineDecoder {

    private PolylineDecoder() {
    }

    public static List<GeoPoint> decode(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        while (index < encoded.length()) {
            int[] latStep = readValue(encoded, index);
            lat += latStep[0];
            int[] lngStep = readValue(encoded, latStep[1]);
            lng += lngStep[0];
            index = lngStep[1];
            points.add(new GeoPoint(lat / 1e5, lng / 1e5));
        }
        return points;
    }

    /** Reads one signed value starting at index. Returns {value, nextIndex}. */
    private static int[] readValue(String encoded, int index) {
        int result = 0;
        int shift = 0;
        int chunk;
        do {
            chunk = encoded.charAt(index++) - 63;   // undo step 5
            result |= (chunk & 0x1f) << shift;      // low 5 bits are data
            shift += 5;
        } while (chunk >= 0x20);                    // sixth bit set: more to come
        // Undo step 3: the lowest bit is the sign.
        int value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{value, index};
    }
}
