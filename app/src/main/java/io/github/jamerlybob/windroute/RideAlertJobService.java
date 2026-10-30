package io.github.jamerlybob.windroute;

import android.app.*;
import android.app.job.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.*;
import java.util.concurrent.*;
import io.github.jamerlybob.windroute.alerts.RideAlert;
import io.github.jamerlybob.windroute.route.*;
import io.github.jamerlybob.windroute.settings.*;
import io.github.jamerlybob.windroute.weather.*;
import io.github.jamerlybob.windroute.wind.DepartureScorer;

/** One free multi-location forecast request per successful evening check; no routing. */
public final class RideAlertJobService extends JobService {
    private static final String CHANNEL = "usual_ride";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Future<?> task;
    private volatile boolean stopped;
    private int generation;
    @Override public boolean onStartJob(JobParameters params) {
        stopped = false;
        final int run = ++generation;
        AlertsStore store = new AlertsStore(this);
        if (!store.enabled() || store.routeJson().isEmpty()) return false;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 17 || hour >= 23) {
            // A long offline spell must not turn an evening forecast into a morning alert.
            AlertsScheduler.align(this);
            if (params.getJobId() == AlertsScheduler.DAILY) getSystemService(JobScheduler.class).cancel(AlertsScheduler.DAILY);
            return false;
        }
        if (params.getJobId() == AlertsScheduler.ALIGN) AlertsScheduler.daily(this);
        long departure = RideAlert.tomorrow(System.currentTimeMillis(), store.hour(), store.minute(), TimeZone.getDefault());
        String day = RideAlert.dayKey(departure);
        if (day.equals(store.prefs.getString("checked", ""))) return false;
        String snapshot = store.routeJson();
        final int rideHour = store.hour(), rideMinute = store.minute();
        final boolean includeBad = store.bad();
        task = worker.submit(() -> {
            boolean retry = false;
            try {
                Settings settings = SettingsStore.load(this);
                Route original = RouteJson.read(snapshot);
                Route route = settings.ridingSpeedKmh == 0 ? original : original.withDuration(io.github.jamerlybob.windroute.units.UnitFormatter
                        .ridingDurationSeconds(original.distanceMeters, original.durationSeconds, settings.ridingSpeedKmh));
                List<Integer> indexes = RouteSamples.indexes(route.points);
                List<GeoPoint> points = new ArrayList<>();
                for (int index : indexes) points.add(route.points.get(index));
                // fetch(..., false) is one HTTP request, with hourly data only.
                List<WindForecast> forecasts = OpenMeteoClient.fetch(points, false);
                List<DepartureScorer.Entry> scores = DepartureScorer.score(route, indexes, forecasts,
                        departure, 1, settings.calmBelowKmh);
                if (scores.isEmpty()) throw new java.io.IOException("Forecast does not cover the ride");
                DepartureScorer.Entry entry = scores.get(0);
                RideAlert.Decision decision = RideAlert.decide(entry, includeBad);
                RideWeather weather = RideWeather.analyze(route, indexes, forecasts, departure);
                if (stopped || Thread.currentThread().isInterrupted() || !store.enabled()
                        || !snapshot.equals(store.routeJson()) || rideHour != store.hour()
                        || rideMinute != store.minute() || includeBad != store.bad()) {
                    main.post(() -> { if (!stopped && run == generation) jobFinished(params, false); });
                    return;
                }
                if (decision != RideAlert.Decision.NONE) {
                    store.preview(route, departure, new RouteForecastLoader.Result(indexes, forecasts));
                }
                main.post(() -> {
                    if (run != generation || stopped) return;
                    if (stopped || !store.enabled() || !snapshot.equals(store.routeJson()) || rideHour != store.hour()
                            || rideMinute != store.minute() || includeBad != store.bad()) { jobFinished(params, false); return; }
                    if (decision != RideAlert.Decision.NONE) notifyRide(entry, decision, weather, settings, departure);
                    store.prefs.edit().putString("checked", day).apply();
                    jobFinished(params, false);
                });
            } catch (Exception ignored) { retry = true; }
            if (retry) main.post(() -> { if (!stopped && run == generation) jobFinished(params, true); });
        });
        return true;
    }
    private void notifyRide(DepartureScorer.Entry entry, RideAlert.Decision decision,
                            RideWeather weather, Settings settings, long departure) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, getString(R.string.alerts_title), NotificationManager.IMPORTANCE_DEFAULT));
        String time = android.text.format.DateFormat.getTimeFormat(this).format(new Date(departure * 1000));
        int facts = decision == RideAlert.Decision.BAD
                ? (entry.averageHeadwindKmh >= 20 ? R.string.alert_headwind : R.string.alert_rain)
                : (entry.tailwindShare >= .5 ? R.string.alert_tailwind : R.string.alert_light_wind);
        String temperature = "";
        if (Double.isFinite(weather.coldestC)) {
            boolean fahrenheit = settings.temperatureUnit == Settings.TemperatureUnit.FAHRENHEIT;
            double value = fahrenheit ? weather.coldestC * 9 / 5 + 32 : weather.coldestC;
            temperature = getString(fahrenheit ? R.string.alert_fahrenheit : R.string.alert_celsius, Math.round(value));
        }
        String message = RideAlert.sentence(getString(decision == RideAlert.Decision.GOOD
                ? R.string.alert_good_sentence : R.string.alert_bad_sentence), time, getString(facts), temperature,
                getString(R.string.alert_temperature_suffix));
        // Preview lives in private storage, avoiding large route arrays in PendingIntent's Binder payload.
        Intent intent = new Intent(this, MainActivity.class).putExtra("ride_alert", true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent tap = PendingIntent.getActivity(this, 600, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(600, new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_wind)
                .setContentTitle(getString(R.string.alerts_title)).setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(tap)
                .setAutoCancel(true).build());
    }
    @Override public boolean onStopJob(JobParameters params) {
        stopped = true; generation++; if (task != null) task.cancel(true); return true;
    }
    @Override public void onDestroy() { stopped = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
