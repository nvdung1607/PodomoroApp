package com.trustMePro.podomoroapp.core

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class BackupDocument(val schemaVersion: Int = 2, val exportedAt: Long, val tasks: List<TaskItem>, val goals: List<GoalItem>, val events: List<TaskEvent>, val sessions: List<FocusSession>, val intervals: List<FocusInterval>, val checklists: List<ChecklistItem>? = emptyList()) {
    val safeChecklists: List<ChecklistItem> get() = checklists.orEmpty()
}

object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    fun encode(document: BackupDocument): String = gson.toJson(document)
    fun decode(text: String): BackupDocument {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val result = requireNotNull(gson.fromJson(text, BackupDocument::class.java))
        validate(result)
        return result
    }
    fun validate(b: BackupDocument) {
        require(b.schemaVersion in 1..2 && b.exportedAt > 0)
        requireNotNull(b.tasks); requireNotNull(b.goals); requireNotNull(b.events); requireNotNull(b.sessions); requireNotNull(b.intervals)
        val checklists = b.checklists.orEmpty()
        fun checkIds(ids: List<String>) { require(ids.all { !it.isNullOrBlank() } && ids.distinct().size == ids.size) }
        checkIds(b.tasks.map { it.id }); checkIds(b.goals.map { it.id }); checkIds(b.events.map { it.id }); checkIds(b.sessions.map { it.id }); checkIds(b.intervals.map { it.id }); checkIds(checklists.map { it.id })
        val tasks = b.tasks.map { it.id }.toSet(); val goals = b.goals.map { it.id }.toSet(); val sessions = b.sessions.associateBy { it.id }
        b.tasks.forEach { require(!it.title.isNullOrBlank() && it.status in listOf("TODO", "IN_PROGRESS", "DONE") && it.priority in 0..2 && TaskRules.validDate(it.plannedDate) && TaskRules.validDate(it.dueDate) && (it.goalId == null || it.goalId in goals) && (it.estimate == null || it.estimate in 1..10000)) }
        b.goals.forEach { require(!it.title.isNullOrBlank() && it.status in listOf("ACTIVE", "COMPLETED", "ARCHIVED") && TaskRules.validDate(it.dueDate)) }
        b.events.forEach { require(it.taskId in tasks && it.type in listOf("COMPLETED", "REOPENED") && it.occurredAt > 0) }
        b.sessions.forEach { require(!it.title.isNullOrBlank() && it.status in listOf("COMPLETED", "ABORTED", "INTERRUPTED") && it.plannedMs in 60_000..10_800_000 && it.activeMs in 0..it.plannedMs && it.startedAt > 0 && it.endedAt != null && it.endedAt >= it.startedAt && (it.taskId == null || it.taskId in tasks) && (it.goalId == null || it.goalId in goals)) }
        b.intervals.forEach { require(it.sessionId in sessions && it.durationMs in 1..10_800_000 && it.startedAt > 0 && it.startedAt <= Long.MAX_VALUE - it.durationMs) }
        checklists.forEach { require(it.taskId in tasks && it.title.isNotBlank()) }
        val totals = b.intervals.groupBy { it.sessionId }.mapValues { (_, intervals) -> intervals.sumOf { it.durationMs } }
        b.sessions.forEach { session -> require((totals[session.id] ?: 0L) == session.activeMs) }
    }
}

class BackupService(private val context: Context, private val repo: Repository) {
    private val previous = AtomicFile(File(context.filesDir, "before-restore.json"))
    private suspend fun document(): BackupDocument {
        val dao = repo.dao
        check(dao.timer()?.status !in listOf("RUNNING", "PAUSED"))
        return BackupDocument(exportedAt = repo.clock.wall(), tasks = dao.tasks(), goals = dao.goals(), events = dao.events(), sessions = dao.sessions(), intervals = dao.intervals(), checklists = dao.allChecklists())
    }
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val text = repo.mutex.withLock { repo.database.withTransaction { BackupCodec.encode(document()) } }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
    suspend fun read(uri: Uri): BackupDocument = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) { val n = stream.read(chunk); if (n < 0) break; require(buffer.size() + n <= BackupCodec.MAX_BYTES); buffer.write(chunk, 0, n) }
            buffer.toByteArray()
        }
        BackupCodec.decode(bytes.toString(Charsets.UTF_8))
    }
    suspend fun restore(b: BackupDocument) = withContext(Dispatchers.IO) {
        BackupCodec.validate(b)
        repo.syncEngine?.pauseSync()
        try {
            repo.mutex.withLock {
                repo.database.withTransaction {
                    val text = BackupCodec.encode(document())
                    val stream = previous.startWrite()
                    try { stream.write(text.toByteArray(Charsets.UTF_8)); previous.finishWrite(stream) }
                    catch (e: Exception) { previous.failWrite(stream); throw e }
                    val dao = repo.dao
                    dao.clearTimer(); dao.clearIntervals(); dao.clearSessions(); dao.clearEvents(); dao.clearChecklists(); dao.clearTasks(); dao.clearGoals()
                    b.goals.forEach { dao.save(it) }; b.tasks.forEach { dao.save(it) }; b.events.forEach { dao.save(it) }
                    b.checklists.orEmpty().forEach { dao.save(it) }
                    b.sessions.forEach { dao.save(it) }; b.intervals.forEach { dao.save(it) }; dao.save(TimerState())
                }
            }
            repo.reconcile(reschedule = true)
            repo.syncEngine?.syncAll(replaceRemote = true)
        } finally {
            repo.syncEngine?.resumeSync()
        }
    }
    suspend fun exportPrevious(uri: Uri) = withContext(Dispatchers.IO) {
        val text = previous.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
