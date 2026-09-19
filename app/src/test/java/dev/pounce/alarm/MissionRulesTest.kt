package dev.pounce.alarm

import org.junit.Assert.*
import org.junit.Test

/** Pure state-machine regressions; storage round-trips and service token guards need device tests. */
class MissionRulesTest {
    private val started = 1_000_000L
    private fun session(
        kinds: List<MissionKind> = listOf(MissionKind.WALK, MissionKind.MEMORY, MissionKind.MATH),
        followUp: Boolean = true,
        isTest: Boolean = false
    ) = WakeSession(token = "wake-session", startedAt = started,
        alarm = AlarmConfig(id = "morning", missions = kinds, followUp = followUp), isTest = isTest)

    @Test fun emptyOrSingleSelectionStillRequiresTwoDistinctTasks() {
        assertEquals(listOf(MissionKind.WALK, MissionKind.MATH), MissionRules.stack(emptyList()))
        assertEquals(listOf(MissionKind.MATH, MissionKind.WALK), MissionRules.stack(listOf(MissionKind.MATH)))
        assertEquals(listOf(MissionKind.MEMORY, MissionKind.MATH), MissionRules.stack(listOf(MissionKind.MEMORY)))
    }

    @Test fun stackRemovesDuplicatesAndKeepsUserOrderWithFiveTaskMaximum() {
        val selection = listOf(MissionKind.TYPING, MissionKind.TYPING, MissionKind.WALK,
            MissionKind.MEMORY, MissionKind.COLORS, MissionKind.PHOTO, MissionKind.HUNT)
        assertEquals(listOf(MissionKind.TYPING, MissionKind.WALK, MissionKind.MEMORY,
            MissionKind.COLORS, MissionKind.PHOTO), MissionRules.stack(selection))
    }

    @Test fun advancingPreservesSessionIdentityAndDoesNotMutateOldSnapshot() {
        val before = session()
        val after = requireNotNull(MissionRules.advance(before, started + 50_000))
        assertEquals(before.token, after.token)
        assertEquals(before.startedAt, after.startedAt)
        assertEquals(before.alarm, after.alarm)
        assertEquals(1, after.missionIndex)
        assertEquals(1, after.completed)
        assertEquals(MissionKind.MEMORY, after.current())
        assertEquals(0, before.missionIndex)
        assertEquals(0, before.completed)
        // A repeated calculation from a stale immutable snapshot is the same proposed transition.
        // The service must compare its expected completed count before persisting that proposal.
        assertEquals(after, MissionRules.advance(before, started + 50_000))
    }

    @Test fun restoredProgressContinuesAtSavedTaskRatherThanRestarting() {
        val restored = session().copy(missionIndex = 1, completed = 1)
        assertEquals(MissionKind.MEMORY, restored.current())
        val next = requireNotNull(MissionRules.advance(restored, started + 90_000))
        assertEquals(2, next.missionIndex)
        assertEquals(2, next.completed)
        assertEquals(MissionKind.MATH, next.current())
    }

    @Test fun replacementRequiresBothTasksAndAdvancesOriginalStackOnlyOnce() {
        val original = session().copy(missionIndex = 1, completed = 1)
        val replacement = MissionRules.replace(original)
        assertEquals(MissionKind.MATH, replacement.current())
        assertEquals(1, replacement.missionIndex)
        assertEquals(1, replacement.completed)
        val first = requireNotNull(MissionRules.advance(replacement, started + 100_000))
        assertEquals(MissionKind.TYPING, first.current())
        assertEquals(1, first.missionIndex)
        assertEquals(2, first.completed)
        val second = requireNotNull(MissionRules.advance(first, started + 110_000))
        assertEquals(MissionKind.MATH, second.current())
        assertEquals(2, second.missionIndex)
        assertEquals(3, second.completed)
        assertTrue(second.replacement.isEmpty())
        assertEquals(0, second.replacementIndex)
        assertEquals(0, second.followUpStage)
    }

    @Test fun replacementCannotBeResetAfterCompletingItsFirstTask() {
        val first = requireNotNull(MissionRules.advance(MissionRules.replace(session()), started + 50_000))
        assertEquals(1, first.replacementIndex)
        assertEquals(first, MissionRules.replace(first))
    }

