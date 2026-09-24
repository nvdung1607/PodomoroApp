package com.trustMePro.podomoroapp.core

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "tasks", indices = [Index("goalId"), Index("dueDate"), Index("plannedDate")])
data class TaskItem(
    @PrimaryKey val id: String = newId(), val title: String = "", val note: String = "",
    val goalId: String? = null, val plannedDate: String? = null, val dueDate: String? = null,
    val priority: Int = 1, val status: String = "TODO", val estimate: Int? = null,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val completedAt: Long? = null, val deletedAt: Long? = null
)

@Entity(tableName = "goals")
data class GoalItem(
    @PrimaryKey val id: String = newId(), val title: String = "", val note: String = "",
    val dueDate: String? = null, val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(), val deletedAt: Long? = null
)

@Entity(tableName = "task_events", indices = [Index("taskId"), Index("occurredAt")])
data class TaskEvent(@PrimaryKey val id: String = newId(), val taskId: String, val type: String, val occurredAt: Long)

@Entity(tableName = "sessions", indices = [Index("endedAt"), Index("goalId")])
data class FocusSession(
    @PrimaryKey val id: String = newId(), val taskId: String? = null, val goalId: String? = null,
    val title: String, val goalTitle: String? = null, val plannedMs: Long,
    val startedAt: Long, val endedAt: Long? = null, val activeMs: Long = 0,
    val status: String = "RUNNING"
)

@Entity(tableName = "intervals", indices = [Index("sessionId")])
data class FocusInterval(@PrimaryKey val id: String = newId(), val sessionId: String, val startedAt: Long, val durationMs: Long)

@Entity(tableName = "checklists", indices = [Index("taskId")])
data class ChecklistItem(
    @PrimaryKey val id: String = newId(), val taskId: String, val title: String = "",
    val isDone: Boolean = false, val order: Int = 0, val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "timer")
data class TimerState(
    @PrimaryKey val id: Int = 1, val generation: String = newId(),
    val phase: String = "FOCUS", val status: String = "IDLE", val sessionId: String? = null,
    val plannedMs: Long = 1_500_000, val remainingMs: Long = 1_500_000,
    val segmentElapsed: Long = 0, val segmentWall: Long = 0, val boot: Int = 0,
    val completedInCycle: Int = 0, val breakMinutes: Int = 5
)

data class AppSettings(val focus: Int = 25, val shortBreak: Int = 5, val longBreak: Int = 15, val dailyTarget: Int = 8, val useDnd: Boolean = true, val theme: String = "SYSTEM", val skipAuthPrompt: Boolean = false, val lastSyncedUid: String? = null)
data class StoreSnapshot(
    val tasks: List<TaskItem> = emptyList(), val goals: List<GoalItem> = emptyList(),
    val events: List<TaskEvent> = emptyList(), val sessions: List<FocusSession> = emptyList(),
    val intervals: List<FocusInterval> = emptyList(), val timer: TimerState = TimerState(),
    val checklists: List<ChecklistItem> = emptyList()
)
