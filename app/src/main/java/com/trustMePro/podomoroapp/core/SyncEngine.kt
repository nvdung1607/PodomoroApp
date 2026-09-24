package com.trustMePro.podomoroapp.core

import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Synced(val lastSyncMs: Long) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class SyncEngine(
    private val authService: AuthService,
    private val database: AppDatabase,
    private val scope: CoroutineScope,
    private val settings: SettingsProvider? = null,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val dao = database.dao()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val registrations = mutableListOf<ListenerRegistration>()
    private var authJob: Job? = null
    private var isSyncPaused = false

    init {
        startObservingAuth()
    }

    private fun startObservingAuth() {
        authJob?.cancel()
        authJob = scope.launch {
            authService.authState.collect { user ->
                clearRegistrations()
                if (user != null) {
                    // QA-20: Isolate accounts on the same device.
                    // If a DIFFERENT user logs in on this phone, clear the previous user's local Room data first.
                    try {
                        val currentConfig = settings?.settings?.first()
                        val lastUid = currentConfig?.lastSyncedUid
                        if (lastUid != null && lastUid != user.uid) {
                            database.withTransaction {
                                dao.clearTimer()
                                dao.clearIntervals()
                                dao.clearSessions()
                                dao.clearEvents()
                                dao.clearChecklists()
                                dao.clearTasks()
                                dao.clearGoals()
                            }
                        }
                        if (currentConfig != null) {
                            settings.save(currentConfig.copy(lastSyncedUid = user.uid))
                        }
                    } catch (_: Exception) { }

                    if (!isSyncPaused) {
                        attachListeners(user.uid)
                        syncAll()
                    }
                } else {
                    _syncStatus.value = SyncStatus.Idle
                }
            }
        }
    }

    fun pauseSync() {
        isSyncPaused = true
        clearRegistrations()
    }

    fun resumeSync() {
        isSyncPaused = false
        val uid = authService.currentUser?.uid ?: return
        attachListeners(uid)
    }

    private fun clearRegistrations() {
        registrations.forEach { it.remove() }
        registrations.clear()
    }

    private fun attachListeners(uid: String) {
        val userDoc = firestore.collection("users").document(uid)

        // 1. Tasks Listener
        val taskReg = userDoc.collection("tasks").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _syncStatus.value = SyncStatus.Error("Lỗi đồng bộ công việc: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                try {
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        val remote = taskFromMap(data)
                        val local = dao.task(remote.id)
                        if (local == null || remote.updatedAt >= local.updatedAt) {
                            dao.save(remote)
                        }
                    }
                    if (!snapshot.metadata.hasPendingWrites()) {
                        _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis())
                    }
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.Error("Lỗi lưu công việc: ${e.localizedMessage}")
                }
            }
        }
        registrations.add(taskReg)

        // 2. Goals Listener
        val goalReg = userDoc.collection("goals").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _syncStatus.value = SyncStatus.Error("Lỗi đồng bộ mục tiêu: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                try {
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        val remote = goalFromMap(data)
                        val local = dao.goal(remote.id)
                        val remoteTime = remote.deletedAt ?: remote.createdAt
                        val localTime = local?.let { it.deletedAt ?: it.createdAt } ?: 0L
                        if (local == null || remoteTime >= localTime) {
                            dao.save(remote)
                        }
                    }
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.Error("Lỗi lưu mục tiêu: ${e.localizedMessage}")
                }
            }
        }
        registrations.add(goalReg)

        // 3. Checklists Listener (QA-22: supports document REMOVED)
        val checklistReg = userDoc.collection("checklists").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _syncStatus.value = SyncStatus.Error("Lỗi đồng bộ việc con: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                try {
                    for (change in snapshot.documentChanges) {
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val remote = checklistFromMap(change.document.data)
                                dao.save(remote)
                            }
                            DocumentChange.Type.REMOVED -> {
                                dao.deleteChecklist(change.document.id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.Error("Lỗi lưu việc con: ${e.localizedMessage}")
                }
            }
        }
        registrations.add(checklistReg)

        // 4. Focus Sessions Listener
        val sessionReg = userDoc.collection("sessions").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _syncStatus.value = SyncStatus.Error("Lỗi đồng bộ phiên: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                try {
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        val remote = sessionFromMap(data)
                        val local = dao.session(remote.id)
                        if (local == null || (remote.endedAt ?: remote.startedAt) >= (local.endedAt ?: local.startedAt)) {
                            dao.save(remote)
                        }
                    }
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.Error("Lỗi lưu phiên: ${e.localizedMessage}")
                }
            }
        }
        registrations.add(sessionReg)

        // 5. Focus Intervals Listener (QA-18)
        val intervalReg = userDoc.collection("intervals").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _syncStatus.value = SyncStatus.Error("Lỗi đồng bộ khoảng thời gian: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                try {
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        val remote = intervalFromMap(data)
                        dao.save(remote)
                    }
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.Error("Lỗi lưu khoảng thời gian: ${e.localizedMessage}")
                }
            }
        }
        registrations.add(intervalReg)

        // 6. Events Listener
        val eventReg = userDoc.collection("events").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _syncStatus.value = SyncStatus.Error("Lỗi đồng bộ sự kiện: ${error.localizedMessage}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                try {
                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        val remote = eventFromMap(data)
                        dao.save(remote)
                    }
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.Error("Lỗi lưu sự kiện: ${e.localizedMessage}")
                }
            }
        }
        registrations.add(eventReg)
    }

    fun pushTask(task: TaskItem) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("tasks").document(task.id)
                    .set(task.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi tải lên công việc: ${e.localizedMessage}")
            }
        }
    }

    fun pushGoal(goal: GoalItem) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("goals").document(goal.id)
                    .set(goal.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi tải lên mục tiêu: ${e.localizedMessage}")
            }
        }
    }

    fun pushChecklist(item: ChecklistItem) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("checklists").document(item.id)
                    .set(item.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi tải lên việc con: ${e.localizedMessage}")
            }
        }
    }

    fun deleteRemoteChecklist(id: String) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("checklists").document(id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi xóa việc con từ xa: ${e.localizedMessage}")
            }
        }
    }

    fun pushSession(session: FocusSession) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("sessions").document(session.id)
                    .set(session.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi tải lên phiên: ${e.localizedMessage}")
            }
        }
    }

    fun pushInterval(interval: FocusInterval) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("intervals").document(interval.id)
                    .set(interval.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi tải lên khoảng thời gian: ${e.localizedMessage}")
            }
        }
    }

    fun pushEvent(event: TaskEvent) {
        val uid = authService.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("users").document(uid)
                    .collection("events").document(event.id)
                    .set(event.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Lỗi tải lên sự kiện: ${e.localizedMessage}")
            }
        }
    }

    suspend fun syncAll(replaceRemote: Boolean = false) = withContext(Dispatchers.IO) {
        val uid = authService.currentUser?.uid ?: return@withContext
        _syncStatus.value = SyncStatus.Syncing
        try {
            val userDoc = firestore.collection("users").document(uid)

            // QA-21: Last-Write-Wins check when syncing tasks
            val tasks = dao.tasks()
            for (task in tasks) {
                if (replaceRemote) {
                    userDoc.collection("tasks").document(task.id).set(task.toMap(), SetOptions.merge()).await()
                } else {
                    val remoteDoc = userDoc.collection("tasks").document(task.id).get().await()
                    if (remoteDoc.exists()) {
                        val remoteUpdated = (remoteDoc.data?.get("updatedAt") as? Number)?.toLong() ?: 0L
                        if (task.updatedAt >= remoteUpdated) {
                            userDoc.collection("tasks").document(task.id).set(task.toMap(), SetOptions.merge()).await()
                        } else {
                            dao.save(taskFromMap(remoteDoc.data!!))
                        }
                    } else {
                        userDoc.collection("tasks").document(task.id).set(task.toMap(), SetOptions.merge()).await()
                    }
                }
            }

            val goals = dao.goals()
            for (goal in goals) {
                userDoc.collection("goals").document(goal.id).set(goal.toMap(), SetOptions.merge()).await()
            }

            val checklists = dao.allChecklists()
            for (item in checklists) {
                userDoc.collection("checklists").document(item.id).set(item.toMap(), SetOptions.merge()).await()
            }

            val sessions = dao.sessions()
            for (session in sessions) {
                userDoc.collection("sessions").document(session.id).set(session.toMap(), SetOptions.merge()).await()
            }

            // QA-18: Upload intervals
            val intervals = dao.intervals()
            for (interval in intervals) {
                userDoc.collection("intervals").document(interval.id).set(interval.toMap(), SetOptions.merge()).await()
            }

            // QA-19: Upload events
            val events = dao.events()
            for (event in events) {
                userDoc.collection("events").document(event.id).set(event.toMap(), SetOptions.merge()).await()
            }

            _syncStatus.value = SyncStatus.Synced(System.currentTimeMillis())
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error("Đồng bộ thất bại: ${e.localizedMessage}")
        }
    }

    companion object {
        fun TaskItem.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "title" to title,
            "note" to note,
            "goalId" to goalId,
            "plannedDate" to plannedDate,
            "dueDate" to dueDate,
            "priority" to priority,
            "status" to status,
            "estimate" to estimate,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "completedAt" to completedAt,
            "deletedAt" to deletedAt
        )

        fun taskFromMap(map: Map<String, Any?>): TaskItem = TaskItem(
            id = map["id"] as? String ?: "",
            title = map["title"] as? String ?: "",
            note = map["note"] as? String ?: "",
            goalId = map["goalId"] as? String,
            plannedDate = map["plannedDate"] as? String,
            dueDate = map["dueDate"] as? String,
            priority = (map["priority"] as? Number)?.toInt() ?: 1,
            status = map["status"] as? String ?: "TODO",
            estimate = (map["estimate"] as? Number)?.toInt(),
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            completedAt = (map["completedAt"] as? Number)?.toLong(),
            deletedAt = (map["deletedAt"] as? Number)?.toLong()
        )

        fun GoalItem.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "title" to title,
            "note" to note,
            "dueDate" to dueDate,
            "status" to status,
            "createdAt" to createdAt,
            "deletedAt" to deletedAt
        )

        fun goalFromMap(map: Map<String, Any?>): GoalItem = GoalItem(
            id = map["id"] as? String ?: "",
            title = map["title"] as? String ?: "",
            note = map["note"] as? String ?: "",
            dueDate = map["dueDate"] as? String,
            status = map["status"] as? String ?: "ACTIVE",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            deletedAt = (map["deletedAt"] as? Number)?.toLong()
        )

        fun ChecklistItem.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "taskId" to taskId,
            "title" to title,
            "isDone" to isDone,
            "order" to order,
            "createdAt" to createdAt
        )

        fun checklistFromMap(map: Map<String, Any?>): ChecklistItem = ChecklistItem(
            id = map["id"] as? String ?: "",
            taskId = map["taskId"] as? String ?: "",
            title = map["title"] as? String ?: "",
            isDone = map["isDone"] as? Boolean ?: false,
            order = (map["order"] as? Number)?.toInt() ?: 0,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )

        fun FocusSession.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "taskId" to taskId,
            "goalId" to goalId,
            "title" to title,
            "goalTitle" to goalTitle,
            "plannedMs" to plannedMs,
            "startedAt" to startedAt,
            "endedAt" to endedAt,
            "activeMs" to activeMs,
            "status" to status
        )

        fun sessionFromMap(map: Map<String, Any?>): FocusSession = FocusSession(
            id = map["id"] as? String ?: "",
            taskId = map["taskId"] as? String,
            goalId = map["goalId"] as? String,
            title = map["title"] as? String ?: "",
            goalTitle = map["goalTitle"] as? String,
            plannedMs = (map["plannedMs"] as? Number)?.toLong() ?: 0L,
            startedAt = (map["startedAt"] as? Number)?.toLong() ?: 0L,
            endedAt = (map["endedAt"] as? Number)?.toLong(),
            activeMs = (map["activeMs"] as? Number)?.toLong() ?: 0L,
            status = map["status"] as? String ?: "RUNNING"
        )

        fun FocusInterval.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "sessionId" to sessionId,
            "startedAt" to startedAt,
            "durationMs" to durationMs
        )

        fun intervalFromMap(map: Map<String, Any?>): FocusInterval = FocusInterval(
            id = map["id"] as? String ?: "",
            sessionId = map["sessionId"] as? String ?: "",
            startedAt = (map["startedAt"] as? Number)?.toLong() ?: 0L,
            durationMs = (map["durationMs"] as? Number)?.toLong() ?: 0L
        )

        fun TaskEvent.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "taskId" to taskId,
            "type" to type,
            "occurredAt" to occurredAt
        )

        fun eventFromMap(map: Map<String, Any?>): TaskEvent = TaskEvent(
            id = map["id"] as? String ?: "",
            taskId = map["taskId"] as? String ?: "",
            type = map["type"] as? String ?: "COMPLETED",
            occurredAt = (map["occurredAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
