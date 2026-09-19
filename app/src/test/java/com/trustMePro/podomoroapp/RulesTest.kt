package com.trustMePro.podomoroapp

import com.trustMePro.podomoroapp.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RulesTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = LocalDate.of(2026, 9, 19)
    @Test fun todayIncludesOverdueButNotMissedPlan() {
        assertTrue(TaskRules.matches(TaskItem(title = "a", dueDate = "2026-09-18"), TaskFilter.TODAY, day, ""))
        assertFalse(TaskRules.matches(TaskItem(title = "a", plannedDate = "2026-09-18"), TaskFilter.TODAY, day, ""))
        assertTrue(TaskRules.matches(TaskItem(title = "a", plannedDate = day.toString(), dueDate = "2026-09-20"), TaskFilter.TODAY, day, ""))
    }
    @Test fun deletedAndSearchFilters() {
        assertFalse(TaskRules.matches(TaskItem(title = "a", deletedAt = 1), TaskFilter.ALL, day, ""))
        assertTrue(TaskRules.matches(TaskItem(title = "a", note = "Học Compose"), TaskFilter.ALL, day, "compose"))
        assertFalse(TaskRules.matches(TaskItem(title = "a", goalId = "g"), TaskFilter.INBOX, day, ""))
    }
    @Test fun rejectsImpossibleDates() { assertFalse(TaskRules.validDate("2026-02-30")); assertTrue(TaskRules.validDate("2028-02-29")) }
    @Test fun timerUsesElapsedAndClamps() {
        val state = TimerState(status = "RUNNING", segmentElapsed = 1000, remainingMs = 5000)
        assertEquals(3000L, TimerRules.remaining(state, 3000)); assertEquals(0L, TimerRules.remaining(state, 10000))
        assertEquals(5000L, TimerRules.remaining(state.copy(status = "PAUSED"), 10000))
    }
    @Test fun everyFourthFocusOffersLongBreak() {
        assertEquals(5, TimerRules.breakAfter(3, AppSettings())); assertEquals(15, TimerRules.breakAfter(4, AppSettings()))
    }
    private fun at(day: LocalDate, hour: Int, minute: Int) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    @Test fun midnightSplitsTimeButCountsCompletionOnEndDay() {
        val begin = at(day, 23, 50)
        val session = FocusSession(id = "s", title = "Học", plannedMs = 1500000, startedAt = begin, endedAt = begin + 1500000, status = "COMPLETED", activeMs = 1500000)
        val data = StoreSnapshot(sessions = listOf(session), intervals = listOf(FocusInterval(sessionId = "s", startedAt = begin, durationMs = 1500000)))
        val a = Statistics.report(data, day, Period.DAY, zone); val b = Statistics.report(data, day.plusDays(1), Period.DAY, zone)
        assertEquals(600000L, a.focusMs); assertEquals(900000L, b.focusMs); assertEquals(0, a.completed); assertEquals(1, b.completed)
    }
    @Test fun pauseAcrossMidnightIsExcluded() {
        val session = FocusSession(id = "s", title = "Học", plannedMs = 1500000, startedAt = at(day, 23, 50))
        val data = StoreSnapshot(sessions = listOf(session), intervals = listOf(FocusInterval(sessionId = "s", startedAt = at(day, 23, 50), durationMs = 300000), FocusInterval(sessionId = "s", startedAt = at(day.plusDays(1), 0, 5), durationMs = 600000)))
        assertEquals(300000L, Statistics.report(data, day, Period.DAY, zone).focusMs)
        assertEquals(600000L, Statistics.report(data, day.plusDays(1), Period.DAY, zone).focusMs)
    }
    @Test fun reportFixtureAndDistinctTaskEvents() {
        val start = at(day, 10, 0)
        val sessions = listOf(1500000L, 1500000L, 600000L).mapIndexed { i, amount -> FocusSession(id = "s$i", title = "a", plannedMs = 1500000, startedAt = start + i * 2000000, endedAt = start + i * 2000000 + amount, activeMs = amount, status = if (i < 2) "COMPLETED" else "ABORTED") }
        val data = StoreSnapshot(sessions = sessions, intervals = sessions.map { FocusInterval(sessionId = it.id, startedAt = it.startedAt, durationMs = it.activeMs) }, events = listOf(TaskEvent(taskId = "t", type = "COMPLETED", occurredAt = start), TaskEvent(taskId = "t", type = "COMPLETED", occurredAt = start + 1)))
        val report = Statistics.report(data, day, Period.DAY, zone)
        assertEquals(3600000L, report.focusMs); assertEquals(2, report.completed); assertEquals(1, report.interrupted); assertEquals(1, report.completedTasks)
    }
    @Test fun dstAndWeekBoundaries() {
        val dst = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 3, 8, 1, 50, 0, 0, dst).toInstant().toEpochMilli()
        val session = FocusSession(id = "s", title = "a", plannedMs = 1500000, startedAt = start, endedAt = start + 1500000, status = "COMPLETED")
        val data = StoreSnapshot(sessions = listOf(session), intervals = listOf(FocusInterval(sessionId = "s", startedAt = start, durationMs = 1500000)))
        assertEquals(1500000L, Statistics.report(data, LocalDate.of(2026, 3, 8), Period.DAY, dst).focusMs)
        assertEquals(LocalDate.of(2026, 9, 14), Statistics.bounds(day, Period.WEEK).first)
    }
    @Test fun backupRoundTripAndInvalidReferences() {
        val b = BackupDocument(exportedAt = 1, tasks = listOf(TaskItem(title = "Tiếng Việt", note = "Ghi chú")), goals = emptyList(), events = emptyList(), sessions = emptyList(), intervals = emptyList())
        assertEquals(b, BackupCodec.decode(BackupCodec.encode(b)))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(b.copy(schemaVersion = 9)) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(b.copy(tasks = listOf(b.tasks[0].copy(goalId = "missing")))) }
        assertThrows(Exception::class.java) { BackupCodec.decode("{\"schemaVersion\":1}") }
    }
}
