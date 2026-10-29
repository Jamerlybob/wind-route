package io.github.jamerlybob.windroute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import com.google.android.material.color.MaterialColors;

/** Verdict ring with a travelling-air arrow; speed and unit remain upright. */
public final class WindBadgeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private int color = 0xff17a398;
    private double direction;
    private String speed = "", unit = "";

    public WindBadgeView(Context context, AttributeSet attributes) { super(context, attributes); }

    public void setWind(int color, double direction, String formattedSpeed) {
        this.color = color;
        this.direction = direction;
        int split = formattedSpeed.indexOf(' ');
        speed = split < 0 ? formattedSpeed : formattedSpeed.substring(0, split);
        unit = split < 0 ? "" : formattedSpeed.substring(split + 1);
        setContentDescription(getContext().getString(R.string.wind_badge_description, formattedSpeed));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 4 * density;
        if (radius <= 0) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(color);
        paint.setStrokeWidth(3 * density);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.translate(cx, cy - radius * .48f);
        canvas.rotate((float) direction);
        arrow.reset();
        arrow.moveTo(0, -7 * density);
        arrow.lineTo(6 * density, 6 * density);
        arrow.lineTo(0, 3 * density);
        arrow.lineTo(-6 * density, 6 * density);
        arrow.close();
        canvas.drawPath(arrow, paint);
        canvas.restore();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        paint.setTextSize(radius * .67f);
        canvas.drawText(speed, cx, cy + radius * .30f, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(radius * .32f);
        canvas.drawText(unit, cx, cy + radius * .69f, paint);
    }
}
