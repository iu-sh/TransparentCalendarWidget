package com.antigravity.transparentcalendar

data class EventModel(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val color: Int,
    val isAllDay: Boolean
)
