package com.antigravity.transparentcalendar

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "event_notification_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SNOOZE_DIALOG = "com.antigravity.transparentcalendar.ACTION_SNOOZE_DIALOG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("EVENT_ID", -1)
        val title = intent.getStringExtra("EVENT_TITLE") ?: "Event"
        val start = intent.getLongExtra("EVENT_START", 0)
        val end = intent.getLongExtra("EVENT_END", 0)

        Log.d("EventAlarmReceiver", "Alarm received for $title")

        if (intent.action == ACTION_SNOOZE_DIALOG) {
            // Handle Snooze Action from Heads-up Notification
            // We reuse EventNotificationActivity to show the snooze dialog
            val activityIntent = Intent(context, EventNotificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("EVENT_ID", eventId)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_START", start)
                putExtra("EVENT_END", end)
                // We might want to pass a flag to indicate immediate snooze mode if needed,
                // but the default UI has snooze controls anyway.
            }
            context.startActivity(activityIntent)

             // Cancel the notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)

            return
        }

        // Schedule the next event now that this one has fired
        NotificationScheduler.scheduleNextEvent(context)

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        if (isLocked) {
            // Show Full Screen Intent
            val activityIntent = Intent(context, EventNotificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra("EVENT_ID", eventId)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_START", start)
                putExtra("EVENT_END", end)
            }

            // For full screen intent, we still need to build a notification that triggers it
            showNotification(context, title, activityIntent, true)
        } else {
            // Show Heads-up Notification
            // We need a dummy intent for content intent if we want;
            // usually clicking it opens the calendar or the app.
            // But here we need a Snooze ACTION.
            val contentIntent = Intent(context, MainActivity::class.java) // or open calendar
            showNotification(context, title, contentIntent, false, eventId, start, end)
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        intent: Intent,
        isFullScreen: Boolean,
        eventId: Long = -1,
        start: Long = 0,
        end: Long = 0
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Event Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming events"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = if (isFullScreen) {
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
             PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher) // Ensure this exists or use android.R.drawable.ic_lock_idle_alarm
            .setContentTitle(title)
            .setContentText("Event is starting now")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        if (isFullScreen) {
            builder.setFullScreenIntent(pendingIntent, true)
        } else {
            builder.setContentIntent(pendingIntent)

            // Add Snooze Action
            val snoozeIntent = Intent(context, EventAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_DIALOG
                putExtra("EVENT_ID", eventId)
                putExtra("EVENT_TITLE", title)
                putExtra("EVENT_START", start)
                putExtra("EVENT_END", end)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(R.drawable.ic_launcher, "Snooze", snoozePendingIntent) // Replace icon if needed
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
