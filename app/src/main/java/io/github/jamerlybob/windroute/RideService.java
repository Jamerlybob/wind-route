package io.github.jamerlybob.windroute;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.jamerlybob.windroute.nav.CuePlanner;
import io.github.jamerlybob.windroute.nav.RouteProgress;
import io.github.jamerlybob.windroute.nav.RideTracker;
import io.github.jamerlybob.windroute.nav.TurnGuide;
import io.github.jamerlybob.windroute.nav.CueBuilder;
import io.github.jamerlybob.windroute.nav.RideWind;
import io.github.jamerlybob.windroute.weather.WindForecast;
import io.github.jamerlybob.windroute.route.GeoMath;
import io.github.jamerlybob.windroute.route.GeoPoint;
import io.github.jamerlybob.windroute.route.Route;
import io.github.jamerlybob.windroute.settings.Settings;
import io.github.jamerlybob.windroute.settings.SettingsStore;

/**
 * Owns live guidance while the Activity may be stopped or recreated.
 *
 * <p>A started foreground service is used because a bound-only service dies as
 * soon as the screen goes away. It deliberately does not request background
 * location: Android permits this location foreground service because the user
 * starts it from the visible Activity after granting location permission.
 * Android requires its ongoing notification: the rider can see and stop GPS
 * guidance even with the screen off. Binding additionally lets the visible
 * Activity receive updates; unbinding must not end the started ride.
 */
