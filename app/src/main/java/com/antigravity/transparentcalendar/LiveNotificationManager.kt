package com.antigravity.transparentcalendar

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

object LiveNotificationManager {

    private const val CHANNEL_ID = "live_event_channel"
    private const val NOTIFICATION_ID = 1234
    private const val TAG = "LiveNotificationManager"
    const val ACTION_UPDATE_NOTIFICATION = "com.antigravity.transparentcalendar.UPDATE_NOTIFICATION"
    private const val EVENT_FETCH_DAYS = 30

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Live Events"
            val descriptionText = "Shows current event duration"
            val importance = NotificationManager.IMPORTANCE_LOW // Low to avoid sound/vibration but visible
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun refreshNotifications(context: Context) {
        Log.d(TAG, "refreshNotifications")

        // Ensure channel exists
        createNotificationChannel(context)

        // Fetch events for longer window to prevent chain death
        val events = CalendarRepository(context).fetchEvents(EVENT_FETCH_DAYS)
        val now = System.currentTimeMillis()

        // Filter for active events
        val activeEvents = events.filter { event ->
            if (event.isAllDay) {
                // Handle all-day event overlap with correct timezone logic
                val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

                utcCalendar.timeInMillis = event.start
                val startYear = utcCalendar.get(Calendar.YEAR)
                val startDay = utcCalendar.get(Calendar.DAY_OF_YEAR)
                val startVal = startYear * 400 + startDay

                utcCalendar.timeInMillis = event.end
                val endYear = utcCalendar.get(Calendar.YEAR)
                val endDay = utcCalendar.get(Calendar.DAY_OF_YEAR)
                val endVal = endYear * 400 + endDay

                // Compare with current local time day
                val localCalendar = Calendar.getInstance()
                localCalendar.timeInMillis = now
                val currentYear = localCalendar.get(Calendar.YEAR)
                val currentDay = localCalendar.get(Calendar.DAY_OF_YEAR)
                val currentVal = currentYear * 400 + currentDay

                currentVal >= startVal && currentVal < endVal
            } else {
                event.start <= now && now < event.end
            }
        }

        // Prioritize non-all-day events for notifications
        val timedEvents = activeEvents.filter { !it.isAllDay }
        val activeEvent = timedEvents.minByOrNull { it.end - it.start } ?: activeEvents.firstOrNull()

        var scheduleTime: Long? = null

        if (activeEvent != null) {
            showNotification(context, activeEvent)

            val activeEventEndTime = if (activeEvent.isAllDay) {
                 val localCalendar = Calendar.getInstance()
                 localCalendar.timeInMillis = now
                 localCalendar.set(Calendar.HOUR_OF_DAY, 0)
                 localCalendar.set(Calendar.MINUTE, 0)
                 localCalendar.set(Calendar.SECOND, 0)
                 localCalendar.set(Calendar.MILLISECOND, 0)
                 localCalendar.add(Calendar.DAY_OF_YEAR, 1)
                 localCalendar.timeInMillis
            } else {
                activeEvent.end
            }
            scheduleTime = activeEventEndTime
        } else {
            cancelNotification(context)
        }

        // Always check for the NEXT event's start time
        val nextEvent = events.filter { it.start > now }.minByOrNull { it.start }

        if (nextEvent != null) {
            scheduleTime = if (scheduleTime != null) {
                min(scheduleTime, nextEvent.start)
            } else {
                nextEvent.start
            }
        }

        // If no events scheduled soon, schedule a maintenance wake-up at end of fetch window
        // to prevent the notification chain from dying if the user has no events for >30 days.
        if (scheduleTime == null) {
            scheduleTime = now + (EVENT_FETCH_DAYS * DateUtils.DAY_IN_MILLIS)
        }

        scheduleUpdate(context, scheduleTime)
    }

    private fun showNotification(context: Context, event: EventModel) {
        Log.d(TAG, "Showing notification for event: ${event.title}")

        val startFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val endFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeText = if (event.isAllDay) {
            "All Day"
        } else {
            "${startFormat.format(Date(event.start))} - ${endFormat.format(Date(event.end))}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher) // Use existing icon or fallback
            .setContentTitle(event.title)
            .setContentText(timeText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(true)
            .setWhen(event.start)

        val intent = Intent(Intent.ACTION_VIEW)
        val eventUri = android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, event.id)
        intent.data = eventUri
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pendingIntent)

        // Use the app's launcher icon which is a calendar
        // Note: Full color icons might appear as white squares on some devices/Android versions
        // but this is per user request to use the app icon.
        builder.setSmallIcon(R.drawable.ic_launcher)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun cancelNotification(context: Context) {
        Log.d(TAG, "Cancelling notification")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun scheduleUpdate(context: Context, timeInMillis: Long) {
        Log.d(TAG, "Scheduling update for: ${Date(timeInMillis)}")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        intent.action = ACTION_UPDATE_NOTIFICATION

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             if (alarmManager.canScheduleExactAlarms()) {
                 alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
             } else {
                 Log.w(TAG, "Cannot schedule exact alarm, falling back to inexact")
                 alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
             }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        }
    }
}
