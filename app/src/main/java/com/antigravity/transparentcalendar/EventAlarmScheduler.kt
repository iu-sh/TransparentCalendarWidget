package com.antigravity.transparentcalendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

object EventAlarmScheduler {
    private const val TAG = "EventAlarmScheduler"
    private const val PREFS_NAME = "scheduled_alarms"
    private const val KEY_SCHEDULED_IDS = "scheduled_ids"
    private const val KEY_ALARM_DETAILS = "alarm_details"
    private const val KEY_SNOOZED_ALARMS = "snoozed_alarms"
    
    fun scheduleUpcomingAlarms(context: Context) {
        Log.d(TAG, "Scheduling upcoming alarms")
        
        // Check if notifications are enabled
        val settingsPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        if (!settingsPrefs.getBoolean("notifications_enabled", true)) {
            Log.d(TAG, "Notifications disabled, skipping alarm scheduling")
            return
        }
        
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_CALENDAR permission not granted")
            return
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Get old scheduled alarms
        val oldScheduledIds = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet()) ?: emptySet()
        
        // Query events for the next 24 hours
        val now = System.currentTimeMillis()
        val endRange = now + DateUtils.DAY_IN_MILLIS
        
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        
        val selection = "${CalendarContract.Instances.BEGIN} > ? AND ${CalendarContract.Instances.BEGIN} <= ?"
        val selectionArgs = arrayOf(now.toString(), endRange.toString())
        
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, endRange)
        
        val newScheduledIds = mutableSetOf<String>()
        val alarmDetailsJson = JSONArray()
        
        try {
            val cursor: Cursor? = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
                val allDayIndex = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                
                while (it.moveToNext()) {
                    val eventId = it.getLong(idIndex)
                    val title = it.getString(titleIndex) ?: "No Title"
                    val startTime = it.getLong(beginIndex)
                    val endTime = it.getLong(endIndex)
                    val isAllDay = it.getInt(allDayIndex) == 1
                    
                    // Skip all-day events - they don't need time-based notifications
                    if (isAllDay) continue
                    
                    // Create unique ID for this instance (eventId + startTime)
                    val uniqueId = "${eventId}_$startTime"
                    newScheduledIds.add(uniqueId)
                    
                    // Store alarm details
                    val alarmJson = JSONObject().apply {
                        put("uniqueId", uniqueId)
                        put("eventId", eventId)
                        put("title", title)
                        put("triggerTime", startTime)
                        put("endTime", endTime)
                        put("isSnoozed", false)
                    }
                    alarmDetailsJson.put(alarmJson)
                    
                    // Only schedule if not already scheduled
                    if (!oldScheduledIds.contains(uniqueId)) {
                        scheduleAlarm(context, alarmManager, eventId, title, startTime, endTime, uniqueId.hashCode())
                    }
                }
            }
            
            // Cancel alarms for events that are no longer in the next 24h window
            for (oldId in oldScheduledIds) {
                if (!newScheduledIds.contains(oldId)) {
                    cancelAlarm(context, alarmManager, oldId.hashCode())
                }
            }
            
            // Add snoozed alarms to the list
            val snoozedJson = prefs.getString(KEY_SNOOZED_ALARMS, "[]") ?: "[]"
            val snoozedArray = JSONArray(snoozedJson)
            for (i in 0 until snoozedArray.length()) {
                val snoozed = snoozedArray.getJSONObject(i)
                if (snoozed.getLong("triggerTime") > now) {
                    alarmDetailsJson.put(snoozed)
                }
            }
            
            // Save new scheduled IDs and details
            editor.putStringSet(KEY_SCHEDULED_IDS, newScheduledIds)
            editor.putString(KEY_ALARM_DETAILS, alarmDetailsJson.toString())
            editor.apply()
            
            Log.d(TAG, "Scheduled ${newScheduledIds.size} alarms")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarms", e)
        }
    }
    
    fun getScheduledAlarms(context: Context): List<ScheduledAlarm> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val detailsJson = prefs.getString(KEY_ALARM_DETAILS, "[]") ?: "[]"
        val now = System.currentTimeMillis()
        
        return try {
            val jsonArray = JSONArray(detailsJson)
            (0 until jsonArray.length()).mapNotNull { i ->
                val obj = jsonArray.getJSONObject(i)
                val triggerTime = obj.getLong("triggerTime")
                if (triggerTime > now) {
                    ScheduledAlarm(
                        eventId = obj.getLong("eventId"),
                        title = obj.getString("title"),
                        triggerTime = triggerTime,
                        endTime = obj.getLong("endTime"),
                        isSnoozed = obj.optBoolean("isSnoozed", false),
                        uniqueId = obj.getString("uniqueId")
                    )
                } else null
            }.sortedBy { it.triggerTime }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing scheduled alarms", e)
            emptyList()
        }
    }
    
    fun cancelAlarmByUniqueId(context: Context, uniqueId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Cancel the alarm
        cancelAlarm(context, alarmManager, uniqueId.hashCode())
        
        // Remove from scheduled IDs
        val scheduledIds = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        scheduledIds.remove(uniqueId)
        
        // Remove from alarm details
        val detailsJson = prefs.getString(KEY_ALARM_DETAILS, "[]") ?: "[]"
        val jsonArray = JSONArray(detailsJson)
        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("uniqueId") != uniqueId) {
                newArray.put(obj)
            }
        }
        
        // Remove from snoozed alarms if present
        val snoozedJson = prefs.getString(KEY_SNOOZED_ALARMS, "[]") ?: "[]"
        val snoozedArray = JSONArray(snoozedJson)
        val newSnoozedArray = JSONArray()
        for (i in 0 until snoozedArray.length()) {
            val obj = snoozedArray.getJSONObject(i)
            if (obj.getString("uniqueId") != uniqueId) {
                newSnoozedArray.put(obj)
            }
        }
        
        prefs.edit()
            .putStringSet(KEY_SCHEDULED_IDS, scheduledIds)
            .putString(KEY_ALARM_DETAILS, newArray.toString())
            .putString(KEY_SNOOZED_ALARMS, newSnoozedArray.toString())
            .apply()
        
        Log.d(TAG, "Cancelled alarm: $uniqueId")
    }
    
    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        eventId: Long,
        title: String,
        startTime: Long,
        endTime: Long,
        requestCode: Int
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("event_title", title)
            putExtra("event_start", startTime)
            putExtra("event_end", endTime)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                startTime,
                pendingIntent
            )
            Log.d(TAG, "Scheduled alarm for '$title' at $startTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm - permission denied", e)
        }
    }
    
    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm with requestCode: $requestCode")
    }
    
    fun scheduleSnoozeAlarm(
        context: Context,
        eventId: Long,
        title: String,
        originalStart: Long,
        endTime: Long,
        snoozeMinutes: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val uniqueId = "${eventId}_snooze_$snoozeTime"
        val requestCode = uniqueId.hashCode()
        
        scheduleAlarm(context, alarmManager, eventId, title, snoozeTime, endTime, requestCode)
        
        // Save snoozed alarm to prefs
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val snoozedJson = prefs.getString(KEY_SNOOZED_ALARMS, "[]") ?: "[]"
        val snoozedArray = JSONArray(snoozedJson)
        
        val snoozedAlarm = JSONObject().apply {
            put("uniqueId", uniqueId)
            put("eventId", eventId)
            put("title", title)
            put("triggerTime", snoozeTime)
            put("endTime", endTime)
            put("isSnoozed", true)
        }
        snoozedArray.put(snoozedAlarm)
        
        prefs.edit().putString(KEY_SNOOZED_ALARMS, snoozedArray.toString()).apply()
        
        Log.d(TAG, "Scheduled snooze alarm for '$title' in $snoozeMinutes minutes")
    }
}

