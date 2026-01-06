package com.antigravity.transparentcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received")
        
        val eventId = intent.getLongExtra("event_id", -1)
        val eventTitle = intent.getStringExtra("event_title") ?: "Event"
        val eventStart = intent.getLongExtra("event_start", 0)
        val eventEnd = intent.getLongExtra("event_end", 0)
        
        if (eventId == -1L) {
            Log.e(TAG, "Invalid event ID")
            return
        }
        
        Log.d(TAG, "Triggering notification for: $eventTitle")
        
        // Get settings
        val settingsPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val soundEnabled = settingsPrefs.getBoolean("sound_enabled", true)
        val vibrationEnabled = settingsPrefs.getBoolean("vibration_enabled", true)
        
        // Play sound and vibrate immediately (before anything else)
        if (soundEnabled) {
            playSound(context, settingsPrefs.getString("notification_sound_uri", null))
        }
        if (vibrationEnabled) {
            vibrate(context)
        }
        
        // Check if screen is off - if so, directly launch the activity
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive
        
        if (!isScreenOn) {
            // Screen is off, launch activity directly for guaranteed fullscreen
            Log.d(TAG, "Screen is off, launching activity directly")
            launchNotificationActivity(context, eventId, eventTitle, eventStart, eventEnd)
        }
        
        // Always show notification (for notification bar, wearOS bridge, etc.)
        NotificationHelper.showEventNotification(
            context,
            eventId,
            eventTitle,
            eventStart,
            eventEnd
        )
    }
    
    private fun playSound(context: Context, soundUriString: String?) {
        try {
            val soundUri = if (soundUriString != null) {
                Uri.parse(soundUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            
            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            ringtone?.play()
            Log.d(TAG, "Playing sound: $soundUri")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound", e)
        }
    }
    
    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            Log.d(TAG, "Vibrating")
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating", e)
        }
    }
    
    private fun launchNotificationActivity(
        context: Context,
        eventId: Long,
        eventTitle: String,
        eventStart: Long,
        eventEnd: Long
    ) {
        val activityIntent = Intent(context, NotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("event_id", eventId)
            putExtra("event_title", eventTitle)
            putExtra("event_start", eventStart)
            putExtra("event_end", eventEnd)
            putExtra("notification_id", eventId.toInt())
        }
        
        try {
            context.startActivity(activityIntent)
            Log.d(TAG, "Activity launched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch activity", e)
        }
    }
}

