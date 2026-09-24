package com.trustMePro.podomoroapp.core

import java.time.*

enum class TaskFilter { INBOX, TODAY, UPCOMING, ALL, DONE }
enum class Period { DAY, WEEK, MONTH }

object TaskRules {
    fun matches(task: TaskItem, filter: TaskFilter, today: LocalDate, query: String): Boolean {
        if (task.deletedAt != null) return false
        if (!task.title.contains(query, true) && !task.note.contains(query, true)) return false
        val day = today.toString()
        return when (filter) {
            TaskFilter.INBOX -> task.status != "DONE" && task.plannedDate == null && task.goalId == null
            TaskFilter.TODAY -> if (task.status == "DONE") task.completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == today } == true
                else task.plannedDate == day || task.dueDate?.let { it <= day } == true
            TaskFilter.UPCOMING -> task.status != "DONE" && (task.plannedDate?.let { it > day } == true || task.dueDate?.let { it > day } == true)
            TaskFilter.ALL -> true
            TaskFilter.DONE -> task.status == "DONE"
        }
    }
    fun validDate(value: String?): Boolean = value == null || runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)
}

interface TimeSource { fun wall(): Long; fun elapsed(): Long; fun boot(): Int }

object TimerRules {
    fun remaining(state: TimerState, elapsed: Long): Long = if (state.status == "RUNNING")
        (state.remainingMs - (elapsed - state.segmentElapsed).coerceAtLeast(0)).coerceAtLeast(0) else state.remainingMs
    fun consumed(state: TimerState, elapsed: Long): Long = state.remainingMs - remaining(state, elapsed)
    fun breakAfter(completed: Int, settings: AppSettings): Int = if (completed % 4 == 0) settings.longBreak else settings.shortBreak
    fun shouldResetCycle(lastCompletedAt: Long?, nowWall: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (lastCompletedAt == null) return false
        val lastDate = Instant.ofEpochMilli(lastCompletedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowWall).atZone(zone).toLocalDate()
        if (lastDate < today) return true
        val threeHoursMs = 3 * 60 * 60 * 1000L
        return (nowWall - lastCompletedAt) > threeHoursMs
    }
}

data class Report(val focusMs: Long, val completed: Int, val interrupted: Int, val completedTasks: Int, val daily: List<Pair<LocalDate, Long>>)
data class DailyPomodoroStat(val date: LocalDate, val focusMs: Long, val pomodoroCount: Int)
data class DayHeatmapStat(val pomodoroCount: Int, val focusMs: Long)

object Statistics {
    fun bounds(date: LocalDate, period: Period): Pair<LocalDate, LocalDate> {
        val start = when (period) { Period.DAY -> date; Period.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong()); Period.MONTH -> date.withDayOfMonth(1) }
        return start to when (period) { Period.DAY -> start.plusDays(1); Period.WEEK -> start.plusWeeks(1); Period.MONTH -> start.plusMonths(1) }
    }
    fun report(data: StoreSnapshot, date: LocalDate, period: Period, zone: ZoneId, goalId: String? = null): Report {
        val (first, last) = bounds(date, period)
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = last.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = data.sessions.filter { goalId == null || it.goalId == goalId }
        val ids = sessions.map { it.id }.toSet()
        val intervals = data.intervals.filter { it.sessionId in ids }
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it < last }.map { day ->
            val a = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val b = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            day to intervals.sumOf { (minOf(b, it.startedAt + it.durationMs) - maxOf(a, it.startedAt)).coerceAtLeast(0) }
        }.toList()
        val ended = sessions.filter { it.endedAt?.let { t -> t >= start && t < end } == true }
        val goalTasks = data.tasks.filter { it.goalId == goalId }.map { it.id }.toSet()
        return Report(days.sumOf { it.second }, ended.count { it.status == "COMPLETED" }, ended.count { it.status in listOf("ABORTED", "INTERRUPTED") },
            data.events.filter { it.type == "COMPLETED" && it.occurredAt >= start && it.occurredAt < end && (goalId == null || it.taskId in goalTasks) }.map { it.taskId }.distinct().size, days)
    }

    fun dailyStreak(data: StoreSnapshot, today: LocalDate, zone: ZoneId): Int {
        val activeDates = data.sessions
            .filter { it.status == "COMPLETED" && it.endedAt != null }
            .map { Instant.ofEpochMilli(it.endedAt!!).atZone(zone).toLocalDate() }
            .toSet()

        var current = if (today in activeDates) today else today.minusDays(1)
        if (current !in activeDates) return 0
        var streak = 0
        while (current in activeDates) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    fun weeklyPomodoroStats(data: StoreSnapshot, today: LocalDate, zone: ZoneId): List<DailyPomodoroStat> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val days = (0L..6L).map { monday.plusDays(it) }
        val completedSessions = data.sessions.filter { it.status == "COMPLETED" && it.endedAt != null }
        val intervals = data.intervals

        return days.map { day ->
            val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val daySessions = completedSessions.filter { it.endedAt!! in startMs until endMs }
            val dayFocusMs = intervals.sumOf {
                (minOf(endMs, it.startedAt + it.durationMs) - maxOf(startMs, it.startedAt)).coerceAtLeast(0)
            }
            DailyPomodoroStat(
                date = day,
                focusMs = dayFocusMs,
                pomodoroCount = daySessions.size
            )
        }
    }

    fun monthHeatmapStats(data: StoreSnapshot, yearMonth: YearMonth, zone: ZoneId): Map<LocalDate, DayHeatmapStat> {
        val length = yearMonth.lengthOfMonth()
        val startOfMonth = yearMonth.atDay(1)
        val startMs = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = startOfMonth.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val completedSessions = data.sessions.filter { it.status == "COMPLETED" && it.endedAt != null && it.endedAt in startMs until endMs }
        val intervals = data.intervals

        val result = mutableMapOf<LocalDate, DayHeatmapStat>()
        for (dayNum in 1..length) {
            val day = yearMonth.atDay(dayNum)
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val count = completedSessions.count { it.endedAt!! in dayStart until dayEnd }
            val focusMs = intervals.sumOf {
                (minOf(dayEnd, it.startedAt + it.durationMs) - maxOf(dayStart, it.startedAt)).coerceAtLeast(0)
            }
            result[day] = DayHeatmapStat(count, focusMs)
        }
        return result
    }

    fun monthHeatmap(data: StoreSnapshot, yearMonth: YearMonth, zone: ZoneId): Map<LocalDate, Int> {
        return monthHeatmapStats(data, yearMonth, zone).mapValues { it.value.pomodoroCount }
    }
}