    @Test fun restoredReplacementResumesSecondReplacementTask() {
        val restored = session().copy(missionIndex = 1, completed = 2,
            replacement = listOf(MissionKind.MATH, MissionKind.TYPING), replacementIndex = 1)
        assertEquals(MissionKind.TYPING, restored.current())
        val next = requireNotNull(MissionRules.advance(restored, started + 100_000))
        assertEquals(2, next.missionIndex)
        assertEquals(3, next.completed)
        assertTrue(next.replacement.isEmpty())
    }

    @Test fun lastReplacementCannotSkipTheFinalCheck() {
        val last = session().copy(missionIndex = 2, completed = 2)
        val first = requireNotNull(MissionRules.advance(MissionRules.replace(last), started + 50_000))
        assertEquals(0, first.followUpStage)
        val second = requireNotNull(MissionRules.advance(first, started + 60_000))
        assertEquals(1, second.followUpStage)
        assertEquals(4, second.completed)
        assertEquals(started + 180_000, second.checkAt)
    }

    @Test fun finalCheckIsGatedForExactlyTwoMinutes() {
        val last = session().copy(missionIndex = 2, completed = 2)
        val waiting = requireNotNull(MissionRules.advance(last, started + 60_000))
        assertEquals(listOf(MissionKind.WALK, MissionKind.TYPING), waiting.stack())
        assertEquals(1, waiting.followUpStage)
        assertEquals(0, waiting.missionIndex)
        assertEquals(3, waiting.completed)
        assertEquals(started + 180_000, waiting.checkAt)
        assertTrue(WakeRules.quiet(waiting, waiting.checkAt - 1))
        assertEquals(waiting, MissionRules.advance(waiting, waiting.checkAt - 1))
        assertFalse(WakeRules.quiet(waiting, waiting.checkAt))
        val resumed = requireNotNull(MissionRules.advance(waiting, waiting.checkAt))
        assertEquals(1, resumed.missionIndex)
        assertEquals(4, resumed.completed)
        assertEquals(MissionKind.TYPING, resumed.current())
        assertNull(MissionRules.advance(resumed, waiting.checkAt + 20_000))
    }

    @Test fun finalCheckOccursOnceRatherThanStartingAnotherLoop() {
        val last = session().copy(followUpStage = 1, missionIndex = 1, completed = 4, checkAt = started)
        assertNull(MissionRules.advance(last, started + 60_000))
    }

    @Test fun disabledFollowUpStillRequiresTheWholeInitialStack() {
        val initial = session(followUp = false)
        val first = requireNotNull(MissionRules.advance(initial, started + 10_000))
        val second = requireNotNull(MissionRules.advance(first, started + 20_000))
        assertEquals(2, second.completed)
        assertNull(MissionRules.advance(second, started + 30_000))
    }

    @Test fun rehearsalCompletesWithoutTheDelayedCheck() {
        val last = session(isTest = true).copy(missionIndex = 2, completed = 2)
        assertNull(MissionRules.advance(last, started + 90_000))
    }

    @Test fun realAlarmDoesNotAutoDismissAfterTwentyMinutesOrClockChanges() {
        val real = session()
        assertFalse(WakeRules.expired(real, started + WakeRules.MAX_RING_MS))
        assertFalse(WakeRules.expired(real, started + 24 * 60 * 60_000L))
        assertFalse(WakeRules.expired(real, started - 10 * 60_000L))
    }

    @Test fun rehearsalTimeoutHasAnExactBoundary() {
        val rehearsal = session(isTest = true)
        assertFalse(WakeRules.expired(rehearsal, started + WakeRules.MAX_RING_MS - 1))
        assertTrue(WakeRules.expired(rehearsal, started + WakeRules.MAX_RING_MS))
    }

    @Test fun legacyQuietFieldsCannotMuteMissionMode() {
        val migrated = session().copy(graceUntil = started + 60_000, scanUntil = started + 60_000,
            graceUsed = true, scanUsed = true)
        assertFalse(WakeRules.quiet(migrated, started + 1))
    }
}
