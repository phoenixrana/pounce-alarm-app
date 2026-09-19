package dev.pounce.alarm

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WakeRulesTest {
    private fun now(text: String) = ZonedDateTime.parse(text)
    private val alarm = AlarmConfig(id = "test", hour = 7, minute = 0)
    private fun session(start: Long = 1_000_000L) = WakeSession(alarm = alarm, startedAt = start)

    @Test fun nextTimeTodayWhenFuture() {
        val t = now("2026-09-13T06:59:00+05:30[Asia/Kolkata]")
        assertEquals(t.withHour(7).withMinute(0), WakeRules.nextTime(alarm, t))
    }
    @Test fun exactMinuteIsStrictlyAfterNow() {
        val t = now("2026-09-13T07:00:00+05:30[Asia/Kolkata]")
        assertEquals(t.plusDays(1), WakeRules.nextTime(alarm, t))
    }
    @Test fun oneOffAfterItsTimeMeansTomorrow() {
        val t = now("2026-09-13T08:30:00+05:30[Asia/Kolkata]")
        assertEquals(t.plusDays(1).withHour(7).withMinute(0), WakeRules.nextTime(alarm.copy(days = emptySet()), t))
    }
    @Test fun weekdaySkipsWeekend() {
        val t = now("2026-09-11T08:00:00+05:30[Asia/Kolkata]")
        assertEquals(t.plusDays(3).withHour(7), WakeRules.nextTime(alarm.copy(days = (1..5).toSet()), t))
    }
    @Test fun weeklyAlarmCanBeSevenDaysAway() {
        val t = now("2026-09-14T07:00:00+05:30[Asia/Kolkata]")
        assertEquals(t.plusDays(7), WakeRules.nextTime(alarm.copy(days = setOf(1)), t))
    }
    @Test fun springGapResolvesForward() {
        val t = now("2026-03-08T01:00:00-05:00[America/New_York]")
        val result = WakeRules.nextTime(alarm.copy(hour = 2, minute = 30), t)
        assertEquals(3, result.hour)
        assertEquals(30, result.minute)
        assertEquals("-04:00", result.offset.toString())
    }
    @Test fun autumnOverlapFiresOnlyOneWallClockOccurrence() {
        val t = now("2026-11-01T01:45:00-04:00[America/New_York]")
        val result = WakeRules.nextTime(alarm.copy(hour = 1, minute = 30), t)
        assertEquals(2, result.dayOfMonth)
        assertEquals("-05:00", result.offset.toString())
    }
    @Test fun changedZoneUsesLocalWakeTime() {
        val t = ZonedDateTime.of(2026, 9, 13, 6, 0, 0, 0, ZoneId.of("Europe/London"))
        val result = WakeRules.nextTime(alarm, t)
        assertEquals(7, result.hour)
        assertEquals(ZoneId.of("Europe/London"), result.zone)
    }
    @Test fun rehearsalExpiresAtBoundary() {
        val s=session().copy(isTest=true)
        assertFalse(WakeRules.expired(s,s.startedAt+WakeRules.MAX_RING_MS-1))
        assertTrue(WakeRules.expired(s,s.startedAt+WakeRules.MAX_RING_MS))
    }
    @Test fun clockChangesDoNotDismissRealAlarm() {
        val s=session()
        assertFalse(WakeRules.expired(s,s.startedAt-600000))
        assertFalse(WakeRules.expired(s,s.startedAt+WakeRules.MAX_RING_MS*2))
    }
    @Test fun checkQuietEndsAtDeadline() {
        val s=session().copy(checkAt=1030000,followUpStage=1)
        assertTrue(WakeRules.quiet(s,1029999))
        assertFalse(WakeRules.quiet(s,1030000))
    }
}
