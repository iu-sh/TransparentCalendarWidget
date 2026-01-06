package com.antigravity.transparentcalendar

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventNotificationActivity : AppCompatActivity() {

    private var currentSnoozeIndex = 0
    private val snoozeOptions = listOf(5, 15, 30, 45, 60, 90, 120)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure full screen and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_event_notification)

        val title = intent.getStringExtra("EVENT_TITLE") ?: "Event"
        val start = intent.getLongExtra("EVENT_START", 0)
        val eventId = intent.getLongExtra("EVENT_ID", -1)

        val textTitle = findViewById<TextView>(R.id.eventTitle)
        val textTime = findViewById<TextView>(R.id.eventTime)
        val textSnoozeMinutes = findViewById<TextView>(R.id.textSnoozeMinutes)
        val btnDecrease = findViewById<Button>(R.id.btnDecreaseSnooze)
        val btnIncrease = findViewById<Button>(R.id.btnIncreaseSnooze)
        val btnSnooze = findViewById<Button>(R.id.btnSnooze)
        val btnDismiss = findViewById<Button>(R.id.btnDismiss)

        textTitle.text = title
        if (start > 0) {
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            textTime.text = format.format(Date(start))
        }

        updateSnoozeText(textSnoozeMinutes)

        btnDecrease.setOnClickListener {
            if (currentSnoozeIndex > 0) {
                currentSnoozeIndex--
                updateSnoozeText(textSnoozeMinutes)
            }
        }

        btnIncrease.setOnClickListener {
            if (currentSnoozeIndex < snoozeOptions.size - 1) {
                currentSnoozeIndex++
                updateSnoozeText(textSnoozeMinutes)
            }
        }

        btnSnooze.setOnClickListener {
            val snoozeMinutes = snoozeOptions[currentSnoozeIndex]
            val snoozeTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)

            NotificationScheduler.scheduleSnooze(this, eventId, title, snoozeTime)
            finish()
        }

        btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun updateSnoozeText(textView: TextView) {
        textView.text = snoozeOptions[currentSnoozeIndex].toString()
    }
}
