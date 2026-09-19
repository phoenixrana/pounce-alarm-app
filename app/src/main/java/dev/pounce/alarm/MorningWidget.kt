package dev.pounce.alarm

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Offline home-screen glance. Every interaction opens Pounce; nothing dismisses an alarm. */
class MorningWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, render(context)) }
    }

    companion object {
        /** Invoke after changing alarms, rescheduling, or changing the active wake session. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MorningWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.pounce_widget)
            val now = ZonedDateTime.now()
            val next = AlarmStore.alarms(context).filter { it.enabled }
                .mapNotNull { alarm -> runCatching { alarm to WakeRules.nextTime(alarm, now) }.getOrNull() }
                .minByOrNull { it.second.toInstant() }
            val active = AlarmStore.session(context)
            val canSchedule = android.os.Build.VERSION.SDK_INT < 31 ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            val heading: String
            val time: String
            val detail: String
            when {
                active != null -> {
                    heading = "GOOD MORNING, HUMAN"
                    time = "Paws on the floor"
                    detail = "Your wake-up missions are waiting"
                }
                next != null -> {
                    heading = if (canSchedule) "YOUR NEXT MORNING" else "ALARM TIMING NEEDS SETUP"
                    time = DateFormat.getTimeFormat(context).format(Date.from(next.second.toInstant()))
                    val day = when (next.second.toLocalDate()) {
                        now.toLocalDate() -> "Today"
                        now.toLocalDate().plusDays(1) -> "Tomorrow"
                        else -> next.second.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                    }
                    detail = if (canSchedule) "$day · ${next.first.label.ifBlank { "Let's wake up" }}"
                        else "Tap Pounce to allow exact alarms"
                }
                else -> {
                    heading = "MAKE MORNINGS YOURS"
                    time = "A fresh start"
                    detail = "Tap to set your next alarm"
                }
            }
            views.setTextViewText(R.id.widget_heading, heading)
            views.setTextViewText(R.id.widget_time, time)
            views.setTextViewText(R.id.widget_detail, detail)
            views.setTextViewText(R.id.widget_brand, "POUNCE")
            val launch = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 7319, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}
