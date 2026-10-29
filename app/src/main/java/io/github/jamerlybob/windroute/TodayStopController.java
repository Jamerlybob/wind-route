package io.github.jamerlybob.windroute;

import android.app.Activity;
import android.location.Geocoder;
import android.os.*;
import android.view.View;
import android.widget.*;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.*;
import com.google.android.material.slider.Slider;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import io.github.jamerlybob.windroute.trip.StopPicker;
import io.github.jamerlybob.windroute.poi.*;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.weather.*;
import io.github.jamerlybob.windroute.settings.*;
import io.github.jamerlybob.windroute.units.UnitText;

/** A short stop preview; network lookups are debounced and never call Google Routes. */
public final class TodayStopController {
    private final Activity activity;
    private final GoogleMap map;
    private final LinearLayout body;
    private final Slider slider;
    private final MaterialSwitch byTime;
    private final TextView label, result, placesCredit;
    private final Button camping;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RideAnalysis analysis;
    private List<PoiAlongRoute> places = Collections.emptyList();
    private List<PoiAlongRoute> foundCamping = Collections.emptyList();
    private List<GeoPoint> lastRoutePoints;
    private Marker marker;
    private GeoPoint point;
    private double stop;
    private int generation;
    private boolean destroyed;
    private String lastLookupKey = "", cachedName;
    private Long cachedSunset;
    private final Map<String,List<DaylightForecast>> daylightCache=new HashMap<>();
    private final Runnable lookup = this::lookup;
    public TodayStopController(Activity activity, GoogleMap map) {
        this.activity=activity; this.map=map;
        body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        label=new TextView(activity); label.setText(R.string.how_far_today); body.addView(label);
        byTime=new MaterialSwitch(activity); byTime.setText(R.string.riding_time); body.addView(byTime);
        slider=new Slider(activity); slider.setValueFrom(10); slider.setValueTo(200); slider.setStepSize(5);
        slider.setValue(activity.getSharedPreferences("stops",0).getInt("km",50));
        slider.setContentDescription(activity.getString(R.string.how_far_today)); body.addView(slider);
        result=new TextView(activity); body.addView(result);
        camping=new com.google.android.material.button.MaterialButton(activity,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        camping.setText(R.string.find_camping_here);
        camping.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tent,0,0,0);
        body.addView(camping);
        placesCredit = new TextView(activity);
        placesCredit.setText(R.string.osm_credit);
        placesCredit.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        body.addView(placesCredit);
        placesCredit.setVisibility(View.GONE);
        ((LinearLayout)activity.findViewById(R.id.summary_content)).addView(body, 2,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        slider.addOnChangeListener((view,value,user) -> { if(user) {
            activity.getSharedPreferences("stops",0).edit().putInt("km",(int)value).apply(); update(); } });
        byTime.setOnCheckedChangeListener((button,checked)->update());
        camping.setOnClickListener(v->findCamping());
    }
    public void show(RideAnalysis analysis, List<PoiAlongRoute> places) {
        if(lastRoutePoints!=analysis.route.points) { foundCamping=Collections.emptyList(); lastLookupKey=""; }
        lastRoutePoints=analysis.route.points;
        this.analysis=analysis; this.places=new ArrayList<>(places); this.places.addAll(foundCamping);
        body.setVisibility(analysis.route.distanceMeters>30000 ? View.VISIBLE : View.GONE);
        if (body.getVisibility()==View.VISIBLE) update();
    }
    private void update() {
        if(analysis==null) return;
        double requested = byTime.isChecked() ? StopPicker.distanceForMinutes(slider.getValue()/5*15,analysis.route,analysis.wind)
                : slider.getValue()*1000;
        double[] distances=GeoMath.cumulativeMeters(analysis.route.points);
        stop=StopPicker.snap(requested,distances[distances.length-1],places);
        point=StopPicker.pointAt(analysis.route.points,stop);
        placesCredit.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        if(marker!=null) marker.remove();
        marker=map.addMarker(new MarkerOptions().position(new LatLng(point.lat,point.lng))
                .title(activity.getString(R.string.overnight_stop)).icon(MapIcons.tent(activity,0)));
        label.setText(byTime.isChecked() ? activity.getString(R.string.riding_minutes,Math.round(slider.getValue()/5*15))
                : activity.getString(R.string.today_km,Math.round(slider.getValue())));
        String key=String.format(Locale.US,"%.3f,%.3f,%d",point.lat,point.lng,analysis.departureEpochSeconds/86400);
        render(key.equals(lastLookupKey) ? cachedName : null,key.equals(lastLookupKey) ? cachedSunset : null);
        if(!key.equals(lastLookupKey)) { generation++; main.removeCallbacks(lookup); main.postDelayed(lookup,600); }
    }
    private void render(String name, Long sunset) {
        double minutes=StopPicker.minutesTo(stop,analysis.route,analysis.wind);
        long arrival=analysis.departureEpochSeconds+Math.round(minutes*60);
        UnitText units=new UnitText(activity,SettingsStore.load(activity));
        String stopName=StopPicker.nameAt(stop,places);
        if(stopName!=null) name=stopName;
        String near=name==null ? activity.getString(R.string.stop_along,units.distance(stop)) : name;
        double calm=analysis.route.durationSeconds/60.0*stop/analysis.route.distanceMeters;
        String warning=sunset!=null && arrival>sunset ? activity.getString(R.string.stop_after_sunset,StopPicker.minutesAfterSunset(arrival,sunset)) : "";
        result.setText(activity.getString(R.string.stop_preview,near,units.distance(stop),Math.round(minutes/60),Math.round(minutes)%60,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(arrival*1000)),
                WindCostText.format(activity,minutes-calm,false),warning));
    }
    @SuppressWarnings("deprecation")
    private void lookup() {
        final int token=generation; final GeoPoint requested=point;
        final long departure=analysis.departureEpochSeconds;
        final String key=String.format(Locale.US,"%.3f,%.3f,%d",requested.lat,requested.lng,departure/86400);
        worker.execute(()->{
            String name=null; Long sunset=null;
            try { List<android.location.Address> addresses=new Geocoder(activity).getFromLocation(requested.lat,requested.lng,1);
                if(addresses!=null&&!addresses.isEmpty()) { name=addresses.get(0).getLocality();
                    if(name==null) name=addresses.get(0).getSubAdminArea(); } } catch(Exception ignored) { }
            try {
                String cell=String.format(Locale.US,"%.2f,%.2f",requested.lat,requested.lng);
                List<DaylightForecast> lights=daylightCache.get(cell);
                if(lights==null) { lights=DaylightClient.fetch(requested); daylightCache.put(cell,lights); }
                sunset=StopPicker.sunsetFor(lights,departure);
            } catch(Exception ignored) { }
            final String found=name; final Long light=sunset;
            main.post(()->{if(!destroyed&&token==generation) {lastLookupKey=key;cachedName=found;cachedSunset=light;render(found,light);}});
        });
    }
    private void findCamping() {
        final GeoPoint requested=point; final int token=generation;
        camping.setEnabled(false);
        worker.execute(()->{
            try {
                List<Poi> found=OverpassClient.fetchCampingBox(requested);
                List<PoiAlongRoute> located=PoiAlongRoute.locate(analysis.route.points,found);
                main.post(()->{if(destroyed)return;camping.setEnabled(true); if(token==generation) {foundCamping=located;places=new ArrayList<>(places);places.addAll(located);update();}});
            } catch(Exception error) {main.post(()->{if(!destroyed){camping.setEnabled(true);result.setText(R.string.camping_unavailable);}});}
        });
    }
    public void destroy(){destroyed=true;main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
