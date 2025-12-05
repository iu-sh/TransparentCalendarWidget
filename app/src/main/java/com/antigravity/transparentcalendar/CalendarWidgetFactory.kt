package com.antigravity.transparentcalendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.CalendarContract
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import android.util.Log

class CalendarWidgetFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    sealed interface WidgetListItem {
        data class EventItem(val event: EventModel, val uniqueId: Long) : WidgetListItem
        data class HeaderItem(val dateText: String, val id: Long) : WidgetListItem
    }

    private var widgetItems: List<WidgetListItem> = ArrayList()
    private val TAG = "CalendarWidgetFactory"

    override fun onCreate() {
        Log.d(TAG, "onCreate")
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged")
        val events = fetchEvents()
        widgetItems = processEvents(events)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        widgetItems = emptyList()
    }

    override fun getCount(): Int {
        return widgetItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= widgetItems.size) return RemoteViews(context.packageName, R.layout.widget_event_item)

        val item = widgetItems[position]

        return when (item) {
            is WidgetListItem.HeaderItem -> {
                val views = RemoteViews(context.packageName, R.layout.widget_date_header)
                views.setTextViewText(R.id.header_text, item.dateText)
                views
            }
            is WidgetListItem.EventItem -> {
                val event = item.event
                val views = RemoteViews(context.packageName, R.layout.widget_event_item)

                views.setTextViewText(R.id.event_title, event.title)

                val timeText = if (event.isAllDay) {
                    "All Day"
                } else {
                    val startFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val endFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    "${startFormat.format(Date(event.start))} - ${endFormat.format(Date(event.end))}"
                }
                views.setTextViewText(R.id.event_time, timeText)

                var color = event.color
                if (color == 0) {
                    color = context.getColor(R.color.purple_500) // Default fallback
                }


                views.setInt(R.id.item_background, "setColorFilter", color)

                val fillInIntent = Intent()
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
                fillInIntent.setDataAndType(eventUri, "vnd.android.cursor.item/event")
                fillInIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start)
                fillInIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
                views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

                views
            }
        }
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getItemId(position: Int): Long {
        return when (val item = widgetItems[position]) {
            is WidgetListItem.EventItem -> item.uniqueId
            is WidgetListItem.HeaderItem -> item.id
        }
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    private fun processEvents(events: List<EventModel>): List<WidgetListItem> {
        val items = ArrayList<WidgetListItem>()
        if (events.isEmpty()) return items

        val headerFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val todayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

        val calendar = Calendar.getInstance()
        // Reset to start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val todayStart = calendar.timeInMillis
        val todayDateText = todayFormat.format(Date(todayStart))

        // Helper for All-Day event calculations (All-day events are stored in UTC)
        val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))

        // Process for the next 7 days (matching fetchEvents range)
        for (i in 0..7) {
            val dayStart = calendar.timeInMillis
            val currentDayYear = calendar.get(Calendar.YEAR)
            val currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis

            val dayDateText = headerFormat.format(Date(dayStart))

            // Filter events that overlap with this day
            val dayEvents = events.filter { event ->
                if (event.isAllDay) {
                    // For all-day events, compare dates using UTC to avoid timezone bleed
                    utcCalendar.timeInMillis = event.start
                    val startYear = utcCalendar.get(Calendar.YEAR)
                    val startDay = utcCalendar.get(Calendar.DAY_OF_YEAR)
                    // Simple comparable value: Year * 400 + Day (sufficient for near-term)
                    val startVal = startYear * 400 + startDay

                    utcCalendar.timeInMillis = event.end
                    val endYear = utcCalendar.get(Calendar.YEAR)
                    val endDay = utcCalendar.get(Calendar.DAY_OF_YEAR)
                    val endVal = endYear * 400 + endDay

                    val loopVal = currentDayYear * 400 + currentDayOfYear

                    loopVal >= startVal && loopVal < endVal
                } else {
                    // Standard physical overlap for regular events
                    event.start < dayEnd && event.end > dayStart
                }
            }

            if (dayEvents.isNotEmpty()) {
                // Add header if not today
                if (dayDateText != todayDateText) {
                    val headerId = ("header_" + dayDateText).hashCode().toLong()
                    items.add(WidgetListItem.HeaderItem(dayDateText, headerId))
                }

                for (event in dayEvents) {
                    // Create a unique ID for this specific day's entry of the event
                    val uniqueId = (event.id.toString() + "_" + dayStart).hashCode().toLong()
                    items.add(WidgetListItem.EventItem(event, uniqueId))
                }
            }
        }

        return items
    }

    private fun fetchEvents(): List<EventModel> {
        return CalendarRepository(context).fetchEvents(7)
    }
}
