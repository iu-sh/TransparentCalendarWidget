package com.antigravity.transparentcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device booted, rescheduling alarms")
            
            // Clear old scheduled IDs since AlarmManager clears on reboot
            val prefs = context.getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)
            prefs.edit().remove("scheduled_ids").apply()
            
            // Reschedule all upcoming alarms
            EventAlarmScheduler.scheduleUpcomingAlarms(context)
        }
    }
}
