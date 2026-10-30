package io.github.jamerlybob.windroute;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.LatLng;
import java.util.Random;
import io.github.jamerlybob.windroute.wind.*;

/** Transparent, touch-through wind trails above the map and below the controls. */
public final class WindFlowView extends View {
    private final WindParticle[] particles = new WindParticle[450];
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path routeMask = new Path();
    private java.util.List<io.github.jamerlybob.windroute.route.GeoPoint> routePoints = java.util.Collections.emptyList();
    private View sheet;
    private float lastSheetTop = Float.NaN;
    private LinearGradient sheetFade;
    private BlurMaskFilter routeFadeMask;
    private Bitmap trails;
    private Canvas trailCanvas;
    private GoogleMap map;
    private WindField field;
    private long hour, lastFrame;
    private double calmBelowKmh = 5;
    public void setCalmThreshold(double threshold) { calmBelowKmh = threshold; }
    private boolean running;
    private WindVector[][] screenWind;
    public WindFlowView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        for (int i = 0; i < particles.length; i++) particles[i] = new WindParticle();
    }
    public void setRouteGeometry(java.util.List<io.github.jamerlybob.windroute.route.GeoPoint> points) {
        if (routePoints != points) { routePoints = points; screenWind = null; }
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sheet = getRootView().findViewById(R.id.summary_card);
    }
    public void setWind(GoogleMap map, WindField field, long hour) {
        this.map = map; this.field = field; this.hour = hour; clear();
    }
    public void setRunning(boolean running) {
        this.running = running; lastFrame = 0;
        if (running) invalidate(); else clear();
    }
    public void clear() {
        screenWind = null;
        if (trails != null) trails.eraseColor(Color.TRANSPARENT);
        for (WindParticle particle : particles) particle.lifetime = 0;
        invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        screenWind = null;
        if (trails != null) trails.recycle();
        if (w > 0 && h > 0) { trails = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); trailCanvas = new Canvas(trails); }
    }
    @Override protected void onDraw(Canvas canvas) {
        if (!running || field == null || map == null || trails == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (lastFrame != 0 && now - lastFrame < 32) { postInvalidateOnAnimation(); return; }
        double dt = lastFrame == 0 ? 0.033 : Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        trailCanvas.drawColor(0x30000000, PorterDuff.Mode.DST_OUT);
        if (screenWind == null) buildScreenWind();
        paint.setStrokeWidth(1.6f * getResources().getDisplayMetrics().density);
        for (WindParticle particle : particles) {
            if (particle.needsRespawn(getWidth(), getHeight())) {
                particle.x = random.nextDouble() * getWidth(); particle.y = random.nextDouble() * getHeight();
                particle.age = 0; particle.lifetime = 1 + random.nextDouble() * 4;
            }
            double gx = particle.x / getWidth() * 8, gy = particle.y / getHeight() * 8;
            int x = Math.min(7, (int) gx), y = Math.min(7, (int) gy);
            // The camera is stationary while particles run. Reuse its projected
            // lattice, then blend numbers without allocating objects per particle.
            double fx = gx - x;
            double fy = gy - y;
            WindVector bottomLeft = screenWind[y][x];
            WindVector bottomRight = screenWind[y][x + 1];
            WindVector topLeft = screenWind[y + 1][x];
            WindVector topRight = screenWind[y + 1][x + 1];
            double east = (bottomLeft.east * (1-fx) + bottomRight.east * fx) * (1-fy)
                    + (topLeft.east * (1-fx) + topRight.east * fx) * fy;
            double north = (bottomLeft.north * (1-fx) + bottomRight.north * fx) * (1-fy)
                    + (topLeft.north * (1-fx) + topRight.north * fx) * fy;
            float oldX = (float) particle.x;
            float oldY = (float) particle.y;
            // A fixed screen scale makes real wind speeds legible at every zoom.
            particle.step(east * .8, north * .8, dt);
            double speed = Math.hypot(east, north);
            paint.setColor(WindPalette.color(speed));
            paint.setAlpha((int) (255 * WindPresentation.particleOpacity(speed, calmBelowKmh)));
            trailCanvas.drawLine(oldX, oldY, (float) particle.x, (float) particle.y, paint);
        }
        fadeUnderRouteAndSheet();
        canvas.drawBitmap(trails, 0, 0, null);
        postInvalidateOnAnimation();
    }
    private void buildScreenWind() {
        Projection projection = map.getProjection();
        routeMask.reset();
        boolean first = true;
        for (io.github.jamerlybob.windroute.route.GeoPoint point : routePoints) {
            Point screen = projection.toScreenLocation(new LatLng(point.lat, point.lng));
            if (first) { routeMask.moveTo(screen.x, screen.y); first = false; }
            else routeMask.lineTo(screen.x, screen.y);
        }
        screenWind = new WindVector[9][9];
        double bearing = Math.toRadians(map.getCameraPosition().bearing);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                LatLng position = projection.fromScreenLocation(
                        new Point(x * getWidth() / 8, y * getHeight() / 8));
                WindVector wind = field.at(position.latitude, position.longitude, hour);
                screenWind[y][x] = new WindVector(
                        wind.east * Math.cos(bearing) - wind.north * Math.sin(bearing),
                        wind.east * Math.sin(bearing) + wind.north * Math.cos(bearing));
            }
        }
    }

    private void fadeUnderRouteAndSheet() {
        float density = getResources().getDisplayMetrics().density;
        // The trails live in a software bitmap, where a soft destination-out
        // mask is cheap and supported. The route itself stays crisp above it.
        eraser.setShader(null);
        eraser.setStyle(Paint.Style.STROKE);
        eraser.setStrokeWidth(22 * density);
        eraser.setStrokeJoin(Paint.Join.ROUND);
        eraser.setStrokeCap(Paint.Cap.ROUND);
        if (routeFadeMask == null) routeFadeMask = new BlurMaskFilter(6 * density, BlurMaskFilter.Blur.NORMAL);
        eraser.setMaskFilter(routeFadeMask);
        eraser.setAlpha(240);
        trailCanvas.drawPath(routeMask, eraser);
        eraser.setMaskFilter(null);
        if (sheet != null && sheet.getVisibility() == VISIBLE) {
            float top = sheet.getY();
            if (sheetFade == null || top != lastSheetTop) {
                lastSheetTop = top;
                sheetFade = new LinearGradient(0, top - 24 * density, 0, top,
                        Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
            }
            eraser.setStyle(Paint.Style.FILL);
            eraser.setAlpha(255);
            eraser.setShader(sheetFade);
            trailCanvas.drawRect(0, Math.max(0, top - 24 * density), getWidth(), getHeight(), eraser);
            eraser.setShader(null);
        }
    }

}
