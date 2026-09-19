package com.trustMePro.podomoroapp.core

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Repository(val database: AppDatabase, val settings: SettingsProvider, val clock: TimeSource, private val scheduler: EndScheduler, private val dnd: (Boolean) -> Unit) {
    val dao = database.dao()
    internal val mutex = Mutex()
    val snapshot = combine(
        combine(dao.observeTasks(), dao.observeGoals(), dao.observeEvents()) { t, g, e -> Triple(t, g, e) },
        combine(dao.observeSessions(), dao.observeIntervals(), dao.observeTimer()) { s, i, t -> Triple(s, i, t) }
    ) { a, b -> StoreSnapshot(a.first, a.second, a.third, b.first, b.second, b.third ?: TimerState()) }

    suspend fun saveTask(value: TaskItem) = mutex.withLock {
        require(value.title.isNotBlank())
        require(TaskRules.validDate(value.plannedDate) && TaskRules.validDate(value.dueDate))
        require(value.priority in 0..2 && (value.estimate == null || value.estimate in 1..10000))
        database.withTransaction {
            val old = dao.task(value.id)
            require(value.goalId == null || dao.goal(value.goalId)?.let { it.deletedAt == null } == true)
            // Editing an old form must not overwrite a completion made by another action.
            dao.save(value.copy(title = value.title.trim(), updatedAt = clock.wall(), status = old?.status ?: "TODO", completedAt = old?.completedAt))
        }
    }
    suspend fun setTaskDone(id: String, done: Boolean) = mutex.withLock {
        database.withTransaction {
            val task = dao.task(id) ?: return@withTransaction
            if (task.deletedAt != null || (task.status == "DONE") == done) return@withTransaction
            val now = clock.wall()
            dao.save(task.copy(status = if (done) "DONE" else "TODO", completedAt = if (done) now else null, updatedAt = now))
            dao.save(TaskEvent(taskId = id, type = if (done) "COMPLETED" else "REOPENED", occurredAt = now))
        }
    }
    suspend fun deleteTask(id: String, restore: Boolean = false) = mutex.withLock {
        dao.task(id)?.let { dao.save(it.copy(deletedAt = if (restore) null else clock.wall())) }
    }
    suspend fun saveGoal(value: GoalItem) = mutex.withLock {
        require(value.title.isNotBlank() && TaskRules.validDate(value.dueDate))
        require(value.status in listOf("ACTIVE", "COMPLETED", "ARCHIVED"))
        dao.save(value.copy(title = value.title.trim()))
    }
    suspend fun deleteGoal(id: String): List<String> = mutex.withLock {
        database.withTransaction {
            val linked = dao.tasks().filter { it.goalId == id }.map { it.id }
            dao.goal(id)?.let { dao.save(it.copy(deletedAt = clock.wall())) }
            dao.detachGoal(id)
            linked
        }
    }
    suspend fun restoreGoal(id: String, linked: List<String>) = mutex.withLock {
        database.withTransaction {
            dao.goal(id)?.let { dao.save(it.copy(deletedAt = null)) }
            linked.forEach { taskId -> dao.task(taskId)?.takeIf { it.goalId == null }?.let { dao.save(it.copy(goalId = id)) } }
        }
    }
    private suspend fun sync(state: TimerState, schedule: Boolean = true) {
        if (schedule) scheduler.schedule(state)
        dnd(state.status == "RUNNING" && state.phase == "FOCUS" && settings.settings.first().useDnd)
    }
    suspend fun startFocus(taskId: String?, goalId: String?) = mutex.withLock {
        val config = settings.settings.first()
        val state = database.withTransaction {
            val old = dao.timer() ?: TimerState()
            check(old.status !in listOf("RUNNING", "PAUSED"))
            require((taskId == null) != (goalId == null))
            val task = taskId?.let { dao.task(it) }
            require(taskId == null || task != null && task.deletedAt == null && task.status != "DONE")
            val goal = (task?.goalId ?: goalId)?.let { dao.goal(it) }
            require(goalId == null || goal != null && goal.status == "ACTIVE" && goal.deletedAt == null)
            val session = FocusSession(taskId = taskId, goalId = goal?.id, title = task?.title ?: requireNotNull(goal).title,
                goalTitle = goal?.title, plannedMs = config.focus * 60_000L, startedAt = clock.wall())
            dao.save(session)
            if (task?.status == "TODO") dao.save(task.copy(status = "IN_PROGRESS", updatedAt = clock.wall()))
            TimerState(generation = newId(), status = "RUNNING", sessionId = session.id, plannedMs = session.plannedMs,
                remainingMs = session.plannedMs, segmentElapsed = clock.elapsed(), segmentWall = clock.wall(), boot = clock.boot(),
                completedInCycle = old.completedInCycle % 4).also { dao.save(it) }
        }
        sync(state)
    }
    suspend fun startBreak() = mutex.withLock {
        val old = dao.timer() ?: return@withLock
        check(old.status == "AWAITING_BREAK")
        val state = old.copy(generation = newId(), phase = "BREAK", status = "RUNNING", sessionId = null,
            plannedMs = old.breakMinutes * 60_000L, remainingMs = old.breakMinutes * 60_000L,
            segmentElapsed = clock.elapsed(), segmentWall = clock.wall(), boot = clock.boot())
        dao.save(state); sync(state)
    }
    private suspend fun closeSegment(state: TimerState, elapsed: Long): Long {
        val amount = TimerRules.consumed(state, elapsed)
        if (state.phase == "FOCUS" && state.sessionId != null && amount > 0) {
            dao.save(FocusInterval(sessionId = state.sessionId, startedAt = state.segmentWall, durationMs = amount))
            dao.session(state.sessionId)?.let { dao.save(it.copy(activeMs = it.activeMs + amount)) }
        }
        return amount
    }
    private suspend fun finish(state: TimerState, completed: Boolean, interrupted: Boolean = false): TimerState {
        val amount = if (!interrupted && state.status == "RUNNING") closeSegment(state, clock.elapsed()) else 0L
        val end = if (state.status == "RUNNING" && !interrupted) state.segmentWall + amount else clock.wall()
        if (state.sessionId != null) dao.session(state.sessionId)?.let { session ->
            dao.save(session.copy(endedAt = maxOf(end, session.startedAt), status = if (interrupted) "INTERRUPTED" else if (completed) "COMPLETED" else "ABORTED"))
        }
        val count = state.completedInCycle + if (completed && state.phase == "FOCUS") 1 else 0
        val config = settings.settings.first()
        return state.copy(status = if (completed && state.phase == "FOCUS") "AWAITING_BREAK" else "IDLE",
            generation = newId(), remainingMs = 0, completedInCycle = count,
            breakMinutes = TimerRules.breakAfter(count, config)).also { dao.save(it) }
    }
    suspend fun pause() = mutex.withLock {
        var completed = false
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction null
            if (old.status != "RUNNING") return@withTransaction null
            if (old.boot != clock.boot()) return@withTransaction finish(old, false, true)
            val nowElapsed = clock.elapsed()
            val left = TimerRules.remaining(old, nowElapsed)
            if (left == 0L) { completed = true; finish(old, true) }
            else { closeSegment(old, nowElapsed); old.copy(status = "PAUSED", remainingMs = left, generation = newId()).also { dao.save(it) } }
        }
        state?.let { sync(it); if (completed) scheduler.completed(it.phase == "FOCUS") }
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
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction null
            if (old.status !in listOf("RUNNING", "PAUSED", "AWAITING_BREAK")) return@withTransaction null
            if (old.status == "AWAITING_BREAK") old.copy(status = "IDLE", generation = newId()).also { dao.save(it) }
            else {
                val interrupted = old.boot != clock.boot()
                completed = !interrupted && old.status == "RUNNING" && TimerRules.remaining(old, clock.elapsed()) == 0L
                finish(old, completed, interrupted)
            }
        }
        state?.let { sync(it); if (completed) scheduler.completed(it.phase == "FOCUS") }
    }
    suspend fun reconcile(generation: String? = null, reschedule: Boolean = false) = mutex.withLock {
        var completed = false
        var changed = false
        val state = database.withTransaction {
            val old = dao.timer() ?: return@withTransaction TimerState()
            if (generation != null && old.generation != generation) return@withTransaction old
            if (old.status in listOf("RUNNING", "PAUSED") && old.boot != clock.boot()) {
                changed = true; finish(old, false, true)
            } else if (old.status == "RUNNING" && TimerRules.remaining(old, clock.elapsed()) == 0L) {
                changed = true; completed = true; finish(old, true)
            } else old
        }
        if (changed || reschedule) sync(state)
        if (completed) scheduler.completed(state.phase == "FOCUS")
    }
}
