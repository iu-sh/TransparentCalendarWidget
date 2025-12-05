package com.antigravity.transparentcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, refreshing notifications")
            val pendingResult = goAsync()
            Thread {
                try {
                    LiveNotificationManager.refreshNotifications(context)
                    // Also reschedule job monitoring
                    CalendarUpdateJobService.scheduleJob(context)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }
    }
}
