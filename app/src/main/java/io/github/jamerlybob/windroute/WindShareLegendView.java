package io.github.jamerlybob.windroute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.units.UnitText;
import io.github.jamerlybob.windroute.wind.RouteWind;
import io.github.jamerlybob.windroute.wind.WindEffect;

/** Three compact share columns, adding calm only when it rounds above zero. */
public final class WindShareLegendView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels = new String[4], percentages = new String[4], distances = new String[4];
    private final int[] colors = new int[4];
    private final Typeface bold = Typeface.create("sans-serif-medium",Typeface.BOLD);
    private int columns;

    public WindShareLegendView(Context context, AttributeSet attrs) {
        super(context,attrs);
        setFocusable(true);
    }

    public void setWind(RouteWind wind, double meters, Settings settings) {
        WindEffect[] effects = {WindEffect.HEADWIND,WindEffect.CROSSWIND,WindEffect.TAILWIND,WindEffect.CALM};
        int[] names = {R.string.legend_name_headwind,R.string.legend_name_crosswind,R.string.legend_name_tailwind,R.string.legend_name_calm};
        int[] colorIds = {R.color.wind_headwind,R.color.wind_crosswind,R.color.wind_tailwind,R.color.wind_calm};
        UnitText units = new UnitText(getContext(),settings);
        columns = Math.round(wind.share(WindEffect.CALM)*100) == 0 ? 3 : 4;
        StringBuilder description = new StringBuilder();
        for (int i=0; i<columns; i++) {
            double share = wind.share(effects[i]);
            labels[i] = getContext().getString(names[i]);
            percentages[i] = getContext().getString(R.string.legend_percent,(int)Math.round(share*100));
            distances[i] = units.distance(share*meters);
            colors[i] = ContextCompat.getColor(getContext(),colorIds[i]);
            if (i>0) description.append(' ');
            description.append(getContext().getString(R.string.legend_accessible,labels[i],percentages[i],distances[i]));
        }
        setContentDescription(description);
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int desired = Math.round(70 * getResources().getDisplayMetrics().scaledDensity);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(),widthSpec),resolveSize(desired,heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        if (columns == 0) return;
        float dp = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        float width = (float)getWidth()/columns;
        int textColor = MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface);
        int muted = MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurfaceVariant);
        int divider = MaterialColors.getColor(this,com.google.android.material.R.attr.colorOutlineVariant);
        for (int i=0; i<columns; i++) {
            float left = i*width + (i==0 ? 0 : 10*dp);
            paint.setColor(colors[i]);
            canvas.drawCircle(left+4*dp,11*sp,3.5f*dp,paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(12*sp);
            float available = width-25*dp;
            if (paint.measureText(labels[i])>available) paint.setTextSize(Math.max(9*dp,12*sp*available/paint.measureText(labels[i])));
            paint.setColor(textColor);
            canvas.drawText(labels[i],left+13*dp,15*sp,paint);
            paint.setTypeface(bold);
            paint.setTextSize(20*sp);
            canvas.drawText(percentages[i],left+13*dp,39*sp,paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(12*sp);
            paint.setColor(muted);
            canvas.drawText(distances[i],left+13*dp,58*sp,paint);
            if (i>0) {
                paint.setColor(divider);
                paint.setStrokeWidth(dp);
                canvas.drawLine(i*width+2*dp,3*dp,i*width+2*dp,62*sp,paint);
            }
        }
    }
}
