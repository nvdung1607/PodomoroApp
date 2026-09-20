package com.trustMePro.podomoroapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trustMePro.podomoroapp.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val clock = object : TimeSource {
        var now = 1_800_000_000_000L; var mono = 1000L; var bootId = 1
        override fun wall() = now; override fun elapsed() = mono; override fun boot() = bootId
        fun advance(ms: Long) { now += ms; mono += ms }
    }
    private val settings = object : SettingsProvider {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun save(value: AppSettings) { settings.value = value }
    }
    private var notifications = 0
    private var dndActive = false
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = Repository(db, settings, clock, object : EndScheduler {
            override fun schedule(state: TimerState) {} ; override fun cancel() {}; override fun completed(focus: Boolean) { notifications++ }
        }) { dndActive = it }
    }
    @After fun close() { db.close() }
    private suspend fun task(): TaskItem = TaskItem(title = "Học Compose", note = "Ghi chú tiếng Việt").also { repo.saveTask(it) }

    @Test fun taskCompletionReopenAndUndo() = runBlocking {
        val task = task(); repo.setTaskDone(task.id, true); repo.setTaskDone(task.id, true)
        assertEquals(1, repo.dao.events().size)
        repo.setTaskDone(task.id, false); assertEquals("TODO", repo.dao.task(task.id)!!.status)
        repo.deleteTask(task.id); assertNotNull(repo.dao.task(task.id)!!.deletedAt)
        repo.deleteTask(task.id, true); assertNull(repo.dao.task(task.id)!!.deletedAt)
        assertEquals(task.note, repo.dao.task(task.id)!!.note)
    }
    @Test fun pauseResumeCompletionIsIdempotent() = runBlocking {
        val task = task(); repo.startFocus(task.id, null); assertTrue(dndActive)
        clock.advance(600000); repo.pause(); assertFalse(dndActive)
        clock.advance(300000); repo.resume(); val generation = repo.dao.timer()!!.generation
        clock.advance(900000); repo.reconcile(generation); repo.reconcile(generation)
        assertEquals(1500000L, repo.dao.sessions().single().activeMs)
        assertEquals("COMPLETED", repo.dao.sessions().single().status)
        assertEquals("AWAITING_BREAK", repo.dao.timer()!!.status)
        assertEquals(1, notifications); assertFalse(dndActive)
        assertEquals("IN_PROGRESS", repo.dao.task(task.id)!!.status)
    }
    @Test fun fourCyclesAndAbortedSession() = runBlocking {
        val task = task()
        repeat(4) { repo.startFocus(task.id, null); clock.advance(1500000); repo.reconcile() }
        assertEquals(15, repo.dao.timer()!!.breakMinutes)
        repo.startBreak(); clock.advance(900000); repo.reconcile()
        repo.startFocus(task.id, null); clock.advance(600000); repo.stop()
        assertEquals(0, repo.dao.timer()!!.completedInCycle)
        assertEquals(4, repo.dao.sessions().count { it.status == "COMPLETED" })
        assertEquals(600000L, repo.dao.sessions().first { it.status == "ABORTED" }.activeMs)
    }
    @Test fun staleAlarmCannotFinishNewSessionAndRebootInterrupts() = runBlocking {
        val task = task(); repo.startFocus(task.id, null); val old = repo.dao.timer()!!.generation
        clock.advance(1000); repo.stop(); repo.startFocus(task.id, null)
        clock.advance(1500000); repo.reconcile(old)
        assertEquals("RUNNING", repo.dao.timer()!!.status)
        clock.bootId++; clock.mono = 100
        repo.reconcile()
        assertEquals("IDLE", repo.dao.timer()!!.status)
        assertEquals(1, repo.dao.sessions().count { it.status == "INTERRUPTED" })
    }
    @Test fun goalDeletionPreservesHistoryAndUndoLinks() = runBlocking {
        val goal = GoalItem(title = "Mục tiêu"); repo.saveGoal(goal)
        val task = TaskItem(title = "Việc", goalId = goal.id); repo.saveTask(task); repo.startFocus(task.id, null)
        clock.advance(10000); repo.stop()
        val ids = repo.deleteGoal(goal.id)
        assertNull(repo.dao.task(task.id)!!.goalId); assertEquals(goal.id, repo.dao.sessions().single().goalId)
        repo.restoreGoal(goal.id, ids); assertEquals(goal.id, repo.dao.task(task.id)!!.goalId)
    }
    @Test fun rejectsEmptyTitleAndCompletedSelection() = runBlocking {
        try { repo.saveTask(TaskItem(title = " ")); fail() } catch (_: IllegalArgumentException) {}
        val task = task(); repo.setTaskDone(task.id, true)
        try { repo.startFocus(task.id, null); fail() } catch (_: IllegalArgumentException) {}
        assertTrue(repo.dao.sessions().isEmpty())
    }
    @Test fun backupRestoreReplacesAtomicallyAndKeepsPriorCopy() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = java.io.File(context.cacheDir, "backup-test-${newId()}").apply { mkdirs() }
        val wrapper = object : android.content.ContextWrapper(context) { override fun getFilesDir() = folder }
        val backup = BackupService(wrapper, repo)
        val file = java.io.File(folder, "export.json")
        val prior = java.io.File(folder, "prior.json")
        try {
            val original = task(); backup.export(android.net.Uri.fromFile(file))
            val imported = backup.read(android.net.Uri.fromFile(file))
            val extra = TaskItem(title = "Extra"); repo.saveTask(extra)
            backup.restore(imported)
            assertEquals(listOf(original.id), repo.dao.tasks().map { it.id })
            backup.exportPrevious(android.net.Uri.fromFile(prior))
            assertEquals(2, backup.read(android.net.Uri.fromFile(prior)).tasks.size)
            file.writeText("{broken")
            try { backup.read(android.net.Uri.fromFile(file)); fail() } catch (_: Exception) {}
            assertEquals(1, repo.dao.tasks().size)
        } finally { folder.listFiles()?.forEach { it.delete() }; folder.delete() }
    }
    @Test fun checklistOperationsAndPersistence() = runBlocking {
        val task = task()
        val c1 = ChecklistItem(taskId = task.id, title = "Việc con 1", isDone = false)
        val c2 = ChecklistItem(taskId = task.id, title = "Việc con 2", isDone = true)
        repo.saveChecklist(c1)
        repo.saveChecklist(c2)
        assertEquals(2, repo.dao.checklists(task.id).size)
        repo.setChecklistDone(c1.id, true)
        assertTrue(repo.dao.checklist(c1.id)!!.isDone)
        repo.deleteChecklist(c2.id)
        assertEquals(1, repo.dao.checklists(task.id).size)
        assertEquals("Việc con 1", repo.dao.checklists(task.id)[0].title)
    }
    @Test fun startsFreeFocusSessionWithoutTaskOrGoal() = runBlocking {
        var sessionId: String? = null
        try {
            repo.startFocus(null, null)
            val session = repo.dao.sessions().single()
            sessionId = session.id
            assertNull(session.taskId)
            assertNull(session.goalId)
            assertEquals("Tập trung tự do", session.title)
            assertEquals("RUNNING", repo.dao.timer()!!.status)
        } finally {
            repo.stop()
            if (sessionId != null) {
                val sql = repo.database.openHelper.writableDatabase
                sql.execSQL("DELETE FROM intervals WHERE sessionId = ?", arrayOf(sessionId))
                sql.execSQL("DELETE FROM sessions WHERE id = ?", arrayOf(sessionId))
            }
        }
    }
}
