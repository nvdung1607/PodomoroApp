package com.trustMePro.podomoroapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.trustMePro.podomoroapp.core.ChecklistItem
import com.trustMePro.podomoroapp.core.FocusApplication
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class UiFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun createPersistCompleteReopenAndUndoDelete() {
        val title = "__ui_test__ Học Compose tiếng Việt"
        val repo = (compose.activity.application as FocusApplication).container.repository
        var id: String? = null
        try {
            compose.onAllNodesWithText("Thêm công việc").onFirst().performClick()
            compose.onNodeWithText("Việc cần làm").performTextInput(title)
            compose.onNodeWithText("Ghi chú").performTextInput("Một ghi chú để kiểm tra lưu dữ liệu.")
            compose.onNodeWithText("Lưu").performClick()
            compose.waitUntil(10000) { runBlocking { repo.dao.tasks().any { it.title == title } } }
            id = runBlocking { repo.dao.tasks().first { it.title == title }.id }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }

            // Subtask verification: Add subtask and verify badge appears
            val subtask = ChecklistItem(taskId = id, title = "Việc con thử nghiệm", isDone = false)
            runBlocking { repo.saveChecklist(subtask) }
            compose.waitUntil(5000) { compose.onAllNodesWithText("☑ 0/1 việc con").fetchSemanticsNodes().isNotEmpty() }

            // Complete task
            compose.onNodeWithTag("task-check-$id").performClick()
            compose.waitUntil(5000) { runBlocking { repo.dao.task(id!!)!!.status == "DONE" } }

            // Show all work, including completed, to reopen it.
            compose.onNodeWithText("Tất cả").performScrollTo().performClick()
            compose.onNodeWithTag("task_list").performScrollToNode(hasTestTag("task-check-$id"))
            compose.onNodeWithTag("task-check-$id").performClick()
            compose.waitUntil(5000) { runBlocking { repo.dao.task(id!!)!!.status == "TODO" } }

            compose.onNodeWithTag("task_list").performScrollToNode(hasText(title))
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText("Xóa").performScrollTo().performClick()
            compose.onNodeWithText("Hoàn tác").performClick()
            compose.waitUntil(5000) { runBlocking { repo.dao.task(id!!)!!.deletedAt == null } }
            assertEquals(2, runBlocking { repo.dao.events().count { it.taskId == id } })

            compose.onNodeWithTag("nav_stats").performClick()
            compose.onNodeWithText("Thời gian theo ngày").assertExists()
            runBlocking { if (repo.dao.timer()?.status in listOf("RUNNING", "PAUSED", "AWAITING_BREAK")) repo.stop() }
            compose.onNodeWithTag("nav_focus").performClick()
            compose.onNodeWithText("Bắt đầu tập trung").assertIsEnabled()
        } finally {
            if (id != null) runBlocking {
                val sql = repo.database.openHelper.writableDatabase
                sql.execSQL("DELETE FROM checklists WHERE taskId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM task_events WHERE taskId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
            }
        }
    }
}
