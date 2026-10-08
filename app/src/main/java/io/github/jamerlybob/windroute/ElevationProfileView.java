package io.github.jamerlybob.windroute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.github.jamerlybob.windroute.elevation.ClimbDetector;
import io.github.jamerlybob.windroute.elevation.ClimbWind;
import io.github.jamerlybob.windroute.elevation.ElevationProfile;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitFormatter;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** A filled elevation profile whose road-facing edge uses the route wind colours. */
public final class ElevationProfileView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path area = new Path();
    private ElevationProfile profile;
    private RouteWind wind;
    private List<ClimbDetector.Climb> climbs = new ArrayList<>();
    private List<ClimbWind> climbWinds = new ArrayList<>();
    private Settings settings = Settings.defaults();
    private double progressMeters = Double.NaN;

    public ElevationProfileView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** Moves the ride marker without rebuilding the terrain or wind data. */
    public void setProgressMeters(double progressMeters) {
        this.progressMeters = progressMeters;
        invalidate();
    }

    public void setData(ElevationProfile profile, RouteWind wind,
                        List<ClimbDetector.Climb> climbs, List<ClimbWind> climbWinds,
                        Settings settings) {
        this.profile = profile;
        this.wind = wind;
        this.climbs = climbs;
        this.climbWinds = climbWinds;
        this.settings = settings;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (profile == null || profile.size() < 2 || wind == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float left = 42 * density;
        float right = getWidth() - 8 * density;
        float top = 20 * density;
        float bottom = getHeight() - 28 * density;
        // Canvas coordinates begin at the top-left: x grows right and y grows
        // down. These margins leave room for the two sets of axis labels.
        double total = profile.distanceMeters[profile.size() - 1];
        double low = profile.elevationMeters[0];
        double high = low;
        for (double height : profile.elevationMeters) {
            low = Math.min(low, height);
            high = Math.max(high, height);
        }
        double range = Math.max(10, high - low);
        // The lowest point is drawn a little above the bottom edge. Without
        // this a flat stretch at the lowest height (a waterfront road, say)
        // has no height at all and looks as if the profile were missing.
        float lowestY = bottom - 6 * density;

        // Each neighbouring pair is a small filled trapezoid. Colouring those
        // pieces independently lets the top edge read as both terrain and wind
        // without hiding the shape beneath an opaque line.
        for (int i = 1; i < profile.size(); i++) {
            float x0 = x(profile.distanceMeters[i - 1], total, left, right);
            float x1 = x(profile.distanceMeters[i], total, left, right);
            float y0 = y(profile.elevationMeters[i - 1], low, range, top, lowestY);
            float y1 = y(profile.elevationMeters[i], low, range, top, lowestY);
            area.reset();
            area.moveTo(x0, bottom);
            area.lineTo(x0, y0);
            area.lineTo(x1, y1);
            area.lineTo(x1, bottom);
            area.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ContextCompat.getColor(getContext(), colorFor(
                    effectAt((profile.distanceMeters[i - 1] + profile.distanceMeters[i]) / 2))));
            paint.setAlpha(145);
            canvas.drawPath(area, paint);
            paint.setAlpha(255);
        }

        paint.setTextSize(10 * density);
        paint.setColor(resolve(com.google.android.material.R.attr.colorOnSurfaceVariant));
        String lowText = heightText(low);
        String highText = heightText(high);
        canvas.drawText(highText, 2 * density, top + 8 * density, paint);
        canvas.drawText(lowText, 2 * density, bottom, paint);

        for (int i = 0; i <= 4; i++) {
            double distance = total * i / 4.0;
            paint.setTextAlign(i == 0 ? Paint.Align.LEFT
                    : i == 4 ? Paint.Align.RIGHT : Paint.Align.CENTER);
            canvas.drawText(distanceText(distance), x(distance, total, left, right),
                    getHeight() - 5 * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);

        for (int i = 0; i < climbs.size(); i++) {
            ClimbDetector.Climb climb = climbs.get(i);
            float start = x(climb.startDistanceMeters, total, left, right);
            float end = x(climb.endDistanceMeters(), total, left, right);
            boolean intoWind = i < climbWinds.size() && climbWinds.get(i).isClimbIntoHeadwind;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((intoWind ? 4 : 2) * density);
            paint.setColor(ContextCompat.getColor(getContext(), intoWind
                    ? R.color.wind_headwind : R.color.wind_crosswind));
            canvas.drawLine(start, 8 * density, end, 8 * density, paint);
            if (intoWind) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle((start + end) / 2, 8 * density, 4 * density, paint);
            }
        }
        if (Double.isFinite(progressMeters)) {
            float markerX = x(Math.max(0, Math.min(total, progressMeters)), total, left, right);
            paint.setColor(resolve(com.google.android.material.R.attr.colorOnSurface));
            paint.setStrokeWidth(2 * density);
            canvas.drawLine(markerX, top, markerX, bottom, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(markerX, top, 5 * density, paint);
        }
    }

    private WindEffect effectAt(double distance) {
        double start = 0;
        for (RouteWind.Stretch stretch : wind.stretches) {
            if (distance <= start + stretch.lengthMeters) {
                return stretch.effect;
            }
            start += stretch.lengthMeters;
        }
        return WindEffect.CALM;
    }

    private String heightText(double meters) {
        double value = UnitFormatter.elevation(meters, settings.elevationUnit);
        return getContext().getString(settings.elevationUnit == Settings.ElevationUnit.FEET
                ? R.string.height_feet : R.string.height_metres, value);
    }

    private String distanceText(double meters) {
        int format = settings.distanceUnit == Settings.DistanceUnit.MILES
                ? R.string.distance_miles : R.string.distance_km;
        return UnitFormatter.distance(meters, settings.distanceUnit,
                getContext().getString(format), java.util.Locale.getDefault());
    }

    private static float x(double value, double total, float left, float right) {
        // Turn progress such as 25 km of 100 km into 0.25, then place that
        // fraction across the graph's drawable width.
        return left + (float) (total > 0 ? value / total : 0) * (right - left);
    }

    private static float y(double value, double low, double range, float top, float bottom) {
        // Height is normalized in the same way, but subtracted from the bottom
        // because smaller Canvas y values appear higher on the screen.
        return bottom - (float) ((value - low) / range) * (bottom - top);
    }

    private int resolve(int attribute) {
        android.util.TypedValue value = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(attribute, value, true);
        return value.data;
    }

    private static int colorFor(WindEffect effect) {
        switch (effect) {
            case HEADWIND: return R.color.wind_headwind;
            case TAILWIND: return R.color.wind_tailwind;
            case CROSSWIND: return R.color.wind_crosswind;
            default: return R.color.wind_calm;
        }
    }
}
