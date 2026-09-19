package com.trustMePro.podomoroapp

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.trustMePro.podomoroapp.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Two host-driven stages: instrumentation exits between them, so no app process keeps time. */
class RecoveryProbeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as FocusApplication).container
    private val marker = File(context.filesDir, "recovery-probe.json")
    private data class Proof(val taskId: String, val sessionId: String, val oldTimer: TimerState?)
    @Test fun stage() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("probe") == "stage")
        assumeTrue(container.scheduler.exactAvailable())
        val repo = container.repository; val old = repo.dao.timer()
        assumeTrue(old?.status !in listOf("RUNNING", "PAUSED"))
        check(!marker.exists())
        val task = TaskItem(title = "__recovery_probe__")
        repo.saveTask(task); repo.startFocus(task.id, null)
        val state = repo.dao.timer()!!
        marker.writeText(Gson().toJson(Proof(task.id, state.sessionId!!, old)))
        repo.dao.save(state.copy(remainingMs = 10000, segmentElapsed = container.clock.elapsed(), segmentWall = container.clock.wall()))
        repo.reconcile(reschedule = true)
        println("RECOVERY_PROBE_STAGED")
    }
    @Test fun verifyAndClean() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("probe") == "verify")
        val proof = Gson().fromJson(marker.readText(), Proof::class.java)
        val repo = container.repository
        try {
            val session = repo.dao.session(proof.sessionId)!!
            assertEquals("COMPLETED", session.status)
            assertEquals(10000L, session.activeMs)
            assertEquals(1, repo.dao.intervals().count { it.sessionId == proof.sessionId })
            assertEquals("AWAITING_BREAK", repo.dao.timer()!!.status)
            println("RECOVERY_PROBE_PASSED: receiver completed exactly once without an Activity or surviving instrumentation")
        } finally {
            container.scheduler.cancel(); container.dnd.apply(false)
            val sql = repo.database.openHelper.writableDatabase
            sql.execSQL("DELETE FROM intervals WHERE sessionId = ?", arrayOf(proof.sessionId))
            sql.execSQL("DELETE FROM sessions WHERE id = ?", arrayOf(proof.sessionId))
            sql.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(proof.taskId))
            if (proof.oldTimer == null) repo.dao.clearTimer() else repo.dao.save(proof.oldTimer)
            marker.delete()
        }
    }
}
