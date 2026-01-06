package com.antigravity.transparentcalendar

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var switchSound: SwitchMaterial
    private lateinit var switchVibration: SwitchMaterial
    private lateinit var soundPickerRow: LinearLayout
    private lateinit var soundNameText: TextView
    private lateinit var alarmsRecyclerView: RecyclerView
    private lateinit var emptyAlarmsText: TextView
    
    private lateinit var alarmAdapter: ScheduledAlarmAdapter
    private val settingsPrefs by lazy { getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }

    private val requestCalendarPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
            val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
            
            if (readGranted) {
                requestNotificationPermission()
            } else {
                Toast.makeText(this, "Calendar permission denied.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            checkExactAlarmPermission()
        }

    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (uri != null) {
                    settingsPrefs.edit().putString("notification_sound_uri", uri.toString()).apply()
                    updateSoundName(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSettings()
        setupListeners()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadScheduledAlarms()
    }

    private fun initViews() {
        switchNotifications = findViewById(R.id.switch_notifications)
        switchSound = findViewById(R.id.switch_sound)
        switchVibration = findViewById(R.id.switch_vibration)
        soundPickerRow = findViewById(R.id.sound_picker_row)
        soundNameText = findViewById(R.id.sound_name)
        alarmsRecyclerView = findViewById(R.id.alarms_list)
        emptyAlarmsText = findViewById(R.id.empty_alarms)
        
        // Setup RecyclerView
        alarmAdapter = ScheduledAlarmAdapter { alarm ->
            dismissAlarm(alarm)
        }
        alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        alarmsRecyclerView.adapter = alarmAdapter
    }

    private fun loadSettings() {
        switchNotifications.isChecked = settingsPrefs.getBoolean("notifications_enabled", true)
        switchSound.isChecked = settingsPrefs.getBoolean("sound_enabled", true)
        switchVibration.isChecked = settingsPrefs.getBoolean("vibration_enabled", true)
        
        val soundUri = settingsPrefs.getString("notification_sound_uri", null)
        if (soundUri != null) {
            updateSoundName(Uri.parse(soundUri))
        } else {
            soundNameText.text = "Default"
        }
        
        updateSettingsEnabled()
    }

    private fun setupListeners() {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            updateSettingsEnabled()
            
            if (isChecked) {
                EventAlarmScheduler.scheduleUpcomingAlarms(this)
            }
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }

        soundPickerRow.setOnClickListener {
            openSoundPicker()
        }
    }

    private fun updateSettingsEnabled() {
        val enabled = switchNotifications.isChecked
        switchSound.isEnabled = enabled
        switchVibration.isEnabled = enabled
        soundPickerRow.isEnabled = enabled
        soundPickerRow.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun openSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            
            val existingUri = settingsPrefs.getString("notification_sound_uri", null)
            if (existingUri != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingUri))
            }
        }
        soundPickerLauncher.launch(intent)
    }

    private fun updateSoundName(uri: Uri) {
        val ringtone = RingtoneManager.getRingtone(this, uri)
        soundNameText.text = ringtone?.getTitle(this) ?: "Custom"
    }

    private fun loadScheduledAlarms() {
        val alarms = EventAlarmScheduler.getScheduledAlarms(this)
        alarmAdapter.updateAlarms(alarms)
        
        if (alarms.isEmpty()) {
            emptyAlarmsText.visibility = View.VISIBLE
            alarmsRecyclerView.visibility = View.GONE
        } else {
            emptyAlarmsText.visibility = View.GONE
            alarmsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun dismissAlarm(alarm: ScheduledAlarm) {
        EventAlarmScheduler.cancelAlarmByUniqueId(this, alarm.uniqueId)
        Toast.makeText(this, "Reminder dismissed", Toast.LENGTH_SHORT).show()
        loadScheduledAlarms()
    }

    private fun checkAndRequestPermissions() {
        val readCalendar = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
        val writeCalendar = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
        
        if (readCalendar != PackageManager.PERMISSION_GRANTED || 
            writeCalendar != PackageManager.PERMISSION_GRANTED) {
            requestCalendarPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        } else {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkExactAlarmPermission()
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "Please allow exact alarms for event notifications", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            }
        }
        
        scheduleAlarmsAndJob()
    }

    private fun scheduleAlarmsAndJob() {
        CalendarUpdateJobService.scheduleJob(this)
        EventAlarmScheduler.scheduleUpcomingAlarms(this)
        loadScheduledAlarms()
    }
}
