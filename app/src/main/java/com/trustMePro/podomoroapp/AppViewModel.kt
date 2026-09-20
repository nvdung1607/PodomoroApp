package com.trustMePro.podomoroapp

import android.app.Application
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trustMePro.podomoroapp.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Feedback(val message: Int, val undo: (suspend () -> Unit)? = null)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as FocusApplication).container
    private val repo = container.repository
    private val backup = BackupService(app, repo)
    val data = repo.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSnapshot())
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val access = MutableStateFlow(DeviceAccess())
    val elapsed = MutableStateFlow(container.clock.elapsed())
    val pendingRestore = MutableStateFlow<BackupDocument?>(null)
    val feedback = Channel<Feedback>(Channel.BUFFERED)
    val busy = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    fun setVisible(value: Boolean) { visible.value = value }
    init {
        viewModelScope.launch {
            visible.collectLatest { shown ->
                if (shown) while (true) {
                    runCatching { repo.reconcile() }
                    elapsed.value = container.clock.elapsed()
                    refreshAccess()
                    delay(500)
                }
            }
        }
    }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { feedback.send(Feedback(R.string.operation_failed)) }
    } }
    fun refresh() = action { repo.reconcile(reschedule = true); refreshAccess() }
    private fun refreshAccess() {
        access.value = DeviceAccess(container.scheduler.exactAvailable(), NotificationManagerCompat.from(getApplication()).areNotificationsEnabled(), container.dnd.hasPermission(), dndActive = container.dnd.active())
    }
    fun saveTask(task: TaskItem) = action { repo.saveTask(task) }
    fun done(task: TaskItem, value: Boolean) = action { repo.setTaskDone(task.id, value) }
    fun delete(task: TaskItem) = action { repo.deleteTask(task.id); feedback.send(Feedback(R.string.task_deleted) { repo.deleteTask(task.id, true) }) }
    fun saveChecklist(item: ChecklistItem) = action { repo.saveChecklist(item) }
    fun doneChecklist(item: ChecklistItem, value: Boolean) = action { repo.setChecklistDone(item.id, value) }
    fun deleteChecklist(item: ChecklistItem) = action { repo.deleteChecklist(item.id) }
    fun saveGoal(goal: GoalItem) = action { repo.saveGoal(goal) }
    fun delete(goal: GoalItem) = action { val ids = repo.deleteGoal(goal.id); feedback.send(Feedback(R.string.goal_deleted) { repo.restoreGoal(goal.id, ids) }) }
    fun start(taskId: String?, goalId: String?, minutes: Int? = null) = action { repo.startFocus(taskId, goalId, minutes); refreshAccess() }
    fun startBreak(minutes: Int? = null) = action { repo.startBreak(minutes); refreshAccess() }
    fun extendTimer(additionalMs: Long = 60_000L) = action { repo.extendCurrentTimer(additionalMs); refreshAccess() }
    fun setFocusDuration(minutes: Int) = action { val current = container.settings.settings.first(); container.settings.save(current.copy(focus = minutes.coerceIn(1, 180))) }
    fun pause() = action { repo.pause(); refreshAccess() }
    fun resume() = action { repo.resume(); refreshAccess() }
    fun stop() = action { repo.stop(); refreshAccess() }
    fun saveSettings(settings: AppSettings) = action { container.settings.save(settings); repo.reconcile(reschedule = true); refreshAccess(); feedback.send(Feedback(R.string.saved)) }
    fun undo(block: suspend () -> Unit) = action(block)
    fun export(uri: Uri, previous: Boolean = false) = action {
        busy.value = true
        try { if (previous) backup.exportPrevious(uri) else backup.export(uri); feedback.send(Feedback(R.string.backup_saved)) } finally { busy.value = false }
    }
    fun prepareRestore(uri: Uri) = action { busy.value = true; try { pendingRestore.value = backup.read(uri) } finally { busy.value = false } }
    fun confirmRestore() = action {
        val document = pendingRestore.value ?: return@action
        busy.value = true
        try { backup.restore(document); pendingRestore.value = null; feedback.send(Feedback(R.string.restore_done)) } finally { busy.value = false }
    }
}
