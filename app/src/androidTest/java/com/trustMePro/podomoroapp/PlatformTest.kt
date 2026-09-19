package com.trustMePro.podomoroapp

import android.app.NotificationManager
import android.os.Build
import android.service.notification.ZenPolicy
import androidx.test.platform.app.InstrumentationRegistry
import com.trustMePro.podomoroapp.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

class PlatformTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
    @Test fun ownedDndRuleAllowsCallsAndCanBeDeactivated() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val manager = context.getSystemService(NotificationManager::class.java)
        val hadAccess = manager.isNotificationPolicyAccessGranted
        val oldIds = if (hadAccess) manager.automaticZenRules.keys.toSet() else emptySet()
        if (!hadAccess) shell("cmd notification allow_dnd ${context.packageName}")
        val adapter = FocusDnd(context)
        try {
            assertTrue(adapter.hasPermission())
            adapter.apply(true); delay(500)
            assertTrue(adapter.active())
            val rule = manager.automaticZenRules.values.first { it.name == context.getString(R.string.dnd_rule) }
            assertEquals(ZenPolicy.PEOPLE_TYPE_ANYONE, rule.zenPolicy!!.priorityCallSenders)
            adapter.apply(false); delay(300)
            assertFalse(adapter.active())
            // Other owned rules, if any, must still exist.
            assertTrue(manager.automaticZenRules.keys.containsAll(oldIds))
        } finally {
            adapter.apply(false)
            if (!hadAccess) shell("cmd notification disallow_dnd ${context.packageName}")
        }
    }
    @Test fun exactAlarmCompletesPersistedSessionWithNoActivity() = runBlocking {
        val container = (context.applicationContext as FocusApplication).container
        val repo = container.repository; val dao = repo.dao
        val old = dao.timer()
        assumeTrue(old?.status !in listOf("RUNNING", "PAUSED"))
        val exact = container.scheduler.exactAvailable()
        // Grant from the host before this test. Revoking exact alarms kills the app process,
        // so permission cleanup must happen after instrumentation has exited.
        assumeTrue("Host must grant exact alarms for the platform spike", exact)
        val task = TaskItem(title = "__platform_test__")
        var sessionId: String? = null
        try {
            repo.saveTask(task); repo.startFocus(task.id, null)
            val state = dao.timer()!!; sessionId = state.sessionId
            dao.save(state.copy(remainingMs = 2000, segmentElapsed = container.clock.elapsed(), segmentWall = container.clock.wall()))
            val deadline = dao.timer()!!.segmentElapsed + 2000
            repo.reconcile(reschedule = true)
            repeat(30) { if (dao.session(sessionId!!)!!.status == "RUNNING") delay(200) }
            println("ALARM_OBSERVED_DELAY_MS=${container.clock.elapsed() - deadline}")
            assertEquals("COMPLETED", dao.session(sessionId!!)!!.status)
            assertEquals("AWAITING_BREAK", dao.timer()!!.status)
        } finally {
            container.scheduler.cancel(); container.dnd.apply(false)
            val sql = repo.database.openHelper.writableDatabase
            if (sessionId != null) { sql.execSQL("DELETE FROM intervals WHERE sessionId = ?", arrayOf(sessionId)); sql.execSQL("DELETE FROM sessions WHERE id = ?", arrayOf(sessionId)) }
            sql.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(task.id))
            if (old == null) dao.clearTimer() else dao.save(old)
        }
    }
}
