package com.trustMePro.podomoroapp.core

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Repository(
    val database: AppDatabase,
    val settings: SettingsProvider,
    val clock: TimeSource,
    private val scheduler: EndScheduler,
    private val dnd: (Boolean) -> Unit,
    var syncEngine: SyncEngine? = null
) {
    val dao = database.dao()
    internal val mutex = Mutex()
    val snapshot = combine(
        combine(dao.observeTasks(), dao.observeGoals(), dao.observeEvents()) { t, g, e -> Triple(t, g, e) },
        combine(dao.observeSessions(), dao.observeIntervals(), dao.observeTimer()) { s, i, t -> Triple(s, i, t) },
        dao.observeChecklists()
    ) { a, b, c -> StoreSnapshot(a.first, a.second, a.third, b.first, b.second, b.third ?: TimerState(), c) }

    suspend fun saveChecklist(value: ChecklistItem) = mutex.withLock {
        require(value.title.isNotBlank())
        val item = value.copy(title = value.title.trim())
        database.withTransaction {
            require(dao.task(value.taskId)?.let { it.deletedAt == null } == true)
            dao.save(item)
        }
        syncEngine?.pushChecklist(item)
    }
    suspend fun setChecklistDone(id: String, done: Boolean) = mutex.withLock {
        val updated = dao.checklist(id)?.copy(isDone = done)
        if (updated != null) {
            dao.save(updated)
            syncEngine?.pushChecklist(updated)
        }
    }
    suspend fun deleteChecklist(id: String) = mutex.withLock {
        dao.deleteChecklist(id)
        syncEngine?.deleteRemoteChecklist(id)
    }

    suspend fun saveTask(value: TaskItem) = mutex.withLock {
        require(value.title.isNotBlank())
        require(TaskRules.validDate(value.plannedDate) && TaskRules.validDate(value.dueDate))
        require(value.priority in 0..2 && (value.estimate == null || value.estimate in 1..10000))
        val saved = database.withTransaction {
            val old = dao.task(value.id)
            require(value.goalId == null || dao.goal(value.goalId)?.let { it.deletedAt == null } == true)
            // Editing an old form must not overwrite a completion made by another action.
            val item = value.copy(title = value.title.trim(), updatedAt = clock.wall(), status = old?.status ?: "TODO", completedAt = old?.completedAt)
            dao.save(item)
            item
        }
        syncEngine?.pushTask(saved)
    }
    suspend fun setTaskDone(id: String, done: Boolean) = mutex.withLock {
        val pair = database.withTransaction {
            val task = dao.task(id) ?: return@withTransaction null
            if (task.deletedAt != null || (task.status == "DONE") == done) return@withTransaction null
            val now = clock.wall()
            val updated = task.copy(status = if (done) "DONE" else "TODO", completedAt = if (done) now else null, updatedAt = now)
            val event = TaskEvent(taskId = id, type = if (done) "COMPLETED" else "REOPENED", occurredAt = now)
            dao.save(updated)
            dao.save(event)
            Pair(updated, event)
        }
        if (pair != null) {
            syncEngine?.pushTask(pair.first)
            syncEngine?.pushEvent(pair.second)
        }
    }
    suspend fun deleteTask(id: String, restore: Boolean = false) = mutex.withLock {
        val updated = dao.task(id)?.copy(deletedAt = if (restore) null else clock.wall(), updatedAt = clock.wall())
        if (updated != null) {
            dao.save(updated)
            syncEngine?.pushTask(updated)
        }
    }
    suspend fun saveGoal(value: GoalItem) = mutex.withLock {
        require(value.title.isNotBlank() && TaskRules.validDate(value.dueDate))
        require(value.status in listOf("ACTIVE", "COMPLETED", "ARCHIVED"))
        val item = value.copy(title = value.title.trim())
        dao.save(item)
        syncEngine?.pushGoal(item)
    }
    suspend fun deleteGoal(id: String): List<String> = mutex.withLock {
        val (linked, updatedGoal) = database.withTransaction {
            val linkedTasks = dao.tasks().filter { it.goalId == id }.map { it.id }
            val g = dao.goal(id)?.copy(deletedAt = clock.wall())
            if (g != null) dao.save(g)
            dao.detachGoal(id)
            Pair(linkedTasks, g)
        }
        if (updatedGoal != null) syncEngine?.pushGoal(updatedGoal)
        linked
    }
    suspend fun restoreGoal(id: String, linked: List<String>) = mutex.withLock {
        val restoredGoal = database.withTransaction {
            val g = dao.goal(id)?.copy(deletedAt = null)
            if (g != null) dao.save(g)
            linked.forEach { taskId -> dao.task(taskId)?.takeIf { it.goalId == null }?.let { dao.save(it.copy(goalId = id)) } }
            g
        }
        if (restoredGoal != null) syncEngine?.pushGoal(restoredGoal)
    }
    private suspend fun sync(state: TimerState, schedule: Boolean = true) {
        if (schedule) {
            val title = if (state.phase == "BREAK") null else {
                state.sessionId?.let { dao.session(it)?.title }
            }
            scheduler.schedule(state, title)
        }
        dnd(state.status == "RUNNING" && state.phase == "FOCUS" && settings.settings.first().useDnd)
    }
    suspend fun startFocus(taskId: String?, goalId: String?, customMinutes: Int? = null) = mutex.withLock {
        val config = settings.settings.first()
        val minutes = (customMinutes ?: config.focus).coerceIn(1, 180)
        val state = database.withTransaction {
            val old = dao.timer() ?: TimerState()
            check(old.status !in listOf("RUNNING", "PAUSED"))
            val task = taskId?.let { dao.task(it) }
            require(taskId == null || (task != null && task.deletedAt == null && task.status != "DONE"))
            val goal = (task?.goalId ?: goalId)?.let { dao.goal(it) }
            require(goalId == null || (goal != null && goal.status == "ACTIVE" && goal.deletedAt == null))
            val title = task?.title ?: goal?.title ?: "Tập trung tự do"
            val session = FocusSession(taskId = taskId, goalId = goal?.id, title = title,
                goalTitle = goal?.title, plannedMs = minutes * 60_000L, startedAt = clock.wall())
            dao.save(session)
            if (task?.status == "TODO") dao.save(task.copy(status = "IN_PROGRESS", updatedAt = clock.wall()))
            val lastSession = dao.lastCompletedSession()
            val resetNeeded = TimerRules.shouldResetCycle(lastSession?.endedAt ?: lastSession?.startedAt, clock.wall())
            val cycleCount = if (resetNeeded) 0 else old.completedInCycle % 4
            TimerState(generation = newId(), status = "RUNNING", sessionId = session.id, plannedMs = session.plannedMs,
                remainingMs = session.plannedMs, segmentElapsed = clock.elapsed(), segmentWall = clock.wall(), boot = clock.boot(),
                completedInCycle = cycleCount).also { dao.save(it) }
        }
        sync(state)
    }
    suspend fun resetCycle() = mutex.withLock {
        database.withTransaction {
            val old = dao.timer() ?: TimerState()
            dao.save(old.copy(completedInCycle = 0))
        }
    }
    suspend fun startBreak(customMinutes: Int? = null) = mutex.withLock {
        val old = dao.timer() ?: return@withLock
        check(old.status in listOf("AWAITING_BREAK", "IDLE"))
        val config = settings.settings.first()
        val defaultMins = if (old.status == "AWAITING_BREAK") old.breakMinutes else config.shortBreak
        val breakMins = (customMinutes ?: defaultMins).coerceIn(1, 180)
        val state = old.copy(generation = newId(), phase = "BREAK", status = "RUNNING", sessionId = null,
            plannedMs = breakMins * 60_000L, remainingMs = breakMins * 60_000L,
            segmentElapsed = clock.elapsed(), segmentWall = clock.wall(), boot = clock.boot())
        dao.save(state); sync(state)
    }
    suspend fun extendCurrentTimer(additionalMs: Long = 60_000L) = mutex.withLock {
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction null
            if (old.status !in listOf("RUNNING", "PAUSED")) return@withTransaction null
            if (old.boot != clock.boot()) return@withTransaction finish(old, false, true)

            val nowElapsed = clock.elapsed()
            val left = if (old.status == "RUNNING") TimerRules.remaining(old, nowElapsed) else old.remainingMs
            if (left <= 0L && old.status == "RUNNING") return@withTransaction finish(old, true)

            val maxPlannedMs = 180 * 60_000L // 10_800_000L (max 180 minutes per PRD)
            val effectiveAdditional = minOf(additionalMs, (maxPlannedMs - old.plannedMs).coerceAtLeast(0L))
            if (effectiveAdditional <= 0L) return@withTransaction old

            val newRemaining = left + effectiveAdditional
            val newPlanned = old.plannedMs + effectiveAdditional

            if (old.status == "RUNNING") {
                closeSegment(old, nowElapsed)
            }
            if (old.sessionId != null) {
                dao.session(old.sessionId)?.let { session ->
                    dao.save(session.copy(plannedMs = session.plannedMs + effectiveAdditional))
                }
            }

            val updated = old.copy(
                generation = newId(),
                plannedMs = newPlanned,
                remainingMs = newRemaining,
                segmentElapsed = if (old.status == "RUNNING") nowElapsed else old.segmentElapsed,
                segmentWall = if (old.status == "RUNNING") clock.wall() else old.segmentWall
            )
            dao.save(updated)
            updated
        }
        state?.let { sync(it) }
    }
    private suspend fun closeSegment(state: TimerState, elapsed: Long): Long {
        val amount = TimerRules.consumed(state, elapsed)
        if (state.phase == "FOCUS" && state.sessionId != null && amount > 0) {
            val interval = FocusInterval(sessionId = state.sessionId, startedAt = state.segmentWall, durationMs = amount)
            dao.save(interval)
            syncEngine?.pushInterval(interval)
            dao.session(state.sessionId)?.let {
                val updated = it.copy(activeMs = it.activeMs + amount)
                dao.save(updated)
                syncEngine?.pushSession(updated)
            }
        }
        return amount
    }
    private suspend fun finish(state: TimerState, completed: Boolean, interrupted: Boolean = false): TimerState {
        val amount = if (!interrupted && state.status == "RUNNING") closeSegment(state, clock.elapsed()) else 0L
        val end = if (state.status == "RUNNING" && !interrupted) state.segmentWall + amount else clock.wall()
        if (state.sessionId != null) dao.session(state.sessionId)?.let { session ->
            val updated = session.copy(endedAt = maxOf(end, session.startedAt), status = if (interrupted) "INTERRUPTED" else if (completed) "COMPLETED" else "ABORTED")
            dao.save(updated)
            syncEngine?.pushSession(updated)
        }
        val count = state.completedInCycle + if (completed && state.phase == "FOCUS") 1 else 0
        val config = settings.settings.first()
        return state.copy(status = if (completed && state.phase == "FOCUS") "AWAITING_BREAK" else "IDLE",
            phase = "FOCUS",
            generation = newId(), remainingMs = 0, completedInCycle = count,
            breakMinutes = TimerRules.breakAfter(count, config)).also { dao.save(it) }
    }
    suspend fun pause() = mutex.withLock {
        var completed = false
        var wasFocus = false
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction null
            if (old.status != "RUNNING") return@withTransaction null
            if (old.boot != clock.boot()) return@withTransaction finish(old, false, true)
            val nowElapsed = clock.elapsed()
            val left = TimerRules.remaining(old, nowElapsed)
            if (left == 0L) {
                completed = true
                wasFocus = old.phase == "FOCUS"
                finish(old, true)
            } else {
                closeSegment(old, nowElapsed)
                old.copy(status = "PAUSED", remainingMs = left, generation = newId()).also { dao.save(it) }
            }
        }
        state?.let { sync(it); if (completed) scheduler.completed(wasFocus) }
    }
    suspend fun resume() = mutex.withLock {
        val old = dao.timer() ?: return@withLock
        if (old.status != "PAUSED") return@withLock
        val state = if (old.boot != clock.boot()) database.withTransaction { finish(old, false, true) }
        else old.copy(status = "RUNNING", generation = newId(), segmentElapsed = clock.elapsed(), segmentWall = clock.wall()).also { dao.save(it) }
        sync(state)
    }
    suspend fun stop() = mutex.withLock {
        var completed = false
        var wasFocus = false
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction null
            if (old.status !in listOf("RUNNING", "PAUSED", "AWAITING_BREAK")) return@withTransaction null
            if (old.status == "AWAITING_BREAK") old.copy(status = "IDLE", phase = "FOCUS", generation = newId()).also { dao.save(it) }
            else {
                val interrupted = old.boot != clock.boot()
                completed = !interrupted && old.status == "RUNNING" && TimerRules.remaining(old, clock.elapsed()) == 0L
                wasFocus = old.phase == "FOCUS"
                finish(old, completed, interrupted)
            }
        }
        state?.let { sync(it); if (completed) scheduler.completed(wasFocus) }
    }
    suspend fun reconcile(generation: String? = null, reschedule: Boolean = false) = mutex.withLock {
        var completed = false
        var wasFocus = false
        var changed = false
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction TimerState()
            if (generation != null && old.generation != generation) return@withTransaction old
            if (old.status in listOf("RUNNING", "PAUSED") && old.boot != clock.boot()) {
                changed = true; finish(old, false, true)
            } else if (old.status == "RUNNING" && TimerRules.remaining(old, clock.elapsed()) == 0L) {
                changed = true
                completed = true
                wasFocus = old.phase == "FOCUS"
                finish(old, true)
            } else old
        }
        if (changed || reschedule) sync(state)
        if (completed) scheduler.completed(wasFocus)
    }
}
