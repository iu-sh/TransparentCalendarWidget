package com.antigravity.transparentcalendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "calendar_events"
    private const val CHANNEL_NAME = "Calendar Events"
    
    fun showEventNotification(
        context: Context,
        eventId: Long,
        title: String,
        startTime: Long,
        endTime: Long
    ) {
        Log.d(TAG, "Showing notification for: $title")
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Get settings
        val settingsPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val vibrationEnabled = settingsPrefs.getBoolean("vibration_enabled", true)
        
        // Create notification channel for Android O+
        createNotificationChannel(context, notificationManager, vibrationEnabled)
        
        // Create fullscreen intent
        val fullscreenIntent = Intent(context, NotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("event_id", eventId)
            putExtra("event_title", title)
            putExtra("event_start", startTime)
            putExtra("event_end", endTime)
            putExtra("notification_id", eventId.toInt())
        }
        
        val fullscreenPendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            fullscreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification - make it dismissible so it works well with WearOS
        // Sound and vibration are handled by AlarmReceiver directly for reliability
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("Starting now")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)  // Auto dismiss when tapped
            .setOngoing(false)    // Allow swipe to dismiss (needed for WearOS)
            .setFullScreenIntent(fullscreenPendingIntent, true)
            .setContentIntent(fullscreenPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(false)  // Allow bridging to WearOS
        
        val notification = builder.build()
        
        try {
            notificationManager.notify(eventId.toInt(), notification)
            Log.d(TAG, "Notification posted with ID: ${eventId.toInt()}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification - permission denied", e)
        }
    }
    
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled notification: $notificationId")
    }
    
    private fun createNotificationChannel(
        context: Context,
        notificationManager: NotificationManager,
        vibrationEnabled: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete existing channel to apply updated settings
            notificationManager.deleteNotificationChannel(CHANNEL_ID)
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Calendar event notifications"
                enableVibration(vibrationEnabled)
                if (vibrationEnabled) {
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}

