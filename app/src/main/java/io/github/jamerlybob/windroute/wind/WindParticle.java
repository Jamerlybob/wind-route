package io.github.jamerlybob.windroute.wind;

/** A screen-space particle; random birth positions belong to the drawing view. */
public final class WindParticle {
    public double x, y, age, lifetime;
    public void step(double eastPixelsPerSecond, double northPixelsPerSecond, double seconds) {
        x += eastPixelsPerSecond * seconds; y -= northPixelsPerSecond * seconds; age += seconds;
    }
    public boolean needsRespawn(int width, int height) {
        return age >= lifetime || x < 0 || y < 0 || x >= width || y >= height;
    }
}
