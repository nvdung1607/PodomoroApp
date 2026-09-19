package com.trustMePro.podomoroapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import java.time.LocalDate
import java.time.ZoneId

private val filterLabels = listOf(R.string.inbox, R.string.today, R.string.upcoming, R.string.all, R.string.done)
@Composable fun TasksScreen(data: StoreSnapshot, settings: AppSettings, onEdit: (TaskItem) -> Unit, onDone: (TaskItem, Boolean) -> Unit, onFocus: (TaskItem) -> Unit, onAdd: () -> Unit) {
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val report = Statistics.report(data, today, Period.DAY, ZoneId.systemDefault())
    val tasks = data.tasks.filter { TaskRules.matches(it, TaskFilter.entries[filterIndex], today, search) }
        .sortedWith(compareBy<TaskItem> { it.status == "DONE" }.thenBy { if (it.status != "DONE" && it.dueDate != null && it.dueDate < today.toString()) 0 else 1 }.thenByDescending { it.priority }.thenBy { it.dueDate ?: it.plannedDate ?: "9999" })
    fun overdue(t: TaskItem) = t.status != "DONE" && t.dueDate?.let { it < today.toString() } == true
    val sections = when (TaskFilter.entries[filterIndex]) {
        TaskFilter.TODAY -> listOf(stringResource(R.string.overdue) to tasks.filter(::overdue),
            stringResource(R.string.today) to tasks.filter { it.status != "DONE" && !overdue(it) },
            stringResource(R.string.done) to tasks.filter { it.status == "DONE" })
        TaskFilter.UPCOMING -> tasks.groupBy { t -> listOfNotNull(t.plannedDate, t.dueDate).filter { it > today.toString() }.min() }.toSortedMap().toList()
        else -> listOf("" to tasks)
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(stringResource(R.string.tasks), stringResource(R.string.tagline)) }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.today_summary, report.completed, report.focusMs / 60_000), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (report.completed.toFloat() / settings.dailyTarget).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.daily_progress, report.completed, settings.dailyTarget), style = MaterialTheme.typography.labelMedium)
            }
        } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search)) }, singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filterLabels.forEachIndexed { index, label -> FilterChip(selected = filterIndex == index, onClick = { filterIndex = index }, label = { Text(stringResource(label)) }) }
        } }
        if (tasks.isEmpty()) item { EmptyCard(stringResource(R.string.empty_tasks), stringResource(if (data.tasks.none { it.deletedAt == null }) R.string.empty_tasks_hint else R.string.no_results)); TextButton(onClick = onAdd) { Text(stringResource(R.string.add_task)) } }
        sections.filter { it.second.isNotEmpty() }.forEach { (heading, group) ->
        if (heading.isNotEmpty()) item(key = "heading-$heading") { Text(heading, style = MaterialTheme.typography.titleMedium, color = if (heading == stringResource(R.string.overdue)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        items(group, key = { it.id }) { task ->
            Card(onClick = { onEdit(task) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val checkLabel = stringResource(if (task.status == "DONE") R.string.reopen else R.string.mark_done) + ": " + task.title
                        Checkbox(checked = task.status == "DONE", onCheckedChange = { onDone(task, it) }, modifier = Modifier.testTag("task-check-${task.id}").semantics { contentDescription = checkLabel })
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when(task.status) { "DONE" -> R.string.done; "IN_PROGRESS" -> R.string.task_in_progress; else -> R.string.todo }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (task.priority == 2) Text(stringResource(R.string.high), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    if (task.note.isNotBlank()) Text(task.note, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    task.estimate?.let { Text(stringResource(R.string.estimate_value, it), style = MaterialTheme.typography.labelMedium) }
                    val completed = data.sessions.count { it.taskId == task.id && it.status == "COMPLETED" }
                    if (completed > 0) Text(stringResource(R.string.task_pomodoros, completed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    data.goals.find { it.id == task.goalId && it.deletedAt == null }?.let { Text(it.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                    task.plannedDate?.let { Text(stringResource(R.string.planned_value, it), style = MaterialTheme.typography.labelMedium) }
                    task.dueDate?.let { Text(stringResource(R.string.due_value, it) + if (task.status != "DONE" && it < today.toString()) " · " + stringResource(R.string.overdue) else "", color = if (task.status != "DONE" && it < today.toString()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                    if (task.status != "DONE") TextButton(onClick = { onFocus(task) }) { Text(stringResource(R.string.start_task)) }
                }
            }
        }
        }
    }
}

@Composable fun GoalSelector(goals: List<GoalItem>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(goals.find { it.id == selected }?.title ?: stringResource(R.string.no_goal)) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.no_goal)) }, onClick = { onSelect(null); expanded = false })
            goals.forEach { goal -> DropdownMenuItem(text = { Text(goal.title) }, onClick = { onSelect(goal.id); expanded = false }) }
        }
    }
}

@Composable fun TaskEditor(task: TaskItem, goals: List<GoalItem>, onDismiss: () -> Unit, onSave: (TaskItem) -> Unit, onDelete: (() -> Unit)?) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }; var note by rememberSaveable(task.id) { mutableStateOf(task.note) }
    var planned by rememberSaveable(task.id) { mutableStateOf(task.plannedDate.orEmpty()) }; var due by rememberSaveable(task.id) { mutableStateOf(task.dueDate.orEmpty()) }
    var estimate by rememberSaveable(task.id) { mutableStateOf(task.estimate?.toString().orEmpty()) }; var goal by rememberSaveable(task.id) { mutableStateOf(task.goalId) }; var priority by rememberSaveable(task.id) { mutableIntStateOf(task.priority) }
    val valid = title.isNotBlank() && TaskRules.validDate(planned.ifBlank { null }) && TaskRules.validDate(due.ifBlank { null }) && (estimate.isBlank() || estimate.toIntOrNull()?.let { it in 1..10000 } == true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (onDelete == null) R.string.add_task else R.string.edit_task)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.task_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(planned, { planned = it.trim() }, label = { Text(stringResource(R.string.planned)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { planned = LocalDate.now().toString() }) { Text(stringResource(R.string.today)) }
            OutlinedTextField(due, { due = it.trim() }, label = { Text(stringResource(R.string.deadline)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.priority))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(R.string.low, R.string.normal, R.string.high).forEachIndexed { i, name -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(stringResource(name)) }) } }
            GoalSelector(goals, goal) { goal = it }
            OutlinedTextField(estimate, { estimate = it }, label = { Text(stringResource(R.string.estimate)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!valid && title.isNotEmpty()) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(task.copy(title = title, note = note, plannedDate = planned.ifBlank { null }, dueDate = due.ifBlank { null }, priority = priority, goalId = goal, estimate = estimate.toIntOrNull())) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun GoalsScreen(data: StoreSnapshot, onEdit: (GoalItem) -> Unit, onAddTask: (GoalItem) -> Unit, onFocus: (GoalItem) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(stringResource(R.string.goals)) }
        if (data.goals.none { it.deletedAt == null }) item { EmptyCard(stringResource(R.string.empty_goals), stringResource(R.string.empty_goals_hint)) }
        items(data.goals.filter { it.deletedAt == null }, key = { it.id }) { goal ->
            val tasks = data.tasks.filter { it.goalId == goal.id && it.deletedAt == null }; val count = tasks.count { it.status == "DONE" }
            Card(onClick = { onEdit(goal) }) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(goal.title, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(when(goal.status) { "COMPLETED" -> R.string.done; "ARCHIVED" -> R.string.archived; else -> R.string.active }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (goal.note.isNotBlank()) Text(goal.note)
                goal.dueDate?.let { Text(stringResource(R.string.due_value, it), style = MaterialTheme.typography.labelMedium) }
                Text(if (tasks.isEmpty()) stringResource(R.string.goal_no_tasks) else stringResource(R.string.goal_progress, count, tasks.size))
                LinearProgressIndicator(progress = { if (tasks.isEmpty()) 0f else count.toFloat() / tasks.size }, modifier = Modifier.fillMaxWidth())
                tasks.take(4).forEach { Text((if (it.status == "DONE") "✓ " else "○ ") + it.title, style = MaterialTheme.typography.bodyMedium) }
                TextButton(onClick = { onAddTask(goal) }) { Text(stringResource(R.string.goal_add_task)) }
                if (goal.status == "ACTIVE") OutlinedButton(onClick = { onFocus(goal) }) { Text(stringResource(R.string.start_focus)) }
            } }
        }
    }
}

@Composable fun GoalEditor(goal: GoalItem, onDismiss: () -> Unit, onSave: (GoalItem) -> Unit, onDelete: (() -> Unit)?) {
    var title by rememberSaveable(goal.id) { mutableStateOf(goal.title) }; var note by rememberSaveable(goal.id) { mutableStateOf(goal.note) }
    var due by rememberSaveable(goal.id) { mutableStateOf(goal.dueDate.orEmpty()) }; var status by rememberSaveable(goal.id) { mutableStateOf(goal.status) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (onDelete == null) R.string.add_goal else R.string.edit_goal)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.goal_title)) })
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note)) })
            OutlinedTextField(due, { due = it.trim() }, label = { Text(stringResource(R.string.deadline)) })
            listOf("ACTIVE" to R.string.active, "COMPLETED" to R.string.done, "ARCHIVED" to R.string.archived).forEach { (value, label) -> FilterChip(selected = status == value, onClick = { status = value }, label = { Text(stringResource(label)) }) }
            if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && TaskRules.validDate(due.ifBlank { null }), onClick = { onSave(goal.copy(title = title, note = note, dueDate = due.ifBlank { null }, status = status)) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
