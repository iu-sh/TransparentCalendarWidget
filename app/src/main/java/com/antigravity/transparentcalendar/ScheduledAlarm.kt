package com.antigravity.transparentcalendar

data class ScheduledAlarm(
    val eventId: Long,
    val title: String,
    val triggerTime: Long,
    val endTime: Long,
    val isSnoozed: Boolean = false,
    val uniqueId: String
)
