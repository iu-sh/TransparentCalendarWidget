package com.antigravity.transparentcalendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.util.Log

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("CalendarWidgetProvider", "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            Log.d("CalendarWidgetProvider", "updateAppWidget called for ID: $appWidgetId")
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)

            // Set Date
            val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
            views.setTextViewText(R.id.date_text, dateFormat.format(Date()))

            // Set Background Opacity
            val prefs = context.getSharedPreferences("com.antigravity.transparentcalendar.prefs", Context.MODE_PRIVATE)
            val opacity = prefs.getInt("opacity_$appWidgetId", 50) // Default 50%
            
            val alpha = (opacity * 255) / 100
            val backgroundColor = Color.argb(alpha, 0, 0, 0)
            views.setInt(R.id.widget_root, "setBackgroundColor", backgroundColor)
            
            // Set up "+" button pending intent
            try {
                val addEventIntent = Intent(Intent.ACTION_INSERT)
                addEventIntent.data = CalendarContract.Events.CONTENT_URI
                
                var flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                
                val addPendingIntent = PendingIntent.getActivity(
                    context, 
                    0, 
                    addEventIntent, 
                    flags
                )
                views.setOnClickPendingIntent(R.id.add_button, addPendingIntent)
            } catch (e: Exception) {
                Log.e("CalendarWidgetProvider", "Error setting button", e)
            }

            // Set up the collection
            try {
                val intent = Intent(context, CalendarWidgetService::class.java)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
                views.setRemoteAdapter(R.id.event_list, intent)
                views.setEmptyView(R.id.event_list, R.id.empty_view)
            } catch (e: Exception) {
                Log.e("CalendarWidgetProvider", "Error setting adapter", e)
            }

            // Set up item click pending intent template
            try {
                val clickIntentTemplate = Intent(Intent.ACTION_VIEW)
                // clickIntentTemplate.data = CalendarContract.Events.CONTENT_URI // Removed to allow fillInIntent to set data
                
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    flags = flags or PendingIntent.FLAG_MUTABLE
                }
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    // FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT = 16777216
                    flags = flags or 16777216 
                }
                
                val clickPendingIntent = PendingIntent.getActivity(
                    context, 
                    1, 
                    clickIntentTemplate, 
                    flags
                )
                views.setPendingIntentTemplate(R.id.event_list, clickPendingIntent)
            } catch (e: Exception) {
                Log.e("CalendarWidgetProvider", "Error setting template", e)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.event_list)
            Log.d("CalendarWidgetProvider", "updateAppWidget finished for ID: $appWidgetId")
            
            // Schedule the job to monitor changes
            CalendarUpdateJobService.scheduleJob(context)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (Intent.ACTION_TIMEZONE_CHANGED == action ||
            Intent.ACTION_TIME_CHANGED == action ||
            Intent.ACTION_DATE_CHANGED == action) {
            
            Log.d("CalendarWidgetProvider", "Time/Date change detected: $action")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CalendarWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            onUpdate(context, appWidgetManager, appWidgetIds)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.event_list)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CalendarUpdateJobService.scheduleJob(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
        jobScheduler.cancel(1001) // Cancel our job
    }
}
