package dev.pounce.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> AlarmScheduler.fire(context,
                intent.getStringExtra("alarmId") ?: return, intent.getLongExtra("at", 0))
            AlarmService.ACTION_RECOVER -> AlarmService.recover(context,
                intent.getStringExtra("sessionToken") ?: return)
            AlarmService.ACTION_TEST -> AlarmService.fireTest(context, intent.getLongExtra("at", 0))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED)
                    AlarmService.cancelTest(context)
                AlarmScheduler.reschedule(context, preservePending = intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED)
                AlarmService.rearmSession(context)
            }
        }
    }
}

