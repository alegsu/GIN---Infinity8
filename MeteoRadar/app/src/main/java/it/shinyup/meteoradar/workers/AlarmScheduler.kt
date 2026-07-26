package it.shinyup.meteoradar.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import it.shinyup.meteoradar.utils.Prefs

/**
 * Doze-resilient heartbeat for weather-alert checks.
 *
 * WorkManager periodic jobs are heavily deferred (or skipped) once the OEM puts
 * the app to sleep, which is why alerts only fired when the app was opened.
 * AlarmManager.setAndAllowWhileIdle() is permitted to fire even in Doze
 * (rate-limited to ~1 per 9 min), so we use it as the primary trigger and
 * re-arm it each time it fires. No exact-alarm permission is required.
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 47110815

    fun schedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val intervalMin = prefs.getString(Prefs.CHECK_INTERVAL, "30")?.toLongOrNull() ?: 30L
        val safeInterval = intervalMin.coerceAtLeast(15L)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + safeInterval * 60_000L
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        } catch (_: SecurityException) {
            // Some OEMs restrict alarms; WorkManager periodic remains as fallback.
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_CHECK
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
