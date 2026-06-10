package it.shinyup.meteoradar.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.shinyup.meteoradar.MainActivity
import it.shinyup.meteoradar.R
import it.shinyup.meteoradar.data.models.AlertLevel
import it.shinyup.meteoradar.data.models.WeatherAlert

object NotificationHelper {

    private const val CHANNEL_ALERTS = "weather_alerts"
    private const val CHANNEL_INFO = "weather_info"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Allerte meteo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche per temporali, grandine e pioggia intensa"
                enableVibration(true)
                enableLights(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INFO,
                "Informazioni meteo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aggiornamenti meteo generali"
            }
        )
    }

    fun sendAlertNotification(context: Context, alert: WeatherAlert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, alert.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (alert.level == AlertLevel.INFO) CHANNEL_INFO else CHANNEL_ALERTS
        val priority = when (alert.level) {
            AlertLevel.EXTREME, AlertLevel.DANGER -> NotificationCompat.PRIORITY_MAX
            AlertLevel.WARNING -> NotificationCompat.PRIORITY_HIGH
            AlertLevel.INFO -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(alertIcon(alert.level))
            .setContentTitle(alert.title)
            .setContentText(alert.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.description))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(alert.id + 1000, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }

    private fun alertIcon(level: AlertLevel): Int = when (level) {
        AlertLevel.EXTREME, AlertLevel.DANGER -> R.drawable.ic_alert_danger
        AlertLevel.WARNING -> R.drawable.ic_alert_warning
        AlertLevel.INFO -> R.drawable.ic_alert_info
    }
}
