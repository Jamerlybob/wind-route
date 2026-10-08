package io.github.jamerlybob.windroute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.jamerlybob.windroute.wind.DepartureScorer;

/** A compact, tappable 24-hour comparison drawn without another chart library. */
public final class DepartureStripView extends View {
    public interface Listener {
        void onDepartureSelected(long epochSeconds);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selectedOutline = new RectF();
    private final Date labelDate = new Date();
    private List<DepartureScorer.Entry> entries = new ArrayList<>();
    private long selectedEpochSeconds;
    private Listener listener;

    public DepartureStripView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.NORMAL));
        setFocusable(true);
    }

    public void setEntries(List<DepartureScorer.Entry> entries, long selectedEpochSeconds,
                           Listener listener, String description) {
        this.entries = entries == null ? new ArrayList<>() : entries;
        this.selectedEpochSeconds = selectedEpochSeconds;
        this.listener = listener;
        setContentDescription(description);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float left = 4 * density;
        float right = getWidth() - 4 * density;
        float labelTop = getHeight() - 28 * density;
        float centre = (labelTop + 10 * density) / 2f;
        float halfHeight = Math.max(1, centre - 18 * density);
        float slot = (right - left) / entries.size();

        // Every bar grows away from the same centre line. Dividing by the
        // largest net wind preserves relative size while using the available
        // height on calm and windy days alike.
        double largest = 1;
        int best = 0;
        for (int i = 0; i < entries.size(); i++) {
            largest = Math.max(largest, Math.abs(entries.get(i).averageHeadwindKmh));
            if (entries.get(i).score < entries.get(best).score) {
                best = i;
            }
        }
        paint.setStrokeWidth(density);
        paint.setColor(resolve(android.R.attr.textColorSecondary));
        canvas.drawLine(left, centre, right, centre, paint);

        for (int i = 0; i < entries.size(); i++) {
            DepartureScorer.Entry entry = entries.get(i);
            float x0 = left + i * slot + 1.5f * density;
            float x1 = left + (i + 1) * slot - 1.5f * density;
            float height = (float) (Math.abs(entry.averageHeadwindKmh) / largest * halfHeight);
            boolean tailwind = entry.averageHeadwindKmh < 0;
            float y0 = tailwind ? centre - height : centre;
            float y1 = tailwind ? centre : centre + height;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ContextCompat.getColor(getContext(), tailwind
                    ? R.color.wind_tailwind : R.color.wind_headwind));
            canvas.drawRect(x0, y0, x1, y1, paint);

            if (entry.hasRainData && ((!Double.isNaN(entry.wettestRainMm)
                    && entry.wettestRainMm > 0) || (!Double.isNaN(entry.rainChancePercent)
                    && entry.rainChancePercent >= 30))) {
                paint.setColor(resolve(androidx.appcompat.R.attr.colorPrimary));
                canvas.drawCircle((x0 + x1) / 2, labelTop - 5 * density, 2.5f * density, paint);
            }

            if (entry.departureEpochSeconds == selectedEpochSeconds) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * density);
                paint.setColor(resolve(com.google.android.material.R.attr.colorOnSurface));
                selectedOutline.set(x0 - density, y0 - density, x1 + density, y1 + density);
                canvas.drawRoundRect(selectedOutline, 3 * density, 3 * density, paint);
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(10 * density);
        paint.setColor(resolve(com.google.android.material.R.attr.colorOnSurfaceVariant));
        DateFormat hours = android.text.format.DateFormat.getTimeFormat(getContext());
        for (int i = 0; i < entries.size(); i += 3) {
            labelDate.setTime(entries.get(i).departureEpochSeconds * 1000);
            String label = hours.format(labelDate);
            canvas.drawText(label, left + i * slot, getHeight() - 4 * density, paint);
        }
        paint.setTextSize(9 * density);
        paint.setColor(resolve(androidx.appcompat.R.attr.colorPrimary));
        canvas.drawText(getContext().getString(R.string.best_label),
                left + (best + 0.1f) * slot, 11 * density, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || entries.isEmpty()) {
            return true;
        }
        int index = (int) (event.getX() / Math.max(1f, getWidth()) * entries.size());
        index = Math.max(0, Math.min(entries.size() - 1, index));
        selectedEpochSeconds = entries.get(index).departureEpochSeconds;
        invalidate();
        if (listener != null) {
            listener.onDepartureSelected(selectedEpochSeconds);
        }
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int resolve(int attribute) {
        android.util.TypedValue value = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(attribute, value, true);
        return value.data;
    }
}
