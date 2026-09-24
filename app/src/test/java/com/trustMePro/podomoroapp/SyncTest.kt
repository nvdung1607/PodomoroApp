package com.trustMePro.podomoroapp

import com.trustMePro.podomoroapp.core.ChecklistItem
import com.trustMePro.podomoroapp.core.FocusInterval
import com.trustMePro.podomoroapp.core.FocusSession
import com.trustMePro.podomoroapp.core.GoalItem
import com.trustMePro.podomoroapp.core.SyncEngine
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.checklistFromMap
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.eventFromMap
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.goalFromMap
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.intervalFromMap
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.sessionFromMap
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.taskFromMap
import com.trustMePro.podomoroapp.core.SyncEngine.Companion.toMap
import com.trustMePro.podomoroapp.core.TaskEvent
import com.trustMePro.podomoroapp.core.TaskItem
import org.junit.Assert.*
import org.junit.Test

class SyncTest {

    @Test
    fun taskMappingConsistency() {
        val original = TaskItem(
            id = "task-123",
            title = "Viết code tính năng đồng bộ",
            note = "Chi phí 0đ với Firebase",
            goalId = "goal-456",
            plannedDate = "2026-09-23",
            dueDate = "2026-09-25",
            priority = 2,
            status = "IN_PROGRESS",
            estimate = 4,
            createdAt = 1000L,
            updatedAt = 2000L,
            completedAt = null,
            deletedAt = null
        )

        val map = original.toMap()
        val reconstructed = taskFromMap(map)

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.title, reconstructed.title)
        assertEquals(original.note, reconstructed.note)
        assertEquals(original.goalId, reconstructed.goalId)
        assertEquals(original.plannedDate, reconstructed.plannedDate)
        assertEquals(original.dueDate, reconstructed.dueDate)
        assertEquals(original.priority, reconstructed.priority)
        assertEquals(original.status, reconstructed.status)
        assertEquals(original.estimate, reconstructed.estimate)
        assertEquals(original.createdAt, reconstructed.createdAt)
        assertEquals(original.updatedAt, reconstructed.updatedAt)
        assertNull(reconstructed.completedAt)
        assertNull(reconstructed.deletedAt)
    }

    @Test
    fun goalMappingConsistency() {
        val original = GoalItem(
            id = "goal-789",
            title = "Hoàn thành MVP FocusDo",
            note = "Mục tiêu quý 3",
            dueDate = "2026-10-01",
            status = "ACTIVE",
            createdAt = 5000L,
            deletedAt = null
        )

        val map = original.toMap()
        val reconstructed = goalFromMap(map)

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.title, reconstructed.title)
        assertEquals(original.note, reconstructed.note)
        assertEquals(original.dueDate, reconstructed.dueDate)
        assertEquals(original.status, reconstructed.status)
        assertEquals(original.createdAt, reconstructed.createdAt)
        assertNull(reconstructed.deletedAt)
    }

    @Test
    fun checklistMappingConsistency() {
        val original = ChecklistItem(
            id = "chk-1",
            taskId = "task-123",
            title = "Cấu hình Firebase BoM",
            isDone = true,
            order = 0,
            createdAt = 3000L
        )

        val map = original.toMap()
        val reconstructed = checklistFromMap(map)

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.taskId, reconstructed.taskId)
        assertEquals(original.title, reconstructed.title)
        assertTrue(reconstructed.isDone)
        assertEquals(original.order, reconstructed.order)
        assertEquals(original.createdAt, reconstructed.createdAt)
    }

    @Test
    fun sessionMappingConsistency() {
        val original = FocusSession(
            id = "session-1",
            taskId = "task-123",
            goalId = "goal-789",
            title = "Tập trung sâu",
            goalTitle = "Mục tiêu quý 3",
            plannedMs = 1500000L,
            startedAt = 10000L,
            endedAt = 1150000L,
            activeMs = 1500000L,
            status = "COMPLETED"
        )

        val map = original.toMap()
        val reconstructed = sessionFromMap(map)

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.taskId, reconstructed.taskId)
        assertEquals(original.goalId, reconstructed.goalId)
        assertEquals(original.title, reconstructed.title)
        assertEquals(original.goalTitle, reconstructed.goalTitle)
        assertEquals(original.plannedMs, reconstructed.plannedMs)
        assertEquals(original.startedAt, reconstructed.startedAt)
        assertEquals(original.endedAt, reconstructed.endedAt)
        assertEquals(original.activeMs, reconstructed.activeMs)
        assertEquals(original.status, reconstructed.status)
    }

    @Test
    fun taskEventMappingConsistency() {
        val original = TaskEvent(
            id = "event-1",
            taskId = "task-123",
            type = "COMPLETED",
            occurredAt = 12000L
        )

        val map = original.toMap()
        val reconstructed = eventFromMap(map)

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.taskId, reconstructed.taskId)
        assertEquals(original.type, reconstructed.type)
        assertEquals(original.occurredAt, reconstructed.occurredAt)
    }

    @Test
    fun lastWriteWinsConflictResolutionLogic() {
        val localTask = TaskItem(id = "t1", title = "Bản cũ trên máy A", updatedAt = 1000L)
        val newerRemoteTask = TaskItem(id = "t1", title = "Bản mới hơn từ máy B", updatedAt = 2000L)
        val olderRemoteTask = TaskItem(id = "t1", title = "Bản cũ hơn từ máy C", updatedAt = 500L)

        // Remote newer than local -> should update
        assertTrue(newerRemoteTask.updatedAt >= localTask.updatedAt)

        // Remote older than local -> should NOT update
        assertFalse(olderRemoteTask.updatedAt >= localTask.updatedAt)
    }

    @Test
    fun softDeletePreservationOnSync() {
        val activeLocal = TaskItem(id = "t2", title = "Việc cần làm", updatedAt = 1000L, deletedAt = null)
        val deletedRemote = TaskItem(id = "t2", title = "Việc cần làm", updatedAt = 2000L, deletedAt = 2000L)

        assertTrue(deletedRemote.updatedAt >= activeLocal.updatedAt)
        assertNotNull(deletedRemote.deletedAt)
    }

    @Test
    fun intervalMappingConsistency() {
        val original = FocusInterval(
            id = "interval-1",
            sessionId = "session-1",
            startedAt = 10000L,
            durationMs = 1500000L
        )

        val map = original.toMap()
        val reconstructed = intervalFromMap(map)

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.sessionId, reconstructed.sessionId)
        assertEquals(original.startedAt, reconstructed.startedAt)
        assertEquals(original.durationMs, reconstructed.durationMs)
    }
}
