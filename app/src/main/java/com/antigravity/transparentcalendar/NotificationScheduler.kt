package com.antigravity.transparentcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import java.util.Calendar

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    private const val PREFS_NAME = "NotificationPrefs"
    private const val PREF_LAST_HANDLED_ID = "last_handled_id"
    private const val PREF_LAST_HANDLED_START = "last_handled_start"
    private const val REQUEST_CODE_ALARM = 100

    fun scheduleNextEvent(context: Context) {
        val nextEvent = getNextEvent(context) ?: return

        Log.d(TAG, "Scheduling alarm for event: ${nextEvent.title} at ${nextEvent.start}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = "com.antigravity.transparentcalendar.EVENT_ALARM"
            putExtra("EVENT_ID", nextEvent.id)
            putExtra("EVENT_TITLE", nextEvent.title)
            putExtra("EVENT_START", nextEvent.start)
            putExtra("EVENT_END", nextEvent.end)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextEvent.start,
                        pendingIntent
                    )
                } else {
                     alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextEvent.start,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextEvent.start,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error scheduling alarm", e)
        }
    }

    fun scheduleSnooze(context: Context, eventId: Long, title: String, triggerTime: Long) {
        Log.d(TAG, "Scheduling snooze for event: $title at $triggerTime")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            action = "com.antigravity.transparentcalendar.EVENT_ALARM"
            putExtra("EVENT_ID", eventId)
            putExtra("EVENT_TITLE", title)
            putExtra("EVENT_START", triggerTime) // Start time is now the snooze time
             // We might want to pass original time too, but for notification purposes, this is fine
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM + eventId.toInt(), // Use ID to allow multiple snoozes if needed, or constant to overwrite
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                 if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                     alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error scheduling snooze", e)
        }
    }

    private fun getNextEvent(context: Context): EventModel? {
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY
        )

        // Look for events starting in the future (next 24 hours for efficiency)
        val endSearch = now + 24 * 60 * 60 * 1000
        val selection = "${CalendarContract.Instances.BEGIN} > ?"
        val selectionArgs = arrayOf(now.toString())
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC LIMIT 1"

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, now)
        android.content.ContentUris.appendId(builder, endSearch)

        var cursor: Cursor? = null
        try {
             cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1) ?: "No Title"
                val start = cursor.getLong(2)
                val end = cursor.getLong(3)
                val color = cursor.getInt(4)
                val allDay = cursor.getInt(5) == 1

                return EventModel(id, title, start, end, color, allDay)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read calendar", e)
        } finally {
            cursor?.close()
        }
        return null
    }
}
