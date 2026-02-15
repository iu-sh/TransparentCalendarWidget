package com.antigravity.transparentcalendar

import android.app.Activity
import android.app.KeyguardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.antigravity.transparentcalendar.databinding.ActivityNotificationBinding

class NotificationActivity : Activity() {
    
    companion object {
        private const val TAG = "NotificationActivity"
        private val SNOOZE_VALUES = intArrayOf(5, 10, 15, 30, 60, 90, 120)
    }
    
    private lateinit var binding: ActivityNotificationBinding
    
    private var eventId: Long = -1
    private var eventTitle: String = ""
    private var eventStart: Long = 0
    private var eventEnd: Long = 0
    private var notificationId: Int = 0
    private var currentSnoozeIndex = 0 // Default to 5 minutes
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lock screen
        setupLockScreenFlags()
        
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get event details from intent
        eventId = intent.getLongExtra("event_id", -1)
        eventTitle = intent.getStringExtra("event_title") ?: "Event"
        eventStart = intent.getLongExtra("event_start", 0)
        eventEnd = intent.getLongExtra("event_end", 0)
        notificationId = intent.getIntExtra("notification_id", 0)
        
        Log.d(TAG, "Opened for event: $eventTitle (ID: $eventId)")
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        // Keep screen on while this activity is visible
        // Removed to allow screen timeout
        // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun setupUI() {
        binding.eventTitle.text = eventTitle
        updateSnoozeDisplay()
    }
    
    private fun updateSnoozeDisplay() {
        binding.snoozeMinutes.text = "${SNOOZE_VALUES[currentSnoozeIndex]}"
    }
    
    private fun setupClickListeners() {
        // Minus button
        binding.btnMinus.setOnClickListener {
            if (currentSnoozeIndex > 0) {
                currentSnoozeIndex--
                updateSnoozeDisplay()
            }
        }
        
        // Plus button
        binding.btnPlus.setOnClickListener {
            if (currentSnoozeIndex < SNOOZE_VALUES.size - 1) {
                currentSnoozeIndex++
                updateSnoozeDisplay()
            }
        }
        
        // Snooze button
        binding.btnSnooze.setOnClickListener {
            snoozeEvent()
        }
        
        // Dismiss (X) button
        binding.btnDismiss.setOnClickListener {
            dismissNotification()
        }
    }
    
    private fun snoozeEvent() {
        val snoozeMinutes = SNOOZE_VALUES[currentSnoozeIndex]
        Log.d(TAG, "Snoozing event for $snoozeMinutes minutes")
        
        // Schedule a new alarm for the snoozed time
        // Note: We don't modify the calendar event because:
        // 1. The eventId from Instances may not match the actual Events table ID
        // 2. Recurring events have complex modification semantics
        // 3. Users may not want the actual event time changed, just the reminder
        EventAlarmScheduler.scheduleSnoozeAlarm(
            this,
            eventId,
            eventTitle,
            eventStart,
            eventEnd,
            snoozeMinutes
        )
        
        Toast.makeText(this, "Snoozed for $snoozeMinutes min", Toast.LENGTH_SHORT).show()
        
        // Cancel notification and close activity
        NotificationHelper.cancelNotification(this, notificationId)
        finish()
    }
    
    private fun dismissNotification() {
        Log.d(TAG, "Dismissing notification")
        NotificationHelper.cancelNotification(this, notificationId)
        finish()
    }
    
    override fun onBackPressed() {
        // Dismiss on back press
        dismissNotification()
    }
}
