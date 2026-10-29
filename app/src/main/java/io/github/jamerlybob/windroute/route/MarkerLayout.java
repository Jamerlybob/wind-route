package io.github.jamerlybob.windroute.route;

/** Screen-space label and arrow clearance, testable without the Maps SDK. */
public final class MarkerLayout {
    private MarkerLayout() { }

    public static final class Box {
        public final double left, top, right, bottom;
        public Box(double left, double top, double right, double bottom) {
            this.left=left; this.top=top; this.right=right; this.bottom=bottom;
        }
        public boolean overlaps(Box other) {
            return left<other.right && right>other.left && top<other.bottom && bottom>other.top;
        }
        public boolean contains(Box other) {
            return other.left>=left && other.top>=top && other.right<=right && other.bottom<=bottom;
        }
    }

    public static boolean labelFits(Box label, Box viewport, Box search, Box sheet, Box otherLabel) {
        return viewport.contains(label) && !label.overlaps(search) && !label.overlaps(sheet)
                && (otherLabel==null || !label.overlaps(otherLabel));
    }

    public static boolean nearMarker(double x, double y, double markerX, double markerY, double clearance) {
        double dx=x-markerX, dy=y-markerY;
        return dx*dx+dy*dy <= clearance*clearance;
    }
}
