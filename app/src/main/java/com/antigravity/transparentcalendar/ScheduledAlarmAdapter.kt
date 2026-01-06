package com.antigravity.transparentcalendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduledAlarmAdapter(
    private val onDismissClick: (ScheduledAlarm) -> Unit
) : RecyclerView.Adapter<ScheduledAlarmAdapter.AlarmViewHolder>() {

    private var alarms: List<ScheduledAlarm> = emptyList()

    fun updateAlarms(newAlarms: List<ScheduledAlarm>) {
        alarms = newAlarms.sortedBy { it.triggerTime }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scheduled_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(alarms[position])
    }

    override fun getItemCount(): Int = alarms.size

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.alarm_title)
        private val timeText: TextView = itemView.findViewById(R.id.alarm_time)
        private val snoozedBadge: TextView = itemView.findViewById(R.id.alarm_snoozed_badge)
        private val dismissBtn: TextView = itemView.findViewById(R.id.btn_dismiss_alarm)
        private val colorIndicator: View = itemView.findViewById(R.id.color_indicator)

        fun bind(alarm: ScheduledAlarm) {
            titleText.text = alarm.title
            timeText.text = formatTime(alarm.triggerTime)
            
            if (alarm.isSnoozed) {
                snoozedBadge.visibility = View.VISIBLE
            } else {
                snoozedBadge.visibility = View.GONE
            }

            dismissBtn.setOnClickListener {
                onDismissClick(alarm)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = Calendar.getInstance()
            val alarmTime = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            
            return when {
                isSameDay(now, alarmTime) -> "Today, ${timeFormat.format(Date(timestamp))}"
                isTomorrow(now, alarmTime) -> "Tomorrow, ${timeFormat.format(Date(timestamp))}"
                else -> "${dateFormat.format(Date(timestamp))}, ${timeFormat.format(Date(timestamp))}"
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun isTomorrow(today: Calendar, other: Calendar): Boolean {
            val tomorrow = today.clone() as Calendar
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)
            return isSameDay(tomorrow, other)
        }
    }
}
