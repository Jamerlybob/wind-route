package io.github.jamerlybob.windroute.wind;

/** Shared opaque speed ramp, readable over either map theme. No Android dependency. */
public final class WindPalette {
    private static final int[] SPEED_COLORS = {
            0xffd5f5ed, 0xff29b59a, 0xfff1be42, 0xffed6344, 0xffb3376e, 0xff7542b5
    };
    private WindPalette() { }
    public static int color(double speed) {
        // Most ordinary cycling days span several hues, rather than all
        // becoming blue. This same absolute 0..60 scale is used by the legend.
        int[] colors = SPEED_COLORS;
        double position = Math.max(0, Math.min(1, speed / 60)) * (colors.length - 1);
        int index = Math.min(colors.length - 2, (int) position);
        return blend(colors[index], colors[index + 1], position - index);
    }
    public static int blend(int a, int b, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        int red = (int) (((a >> 16) & 255) * (1-f) + ((b >> 16) & 255) * f);
        int green = (int) (((a >> 8) & 255) * (1-f) + ((b >> 8) & 255) * f);
        int blue = (int) ((a & 255) * (1-f) + (b & 255) * f);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }
}
