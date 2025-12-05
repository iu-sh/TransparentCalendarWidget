package com.antigravity.transparentcalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationReceiver", "Received intent: ${intent.action}")

        if (intent.action == LiveNotificationManager.ACTION_UPDATE_NOTIFICATION) {
            val pendingResult = goAsync()
            Thread {
                try {
                    LiveNotificationManager.refreshNotifications(context)
                } finally {
                    pendingResult.finish()
                }
            }.start()
        }
    }
}
