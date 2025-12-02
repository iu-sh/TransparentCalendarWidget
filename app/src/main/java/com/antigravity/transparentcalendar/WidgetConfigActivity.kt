package com.antigravity.transparentcalendar

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.util.Log

class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val TAG = "WidgetConfigActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d(TAG, "onCreate called")
            setContentView(R.layout.activity_widget_config)

            val intent = intent
            val extras = intent.extras
            if (extras != null) {
                appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                Log.d(TAG, "Widget ID from extras: $appWidgetId")
            } else {
                Log.d(TAG, "No extras in intent")
            }

            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.e(TAG, "Invalid Widget ID, finishing")
                android.widget.Toast.makeText(this, "Invalid Widget ID", android.widget.Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            setResult(Activity.RESULT_CANCELED)

            val prefs = getSharedPreferences("com.antigravity.transparentcalendar.prefs", Context.MODE_PRIVATE)
            val currentOpacity = prefs.getInt("opacity_$appWidgetId", 50)

            val seekBar = findViewById<SeekBar>(R.id.opacity_seekbar)
            seekBar.progress = currentOpacity

            findViewById<Button>(R.id.save_button).setOnClickListener {
                Log.d(TAG, "Save button clicked")
                try {
                    val opacity = seekBar.progress
                    prefs.edit().putInt("opacity_$appWidgetId", opacity).apply()

                    // Update the widget
                    val appWidgetManager = AppWidgetManager.getInstance(this)
                    CalendarWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

                    val resultValue = Intent()
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(Activity.RESULT_OK, resultValue)
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving widget config", e)
                    android.widget.Toast.makeText(this, "Error saving: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            android.widget.Toast.makeText(this, "Error starting config: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
