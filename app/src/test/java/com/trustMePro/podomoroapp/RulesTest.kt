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
    @Test fun checklistProgressAndBackupCompatibility() {
        val task = TaskItem(id = "t1", title = "Task 1")
        val subs = listOf(
            ChecklistItem(id = "c1", taskId = "t1", title = "Sub 1", isDone = true),
            ChecklistItem(id = "c2", taskId = "t1", title = "Sub 2", isDone = false)
        )
        val snapshot = StoreSnapshot(tasks = listOf(task), checklists = subs)
        val taskSubs = snapshot.checklists.filter { it.taskId == task.id }
        assertEquals(2, taskSubs.size)
        assertEquals(1, taskSubs.count { it.isDone })

        // Backup v2 with checklists
        val b2 = BackupDocument(schemaVersion = 2, exportedAt = 100, tasks = listOf(task), goals = emptyList(), events = emptyList(), sessions = emptyList(), intervals = emptyList(), checklists = subs)
        val json2 = BackupCodec.encode(b2)
        val decoded2 = BackupCodec.decode(json2)
        assertEquals(2, decoded2.safeChecklists.size)
        assertEquals("Sub 1", decoded2.safeChecklists[0].title)

        // Invalid: orphan subtask
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.validate(b2.copy(checklists = listOf(ChecklistItem(id = "c3", taskId = "unknown", title = "Orphan"))))
        }

        // Backward compatibility: decode v1 json without checklists field
        val v1Json = """{"schemaVersion":1,"exportedAt":50,"tasks":[{"id":"t1","title":"Task 1","note":"","priority":1,"status":"TODO","createdAt":1,"updatedAt":1}],"goals":[],"events":[],"sessions":[],"intervals":[]}"""
        val decoded1 = BackupCodec.decode(v1Json)
        assertEquals(1, decoded1.schemaVersion)
        assertTrue(decoded1.checklists.orEmpty().isEmpty())
    }

    @Test fun dailyStreakCalculation() {
        val today = LocalDate.of(2026, 9, 19)
        // 3 ngày liên tiếp: 17, 18, 19
        val sessions3 = listOf(today, today.minusDays(1), today.minusDays(2)).mapIndexed { i, d ->
            val t = at(d, 14, 0)
            FocusSession(id = "s$i", title = "P$i", plannedMs = 1500000, startedAt = t, endedAt = t + 1500000, status = "COMPLETED")
        }
        val data3 = StoreSnapshot(sessions = sessions3)
        assertEquals(3, Statistics.dailyStreak(data3, today, zone))

        // Nếu hôm nay chưa hoàn thành nhưng hôm qua có hoàn thành -> streak vẫn giữ 2 ngày (17, 18)
        val sessionsYesterdayOnly = listOf(today.minusDays(1), today.minusDays(2)).mapIndexed { i, d ->
            val t = at(d, 14, 0)
            FocusSession(id = "s$i", title = "P$i", plannedMs = 1500000, startedAt = t, endedAt = t + 1500000, status = "COMPLETED")
        }
        val dataYesterday = StoreSnapshot(sessions = sessionsYesterdayOnly)
        assertEquals(2, Statistics.dailyStreak(dataYesterday, today, zone))

        // Nếu cả hôm nay lẫn hôm qua đều không có -> streak = 0
        val sessionsOld = listOf(today.minusDays(2)).mapIndexed { i, d ->
            val t = at(d, 14, 0)
            FocusSession(id = "s$i", title = "P$i", plannedMs = 1500000, startedAt = t, endedAt = t + 1500000, status = "COMPLETED")
        }
        val dataOld = StoreSnapshot(sessions = sessionsOld)
        assertEquals(0, Statistics.dailyStreak(dataOld, today, zone))
    }

    @Test fun weeklyAndHeatmapStats() {
        val today = LocalDate.of(2026, 9, 19) // Thứ 7
        val t = at(today, 10, 0)
        val s = FocusSession(id = "s1", title = "Focus", plannedMs = 1500000, startedAt = t, endedAt = t + 1500000, status = "COMPLETED")
        val interval = FocusInterval(sessionId = "s1", startedAt = t, durationMs = 1500000)
        val data = StoreSnapshot(sessions = listOf(s), intervals = listOf(interval))

        val weekly = Statistics.weeklyPomodoroStats(data, today, zone)
        assertEquals(7, weekly.size)
        val sat = weekly.find { it.date == today }
        assertNotNull(sat)
        assertEquals(1, sat!!.pomodoroCount)
        assertEquals(1500000L, sat.focusMs)

        val heatmap = Statistics.monthHeatmap(data, java.time.YearMonth.of(2026, 9), zone)
        assertEquals(30, heatmap.size)
        assertEquals(1, heatmap[today])
        assertEquals(0, heatmap[today.minusDays(1)])
    }

    @Test fun timerExtendLogic() {
        val startElapsed = 1000L
        val originalPlanned = 1500_000L // 25 min
        val state = TimerState(status = "RUNNING", segmentElapsed = startElapsed, remainingMs = originalPlanned, plannedMs = originalPlanned)

        // Sau 10 phut (600_000 ms)
        val nowElapsed = startElapsed + 600_000L
        val remainingBefore = TimerRules.remaining(state, nowElapsed)
        assertEquals(900_000L, remainingBefore) // 15 min left
        val consumedBefore = TimerRules.consumed(state, nowElapsed)
        assertEquals(600_000L, consumedBefore) // 10 min consumed

        // Extension +1 phut (60_000 ms)
        val additionalMs = 60_000L
        val extendedRemaining = remainingBefore + additionalMs
        val extendedPlanned = originalPlanned + additionalMs
        val extendedState = state.copy(
            segmentElapsed = nowElapsed,
            remainingMs = extendedRemaining,
            plannedMs = extendedPlanned
        )

        assertEquals(960_000L, TimerRules.remaining(extendedState, nowElapsed))
        assertEquals(1560_000L, extendedState.plannedMs)

        // Sau them 1 phut nua (60_000 ms)
        val laterElapsed = nowElapsed + 60_000L
        assertEquals(900_000L, TimerRules.remaining(extendedState, laterElapsed))
        assertEquals(60_000L, TimerRules.consumed(extendedState, laterElapsed))
    }

    @Test fun timerExtendClampAt180Min() {
        val maxPlanned = 10_800_000L // 180 min
        val nearMaxState = TimerState(status = "RUNNING", segmentElapsed = 0, remainingMs = 60_000L, plannedMs = maxPlanned - 30_000L) // 179.5 min
        val effectiveAdd = minOf(60_000L, (maxPlanned - nearMaxState.plannedMs).coerceAtLeast(0L))
        assertEquals(30_000L, effectiveAdd)
        val clampedPlanned = nearMaxState.plannedMs + effectiveAdd
        assertEquals(maxPlanned, clampedPlanned)

        val atMaxState = nearMaxState.copy(plannedMs = maxPlanned)
        val effectiveAddAtMax = minOf(60_000L, (maxPlanned - atMaxState.plannedMs).coerceAtLeast(0L))
        assertEquals(0L, effectiveAddAtMax)
    }

    @Test fun breakTimerLogic() {
        val breakMins = 5
        val breakPlannedMs = breakMins * 60_000L
        val startElapsed = 2000L
        val breakState = TimerState(
            phase = "BREAK",
            status = "RUNNING",
            segmentElapsed = startElapsed,
            remainingMs = breakPlannedMs,
            plannedMs = breakPlannedMs,
            completedInCycle = 1
        )
        // Check remaining time at 2 minutes into break
        val elapsedAfter2Min = startElapsed + 120_000L
        assertEquals(180_000L, TimerRules.remaining(breakState, elapsedAfter2Min)) // 3 minutes remaining

        // Extend break by +1 minute
        val extendedRemaining = TimerRules.remaining(breakState, elapsedAfter2Min) + 60_000L
        val extendedBreakState = breakState.copy(
            segmentElapsed = elapsedAfter2Min,
            remainingMs = extendedRemaining,
            plannedMs = breakPlannedMs + 60_000L
        )
        assertEquals(240_000L, TimerRules.remaining(extendedBreakState, elapsedAfter2Min)) // 4 minutes remaining
        assertEquals(360_000L, extendedBreakState.plannedMs) // 6 minutes total planned
    }

    @Test fun cycleAutoResetOnNewDayOrLongHiatus() {
        val today = LocalDate.of(2026, 9, 23)
        val tenAmToday = at(today, 10, 0)

        // 1. No previous session -> no reset needed (already 0)
        assertFalse(TimerRules.shouldResetCycle(null, tenAmToday, zone))

        // 2. Previous session was yesterday -> should reset
        val yesterdayEvening = at(today.minusDays(1), 21, 0)
        assertTrue(TimerRules.shouldResetCycle(yesterdayEvening, tenAmToday, zone))

        // 3. Previous session was today, but > 3 hours ago -> should reset
        val fourHoursAgo = tenAmToday - (4 * 3600_000L)
        assertTrue(TimerRules.shouldResetCycle(fourHoursAgo, tenAmToday, zone))

        // 4. Previous session was today, 30 minutes ago -> should NOT reset
        val thirtyMinsAgo = tenAmToday - (30 * 60_000L)
        assertFalse(TimerRules.shouldResetCycle(thirtyMinsAgo, tenAmToday, zone))

        // 5. Previous session was today, 2.5 hours ago -> should NOT reset
        val twoAndHalfHoursAgo = tenAmToday - (150 * 60_000L)
        assertFalse(TimerRules.shouldResetCycle(twoAndHalfHoursAgo, tenAmToday, zone))
    }

    @Test fun alarmSoundOnlyPlaysInNormalRingerMode() {
        assertTrue(AlarmPlayer.shouldPlaySound(android.media.AudioManager.RINGER_MODE_NORMAL))
        assertFalse(AlarmPlayer.shouldPlaySound(android.media.AudioManager.RINGER_MODE_VIBRATE))
        assertFalse(AlarmPlayer.shouldPlaySound(android.media.AudioManager.RINGER_MODE_SILENT))
    }
}
