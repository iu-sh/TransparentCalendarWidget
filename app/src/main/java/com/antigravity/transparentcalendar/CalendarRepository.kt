package com.antigravity.transparentcalendar

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.util.Log

class CalendarRepository(private val context: Context) {

    companion object {
        private const val TAG = "CalendarRepository"
    }

    fun fetchEvents(days: Int = 7): List<EventModel> {
        Log.d(TAG, "fetchEvents called")
        val events = ArrayList<EventModel>()

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_CALENDAR permission not granted")
            return events
        }

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY
        )

        val now = System.currentTimeMillis()
        val endRange = now + (days * DateUtils.DAY_IN_MILLIS)

        val selection = "${CalendarContract.Instances.END} >= ? AND ${CalendarContract.Instances.BEGIN} <= ?"
        val selectionArgs = arrayOf(now.toString(), endRange.toString())

        // Construct the URI for the instance table
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, endRange)

        try {
            val cursor: Cursor? = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            if (cursor == null) {
                Log.e(TAG, "Cursor is null")
                return events
            }

            cursor.use {
                val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
                val colorIndex = it.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
                val allDayIndex = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                if (idIndex == -1 || titleIndex == -1 || beginIndex == -1 || endIndex == -1) {
                    Log.e(TAG, "Missing columns in cursor")
                    return events
                }

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val title = it.getString(titleIndex) ?: "No Title"
                    val start = it.getLong(beginIndex)
                    val end = it.getLong(endIndex)
                    val color = if (colorIndex != -1) it.getInt(colorIndex) else 0
                    val isAllDay = if (allDayIndex != -1) it.getInt(allDayIndex) == 1 else false

                    events.add(EventModel(id, title, start, end, color, isAllDay))
                }
            }
            Log.d(TAG, "Fetched ${events.size} events")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events", e)
            e.printStackTrace()
        }

        return events
    }
}
