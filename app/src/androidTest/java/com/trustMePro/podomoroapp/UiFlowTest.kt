package com.trustMePro.podomoroapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            compose.onNodeWithTag("task-check-$id").performClick()
            compose.waitUntil(5000) { runBlocking { repo.dao.task(id!!)!!.status == "DONE" } }
            // Show all work, including completed, to reopen it.
            compose.onNodeWithText("Tất cả").performScrollTo().performClick()
            compose.onNodeWithTag("task-check-$id").performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { repo.dao.task(id!!)!!.status == "TODO" } }
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText("Xóa").performScrollTo().performClick()
            compose.onNodeWithText("Hoàn tác").performClick()
            compose.waitUntil(5000) { runBlocking { repo.dao.task(id!!)!!.deletedAt == null } }
            assertEquals(2, runBlocking { repo.dao.events().count { it.taskId == id } })
            compose.onNodeWithTag("nav_stats").performClick()
            compose.onNodeWithText("Thời gian theo ngày").assertExists()
            compose.onNodeWithTag("nav_focus").performClick()
            compose.onNodeWithText("Bắt đầu tập trung").assertIsNotEnabled()
        } finally {
            if (id != null) runBlocking {
                val sql = repo.database.openHelper.writableDatabase
                sql.execSQL("DELETE FROM task_events WHERE taskId = ?", arrayOf(id))
                sql.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
            }
        }
    }
}
