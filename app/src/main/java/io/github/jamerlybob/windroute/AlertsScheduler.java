package io.github.jamerlybob.windroute;

import android.app.job.*;
import android.content.*;
import java.util.TimeZone;
import io.github.jamerlybob.windroute.alerts.RideAlert;

/** Platform jobs avoid adding WorkManager, which would require a new dependency. */
final class AlertsScheduler {
    static final int ALIGN = 600, DAILY = 601;
    private static final long DAY = 24 * 60 * 60 * 1000L;
    private AlertsScheduler() { }
    static void update(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!new AlertsStore(context).enabled()) { scheduler.cancel(ALIGN); scheduler.cancel(DAILY); return; }
        if (scheduler.getPendingJob(ALIGN) == null && scheduler.getPendingJob(DAILY) == null) align(context);
    }
    static void align(Context context) {
        long delay = RideAlert.nextEvening(System.currentTimeMillis(), TimeZone.getDefault()) - System.currentTimeMillis();
        context.getSystemService(JobScheduler.class).schedule(builder(context, ALIGN)
                .setMinimumLatency(Math.max(0, delay)).build());
    }
    static void daily(Context context) {
        // A periodic job cannot also have minimum latency. The initial one-off
        // aligns its first period to evening. Android may delay either job;
        // the worker realigns if constraints push it outside the evening window.
        context.getSystemService(JobScheduler.class).schedule(builder(context, DAILY)
                .setPeriodic(DAY, 2 * 60 * 60 * 1000L).build());
    }
    private static JobInfo.Builder builder(Context context, int id) {
        return new JobInfo.Builder(id, new ComponentName(context, RideAlertJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setBackoffCriteria(30 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
    }
}
