package io.github.jamerlybob.windroute;

import android.content.Context;
import android.graphics.*;
import com.google.android.gms.maps.model.*;

/** Small original tent symbol, with an optional overnight number. */
public final class MapIcons {
    private MapIcons() { }
    public static BitmapDescriptor endpoint(Context context, int color, String name) {
        float density = context.getResources().getDisplayMetrics().density;
        android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(12 * density);
        text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        String label = android.text.TextUtils.ellipsize(name,text,180*density,
                android.text.TextUtils.TruncateAt.END).toString();
        int width = (int)Math.max(48*density,text.measureText(label)+20*density);
        Bitmap image = Bitmap.createBitmap(width,(int)(70*density),Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xe6101b29);
        canvas.drawRoundRect(0,0,width,27*density,9*density,9*density,paint);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label,width/2f,18*density,text);
        paint.setColor(0xff101b29);
        canvas.drawCircle(width/2f,48*density,9*density,paint);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3*density);
        canvas.drawCircle(width/2f,48*density,9*density,paint);
        return BitmapDescriptorFactory.fromBitmap(image);
    }

    public static BitmapDescriptor arrow(Context context,int color) {
        float density=context.getResources().getDisplayMetrics().density;
        Bitmap image=Bitmap.createBitmap((int)(28*density),(int)(28*density),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.scale(density,density);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Path arrow=new Path();arrow.moveTo(14,2);arrow.lineTo(25,16);arrow.lineTo(17,14);
        arrow.lineTo(17,26);arrow.lineTo(11,26);arrow.lineTo(11,14);arrow.lineTo(3,16);arrow.close();
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);canvas.drawPath(arrow,paint);
        paint.setColor(color);paint.setStyle(Paint.Style.FILL);canvas.drawPath(arrow,paint);
        return BitmapDescriptorFactory.fromBitmap(image);
    }
    public static BitmapDescriptor tent(Context context, int number) {
        float density=context.getResources().getDisplayMetrics().density;
        Bitmap image=Bitmap.createBitmap((int)(40*density),(int)(40*density),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.scale(density,density);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xff267b68);
        canvas.drawCircle(20,20,19,paint);paint.setColor(Color.WHITE);
        android.graphics.drawable.Drawable tent = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_tent);
        if (tent != null) {
            tent.setTint(Color.WHITE);
            tent.setBounds(4, 4, 36, 36);
            tent.draw(canvas);
        }
        paint.setColor(0xff267b68);paint.setTextSize(13);paint.setTextAlign(Paint.Align.CENTER);
        if(number>0)canvas.drawText(Integer.toString(number),20,26,paint);
        else {Path door=new Path();door.moveTo(14,29);door.lineTo(20,18);door.lineTo(26,29);door.close();canvas.drawPath(door,paint);}
        return BitmapDescriptorFactory.fromBitmap(image);
    }
}
