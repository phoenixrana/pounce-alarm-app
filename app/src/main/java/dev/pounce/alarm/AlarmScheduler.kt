package dev.pounce.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.ZonedDateTime

object AlarmScheduler {
    const val ACTION_FIRE = "dev.pounce.alarm.FIRE"
    fun canSchedule(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun nextAt(alarm: AlarmConfig): Long = WakeRules.nextTime(alarm, ZonedDateTime.now()).toInstant().toEpochMilli()

    private fun pending(context: Context, id: String, at: Long): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE; data = Uri.parse("pounce://alarm/" + Uri.encode(id))
            putExtra("alarmId", id); putExtra("at", at)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reschedule(context: Context, preservePending: Boolean = true) = synchronized(AlarmStore.lock) {
        val prefs = AlarmStore.prefs(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val previous = prefs.getStringSet("scheduled_ids", emptySet()).orEmpty().toSet()
        for (id in previous) manager.cancel(pending(context, id, 0))
        val edit = prefs.edit()
        for (id in previous) { edit.remove("scheduled_" + id); edit.remove("scheduled_config_" + id) }
        val ids = mutableSetOf<String>()
        if (canSchedule(context)) {
            for (alarm in AlarmStore.alarms(context).filter { it.enabled }) {
                val now = System.currentTimeMillis()
                val previousAt = prefs.getLong("scheduled_" + alarm.id, 0)
                val fingerprint = AlarmStore.encode(alarm).toString() + "|" + java.time.ZoneId.systemDefault().id
                val unchanged = preservePending && prefs.getString("scheduled_config_" + alarm.id, null) == fingerprint
                val recentlyDue = unchanged && previousAt in (now - 120_000)..now
                // Preserve a just-due alarm when boot or activity resume races its broadcast.
                val at = when {
                    unchanged && previousAt > now -> previousAt
                    recentlyDue -> now + 1000
                    else -> nextAt(alarm)
                }
                val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(context, alarm.id, at))
                    edit.putLong("scheduled_" + alarm.id, at)
                    edit.putString("scheduled_config_" + alarm.id, fingerprint); ids.add(alarm.id)
                } catch (_: SecurityException) { /* Permission can change between the check and scheduling. */ }
            }
        }
        check(edit.putStringSet("scheduled_ids", ids).commit())
        MorningWidget.updateAll(context)
    }

    internal fun fire(context: Context, id: String, at: Long) = synchronized(AlarmStore.lock) {
        if (at == 0L || AlarmStore.prefs(context).getLong("scheduled_" + id, 0) != at) return@synchronized
        val alarm = AlarmStore.alarms(context).find { it.id == id && it.enabled } ?: return@synchronized
        if (alarm.days.isEmpty()) AlarmStore.saveAlarm(context, alarm.copy(enabled = false))
        // Persist the firing before arming tomorrow, rejecting duplicate or stale broadcast deliveries.
        AlarmStore.prefs(context).edit().remove("scheduled_" + id).commit()
        AlarmService.startScheduled(context, alarm)
        reschedule(context)
    }
}


