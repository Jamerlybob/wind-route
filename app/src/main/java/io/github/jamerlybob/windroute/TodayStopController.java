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
    private final TextView label, result, stopTitle, sunsetWarning, placesCredit;
    private final java.util.function.Consumer<LatLng> stopChanged;
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
    public TodayStopController(Activity activity, GoogleMap map, java.util.function.Consumer<LatLng> stopChanged) {
        this.stopChanged = stopChanged;
        this.activity=activity; this.map=map;
        body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        label=new TextView(activity);
        label.setText(R.string.how_far_today);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        body.addView(label);
        byTime=new MaterialSwitch(activity); byTime.setText(R.string.riding_time); body.addView(byTime);
        slider=new Slider(activity); slider.setValueFrom(10); slider.setValueTo(200); slider.setStepSize(5); slider.setTickVisible(false);
        slider.setValue(activity.getSharedPreferences("stops",0).getInt("km",50));
        slider.setContentDescription(activity.getString(R.string.how_far_today)); body.addView(slider);
        com.google.android.material.card.MaterialCardView stopCard = new com.google.android.material.card.MaterialCardView(activity);
        stopCard.setRadius(dp(16));
        stopCard.setCardElevation(0);
        stopCard.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                body,com.google.android.material.R.attr.colorSurfaceContainerLow));
        stopCard.setStrokeColor(androidx.core.content.ContextCompat.getColor(activity,R.color.brand_outline_variant));
        stopCard.setStrokeWidth(dp(1));
        LinearLayout cardBody = new LinearLayout(activity);
        cardBody.setOrientation(LinearLayout.VERTICAL);
        cardBody.setPadding(dp(12),dp(12),dp(12),dp(12));
        stopTitle=new TextView(activity);
        stopTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        stopTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tent,0,0,0);
        stopTitle.setCompoundDrawablePadding(dp(8));
        cardBody.addView(stopTitle);
        result=new TextView(activity);
        result.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        result.setPadding(0,dp(6),0,0);
        cardBody.addView(result);
        sunsetWarning=new TextView(activity);
        sunsetWarning.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        sunsetWarning.setTextColor(androidx.core.content.ContextCompat.getColor(activity,R.color.wind_headwind));
        cardBody.addView(sunsetWarning);
        stopCard.addView(cardBody);
        body.addView(stopCard);
        camping=new com.google.android.material.button.MaterialButton(activity,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        camping.setText(R.string.find_camping_here);
        camping.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tent,0,0,0);
        body.addView(camping);
        placesCredit = new TextView(activity);
        placesCredit.setText(R.string.osm_credit);
        placesCredit.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        body.addView(placesCredit);
        placesCredit.setVisibility(View.GONE);
        LinearLayout.LayoutParams section = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        section.topMargin = dp(24);
        section.bottomMargin = dp(8);
        ((LinearLayout)activity.findViewById(R.id.summary_content)).addView(body,2,section);
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
        else {
            if(marker!=null) { marker.remove(); marker=null; }
            stopChanged.accept(null);
        }
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
        stopChanged.accept(new LatLng(point.lat,point.lng));
        label.setText(byTime.isChecked() ? activity.getString(R.string.today_time_target,
                io.github.jamerlybob.windroute.nav.DurationFormatter.format(slider.getValue()/5*15,
                        activity.getString(R.string.duration_min),activity.getString(R.string.duration_h_min)))
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
        Settings settings=SettingsStore.load(activity);
        String distance=io.github.jamerlybob.windroute.units.UnitFormatter.distance(stop,settings.distanceUnit,
                activity.getString(settings.distanceUnit==Settings.DistanceUnit.MILES
                        ? R.string.stop_distance_miles : R.string.stop_distance_km),Locale.getDefault());
        String duration=io.github.jamerlybob.windroute.nav.DurationFormatter.format(minutes,
                activity.getString(R.string.duration_min),activity.getString(R.string.duration_h_min));
        stopTitle.setText(activity.getString(R.string.stop_card_title,near));
        result.setText(activity.getString(R.string.stop_card_details,distance,duration,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(arrival*1000)),
                WindCostText.format(activity,minutes-calm,false)));
        sunsetWarning.setText(warning);
        sunsetWarning.setVisibility(warning.isEmpty() ? View.GONE : View.VISIBLE);
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
    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }

    public void destroy(){destroyed=true;main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
