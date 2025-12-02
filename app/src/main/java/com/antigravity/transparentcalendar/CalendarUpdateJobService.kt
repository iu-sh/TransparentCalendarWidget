package com.antigravity.transparentcalendar

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class CalendarUpdateJobService : JobService() {

    companion object {
        private const val JOB_ID = 1001
        private const val TAG = "CalendarUpdateJob"

        fun scheduleJob(context: Context) {
            val componentName = ComponentName(context, CalendarUpdateJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, componentName)
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        android.provider.CalendarContract.Events.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                // Add a small delay to batch updates if multiple changes happen quickly
                .setTriggerContentUpdateDelay(500) 
                .setTriggerContentMaxDelay(2000)
                .build()

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
            jobScheduler.schedule(jobInfo)
            Log.d(TAG, "Job scheduled to monitor Calendar changes")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob: Calendar content changed")
        
        // Trigger widget update
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, CalendarWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        if (appWidgetIds.isNotEmpty()) {
            Log.d(TAG, "Updating ${appWidgetIds.size} widgets")
            val intent = Intent(this, CalendarWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            sendBroadcast(intent)
            
            // Also notify data changed for the list view
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.event_list)
        }

        // Reschedule the job to keep monitoring
        scheduleJob(this)
        
        return false // Work is done synchronously
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Reschedule if stopped
    }
}