public final class RideService extends Service implements LocationListenerCompat,
        TextToSpeech.OnInitListener {
    public static final String ACTION_STOP = "io.github.jamerlybob.windroute.STOP_RIDE";
    private static final String CHANNEL = "ride_guidance";
    private static final int NOTIFICATION_ID = 41;
    private static final long FIX_INTERVAL_MS = 1000;
    private static final float MIN_FIX_METERS = 2;

    public interface Listener {
        void onRideUpdate(RideUpdate update);
        void onRideStopped(boolean arrived);
    }

    public static final class RideUpdate {
        public final GeoPoint position;
        public final float bearing;
        public final RouteProgress progress;
        public final String instruction;
        public final boolean offRoute;
        public final long elapsedSeconds;
        public final double instructionDistanceMeters;
        public final double riddenMeters;
        public final long remainingSeconds;
        public final RideWind wind;

        RideUpdate(GeoPoint position, float bearing, RouteProgress progress,
                   String instruction, boolean offRoute, long elapsedSeconds,
                   double instructionDistanceMeters, double riddenMeters,
                   long remainingSeconds, RideWind wind) {
            this.position = position;
            this.bearing = bearing;
            this.progress = progress;
            this.instruction = instruction;
            this.offRoute = offRoute;
            this.elapsedSeconds = elapsedSeconds;
            this.instructionDistanceMeters = instructionDistanceMeters;
            this.riddenMeters = riddenMeters;
            this.remainingSeconds = remainingSeconds;
            this.wind = wind;
        }
    }

    public final class RideBinder extends Binder {
        public RideService service() { return RideService.this; }
    }

    // Ride snapshot and progress survive Activity recreation, but not process death.
    private final RideBinder binder = new RideBinder();
    private Route route;
    private CuePlanner planner;
    private Settings settings;
    private Listener listener;
    private RideUpdate lastUpdate;
    // Android resources belong to the Service and are released in onDestroy.
    private LocationManager locations;
    private TextToSpeech speech;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private long startedAt;
    private String lastInstruction;
    // Same-process handoff consumed once when starting or replacing a ride.
    private static List<CuePlanner.Event> pendingEvents;
    private static Route pendingRoute;
    private static RideAnalysis pendingAnalysis;
    private static List<Integer> pendingIndexes;
    private static List<WindForecast> pendingForecasts;
    private List<Integer> sampleIndexes;
    private List<WindForecast> forecasts;
    private double weightedHeadwind;
    private long windFixes;
    private RideAnalysis analysis;
    private RideTracker tracker = new RideTracker();
    private TurnGuide turns;
    // Speech callbacks are posted to the main thread, like location callbacks.
    private boolean running;
    private boolean speechReady;
    private boolean speaking;
    private boolean speakingTurn;
    private boolean arriving;
    private String urgentCue;
    private String activeUtteranceId;
    private final Handler main = new Handler(Looper.getMainLooper());

    /**
     * Transfers the computed snapshot within this process. A route and cached
     * forecasts can exceed Binder's roughly 1 MB buffer, which is shared by
     * in-flight transactions, so the start Intent carries no large payload.
     * This is not persistence after OS death.
     */
    public static synchronized void prepare(Route route, RideAnalysis analysis,
                                            List<CuePlanner.Event> events, List<Integer> indexes,
                                            List<WindForecast> forecasts) {
        pendingEvents = new ArrayList<>(events);
        pendingRoute = route;
        pendingAnalysis = analysis;
        pendingIndexes = indexes == null ? null : new ArrayList<>(indexes);
        pendingForecasts = forecasts == null ? null : new ArrayList<>(forecasts);
    }

    @Override public void onCreate() {
        super.onCreate();
        settings = SettingsStore.load(this);
        RouteStore.SavedRoute saved = new RouteStore(this).loadRoute();
        if (saved != null) route = saved.route;
        synchronized (RideService.class) {
            if (pendingRoute != null) route = pendingRoute;
            analysis = pendingAnalysis;
            sampleIndexes = pendingIndexes;
            forecasts = pendingForecasts;
            planner = new CuePlanner(pendingEvents != null ? pendingEvents
                    : route == null ? new ArrayList<>() : turnEvents(route));
            pendingEvents = null;
            pendingRoute = null;
            pendingAnalysis = null;
            pendingIndexes = null;
            pendingForecasts = null;
        }
        if (route != null) turns = new TurnGuide(route);
        lastInstruction = getString(R.string.ride_default_instruction);
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        speech = new TextToSpeech(this, this);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRide(false);
            return START_NOT_STICKY;
        }
        if (route == null || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        adoptPreparedRide();
        running = true;
        startedAt = SystemClock.elapsedRealtime();
        startForeground(NOTIFICATION_ID, notification());
        try {
            startLocations();
        } catch (SecurityException | IllegalArgumentException error) {
            stopRide(false);
        }
        // Never silently resume GPS with a stale handoff after Android kills us.
        // A fresh visible Start ride tap supplies permission and a fresh plan.
        return START_NOT_STICKY;
    }

    private void adoptPreparedRide() {
        synchronized (RideService.class) {
            if (pendingRoute != null) {
                // Arrival speech keeps the stopped instance alive briefly. A
                // new visible Start tap may arrive during that sentence; adopt
                // its snapshot rather than restarting the previous route.
                main.removeCallbacksAndMessages(null);
                activeUtteranceId = null;
                speech.stop();
                speaking = false;
                arriving = false;
                route = pendingRoute;
                analysis = pendingAnalysis;
                sampleIndexes = pendingIndexes;
                forecasts = pendingForecasts;
                planner = new CuePlanner(pendingEvents);
                turns = new TurnGuide(route);
                tracker = new RideTracker();
                weightedHeadwind = 0;
                windFixes = 0;
                lastUpdate = null;
                urgentCue = null;
                pendingRoute = null;
                pendingAnalysis = null;
                pendingEvents = null;
                pendingIndexes = null;
                pendingForecasts = null;
            }
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return binder; }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null && lastUpdate != null) listener.onRideUpdate(lastUpdate);
    }

    public boolean isRunning() { return running; }
    public Route route() { return route; }
    public RideAnalysis analysis() { return analysis; }
    public double averageHeadwindKmh() {
        return windFixes == 0 ? Double.NaN : weightedHeadwind / windFixes;
    }

    @SuppressWarnings("MissingPermission")
    private void startLocations() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        String provider = locations.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER
                : locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER : null;
        if (provider == null) { stopRide(false); return; }
        // One fix per second is responsive at cycling speed. Asking for more
        // would keep the GPS awake harder without materially improving cues.
        LocationRequestCompat request = new LocationRequestCompat.Builder(FIX_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FIX_INTERVAL_MS)
                .setMinUpdateDistanceMeters(MIN_FIX_METERS).build();
        LocationManagerCompat.requestLocationUpdates(locations, provider, request,
                ContextCompat.getMainExecutor(this), this);
    }

    @Override public void onLocationChanged(Location location) {
        if (!running) return;
        lastUpdate = updateProgress(location);
        if (listener != null) listener.onRideUpdate(lastUpdate);
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
        if (tracker.arrived) stopRide(true);
        else drainSpeech();
    }

    private RideUpdate updateProgress(Location location) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        long elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000;
        if (tracker.update(route, point, elapsed)) urgentCue = getString(R.string.cue_off_route);
        RouteProgress progress = tracker.progress;
        int next = turns.nextIndex(progress.distanceAlongMeters);
        lastInstruction = next < 0 ? getString(R.string.ride_default_instruction)
                : turns.steps.get(next).instruction;
        float heading = location.hasBearing() ? location.getBearing() : (float) GeoMath.bearingDegrees(
                route.points.get(progress.segmentIndex),
                route.points.get(Math.min(route.points.size() - 1, progress.segmentIndex + 1)));
        RideWind wind = RideWind.at(route, sampleIndexes, forecasts,
                progress.distanceAlongMeters, heading, System.currentTimeMillis() / 1000,
                settings.calmBelowKmh);
        if (wind != null) {
            weightedHeadwind += wind.headwindKmh;
            windFixes++;
        }
        long remainingSeconds = route.distanceMeters <= 0 ? 0 : Math.round(
                (analysis == null ? route.durationSeconds : analysis.route.durationSeconds)
                        * progress.distanceRemainingMeters / route.distanceMeters);
        // Publish a complete immutable value, never half an update to the screen.
        return new RideUpdate(point, heading, progress, lastInstruction,
                tracker.isOffRoute(), elapsed, turns.distanceTo(next, progress.distanceAlongMeters),
                tracker.riddenMeters, remainingSeconds, wind);
    }

    private List<CuePlanner.Event> turnEvents(Route route) {
        List<Double> starts = new TurnGuide(route).starts;
        return settings.cueTurns ? CuePlanner.turnEvents(route.steps, starts,
                new CueBuilder(getResources().getStringArray(R.array.cue_words),
                        settings.distanceUnit), settings.cueAheadMeters) : new ArrayList<>();
    }

    private void speak(String text) {
        if (!speechReady) return;
        requestAudioFocus();
        speaking = true;
        activeUtteranceId = "ride-" + System.nanoTime();
        if (speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, activeUtteranceId)
                == TextToSpeech.ERROR) finishSpeech();
    }

    private void drainSpeech() {
        if (!running || !speechReady || lastUpdate == null) return;
        if (urgentCue != null && !speaking) {
            String text = urgentCue;
            urgentCue = null;
            speakingTurn = false;
            speak(text);
            return;
        }
        if (tracker.isOffRoute() || speaking && speakingTurn) return;
        CuePlanner.Event event = planner.nextEvent(lastUpdate.progress.distanceAlongMeters,
                SystemClock.elapsedRealtime() / 1000, speaking);
        if (event == null) return;
        speakingTurn = event.kind == CuePlanner.Kind.TURN_EARLY
                || event.kind == CuePlanner.Kind.TURN_NOW;
        // Only one sentence enters TTS at a time. Pending features stay in the
        // planner, where a fresh fix can discard them if they have been passed.
        speak(event.text);
    }

    private void finishSpeech() {
        speaking = false;
        if (!speakingTurn) planner.nonTurnFinished(SystemClock.elapsedRealtime() / 1000);
        releaseAudioFocus();
        if (arriving) stopSelf();
        else {
            drainSpeech();
            main.postDelayed(this::drainSpeech, CuePlanner.NON_TURN_GAP_SECONDS * 1000);
        }
    }

    private void releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audio.abandonAudioFocusRequest(focusRequest);
        } else if (audio != null) audio.abandonAudioFocus(null);
    }

    private void requestAudioFocus() {
        // Borrow focus only for a sentence. MAY_DUCK lets a music app lower its
        // volume rather than stop playback; release restores it when speech ends.
        if (Build.VERSION.SDK_INT >= 26) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes).build();
            audio.requestAudioFocus(focusRequest);
        } else {
            audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        speech.setLanguage(Locale.getDefault());
        speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { complete(id); }
            @Override public void onError(String id) { complete(id); }
            private void complete(String id) {
                main.post(() -> {
                    // A flushed non-turn can finish after its replacing turn
                    // starts. Ignore that old callback instead of releasing focus.
                    if (id.equals(activeUtteranceId)) finishSpeech();
                });
            }
        });
        speechReady = true;
        drainSpeech();
    }

    private Notification notification() {
        Intent stop = new Intent(this, RideService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_my_location)
                .setContentTitle(getString(R.string.ride_notification))
                .setContentText(lastInstruction).setContentIntent(content)
                .setOngoing(true).setOnlyAlertOnce(true)
                .addAction(0, getString(R.string.stop_ride), stopAction).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                getString(R.string.ride_channel), NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public void stopRide(boolean arrived) {
        if (!running) { stopSelf(); return; }
        running = false;
        stopLocations();
        stopForeground(STOP_FOREGROUND_REMOVE);
        arriving = arrived;
        if (arrived && speechReady) {
            speakingTurn = true;
            speak(getString(R.string.cue_arrived));
            // A broken TTS engine must not keep a stopped service alive forever.
            main.postDelayed(this::stopSelf, 8000);
        }
        if (listener != null) listener.onRideStopped(arrived);
        if (!arrived || !speechReady) stopSelf();
    }

    @Override public void onDestroy() {
        stopLocations();
        if (speech != null) speech.shutdown();
        main.removeCallbacksAndMessages(null);
        releaseAudioFocus();
        super.onDestroy();
    }

    private void stopLocations() {
        if (locations == null) return;
        try {
            LocationManagerCompat.removeUpdates(locations, this);
        } catch (SecurityException ignored) {
            // A permission revocation may race the Stop tap. Android has already
            // revoked access, but notification and service cleanup must continue.
        }
    }

    @Override public void onProviderDisabled(String provider) {
        if (!running) return;
        stopLocations();
        try { startLocations(); }
        catch (SecurityException | IllegalArgumentException error) { stopRide(false); }
    }
}
