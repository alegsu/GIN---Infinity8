package it.shinyup.meteoradar.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Fired by [AlarmScheduler]'s Doze-permitted alarm. Runs one weather-alert
 * check (as an expedited one-time work request so it starts promptly) and
 * re-arms the next alarm.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "it.shinyup.meteoradar.ACTION_ALARM_CHECK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val work = OneTimeWorkRequestBuilder<WeatherAlertWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "weather_alert_now",
            ExistingWorkPolicy.REPLACE,
            work
        )

        // Re-arm for the next cycle.
        AlarmScheduler.schedule(context)
    }
}
