package com.trustMePro.podomoroapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val filterOptions = listOf(
    TaskFilter.TODAY to R.string.today,
    TaskFilter.UPCOMING to R.string.upcoming,
    TaskFilter.INBOX to R.string.inbox,
    TaskFilter.ALL to R.string.all,
    TaskFilter.DONE to R.string.done
)

fun formatFriendlyDate(dateStr: String, today: LocalDate): String {
    return try {
        val parsed = LocalDate.parse(dateStr)
        when (parsed) {
            today -> "Hôm nay"
            today.plusDays(1) -> "Ngày mai"
            today.minusDays(1) -> "Hôm qua"
            else -> {
                if (parsed.year == today.year) {
                    parsed.format(DateTimeFormatter.ofPattern("dd 'thg' MM"))
                } else {
                    parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                }
            }
        }
    } catch (_: Exception) {
        dateStr
    }
}

@Composable fun TasksScreen(
    data: StoreSnapshot, settings: AppSettings,
    onEdit: (TaskItem) -> Unit, onDone: (TaskItem, Boolean) -> Unit,
    onFocus: (TaskItem) -> Unit, onAdd: () -> Unit,
    onSaveChecklist: (ChecklistItem) -> Unit,
    onDoneChecklist: (ChecklistItem, Boolean) -> Unit,
    onDeleteChecklist: (ChecklistItem) -> Unit
) {
    var currentFilter by rememberSaveable { mutableStateOf(TaskFilter.TODAY) }
    var search by rememberSaveable { mutableStateOf("") }
    var expandedTasks by rememberSaveable { mutableStateOf(setOf<String>()) }
    var editingSubtask by remember { mutableStateOf<ChecklistItem?>(null) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val report = remember(data, today) { Statistics.report(data, today, Period.DAY, zone) }
    val streak = remember(data, today) { Statistics.dailyStreak(data, today, zone) }
    val hour = remember { java.time.LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> stringResource(R.string.greeting_morning)
        hour < 18 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }

    val tasks = data.tasks.filter { TaskRules.matches(it, currentFilter, today, search) }
        .sortedWith(
            compareBy<TaskItem> { it.status == "DONE" }
                .thenBy { if (it.status != "DONE" && it.dueDate != null && it.dueDate < today.toString()) 0 else 1 }
                .thenByDescending { it.priority }
                .thenBy { it.dueDate ?: it.plannedDate ?: "9999" }
        )
    fun overdue(t: TaskItem) = t.status != "DONE" && t.dueDate?.let { it < today.toString() } == true

    val sections = when (currentFilter) {
        TaskFilter.TODAY -> listOf(
            stringResource(R.string.overdue) to tasks.filter(::overdue),
            stringResource(R.string.today) to tasks.filter { it.status != "DONE" && !overdue(it) },
            stringResource(R.string.done) to tasks.filter { it.status == "DONE" }
        )
        TaskFilter.UPCOMING -> tasks.groupBy { t ->
            listOfNotNull(t.plannedDate, t.dueDate).filter { it > today.toString() }.minOrNull() ?: ""
        }.toSortedMap().toList()
        else -> listOf("" to tasks)
    }

    LazyColumn(
        modifier = Modifier.testTag("task_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Daily Compact Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                greeting,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                stringResource(R.string.tasks),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (streak > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    stringResource(R.string.streak_badge, streak),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.today_summary, report.completed, report.focusMs / 60_000),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${report.completed}/${settings.dailyTarget} 🍅",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val target = settings.dailyTarget.coerceAtLeast(1)
                    val progress = (report.completed.toFloat() / target).coerceIn(0f, 1f)
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            )
        }

        // Filter Chips Bar (Hôm nay first!)
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { (filter, labelRes) ->
                    val isSelected = currentFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { currentFilter = filter },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        // Empty state
        if (tasks.isEmpty()) {
            item {
                EmptyCard(
                    stringResource(R.string.empty_tasks),
                    stringResource(if (data.tasks.none { it.deletedAt == null }) R.string.empty_tasks_hint else R.string.no_results)
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.add_task), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Task Items with Subtasks
        sections.filter { it.second.isNotEmpty() }.forEach { (heading, group) ->
            if (heading.isNotEmpty()) {
                item(key = "heading-$heading") {
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (heading == stringResource(R.string.overdue)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
            }
            items(group, key = { it.id }) { task ->
                val isDone = task.status == "DONE"
                val isOverdue = overdue(task)
                val subtasks = data.checklists.filter { it.taskId == task.id }
                val doneSubs = subtasks.count { it.isDone }
                val totalSubs = subtasks.size
                val isExpanded = task.id in expandedTasks

                Card(
                    onClick = { onEdit(task) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 1.5.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Row 1: Checkbox, Title, Priority, Focus Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val checkLabel = stringResource(if (isDone) R.string.reopen else R.string.mark_done) + ": " + task.title
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = { onDone(task, it) },
                                modifier = Modifier
                                    .testTag("task-check-${task.id}")
                                    .semantics { contentDescription = checkLabel }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(when(task.status) { "DONE" -> R.string.done; "IN_PROGRESS" -> R.string.task_in_progress; else -> R.string.todo }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (task.priority == 2) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        stringResource(R.string.high),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            if (!isDone) {
                                FilledTonalIconButton(
                                    onClick = { onFocus(task) },
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text("🍅", fontSize = 16.sp)
                                }
                            }
                        }

                        // Note formatted with subtle container
                        if (task.note.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📝 ", fontSize = 13.sp)
                                    Text(
                                        task.note,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Metadata Badges (Due, Planned, Pomodoros)
                        val completed = data.sessions.count { it.taskId == task.id && it.status == "COMPLETED" }
                        val est = task.estimate
                        val hasBadges = completed > 0 || est != null || task.dueDate != null || task.plannedDate != null

                        if (hasBadges) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (completed > 0 || est != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        val tomatoText = when {
                                            est != null && completed > 0 -> "🍅 $completed/$est"
                                            est != null -> "🍅 0/$est"
                                            else -> "🍅 $completed"
                                        }
                                        Text(
                                            tomatoText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                task.dueDate?.let { due ->
                                    val friendlyDue = formatFriendlyDate(due, today)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            (if (isOverdue) "⚠️ " else "📅 ") + friendlyDue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (task.dueDate == null && task.plannedDate != null) {
                                    val friendlyPlanned = formatFriendlyDate(task.plannedDate, today)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "📌 $friendlyPlanned",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Subtasks Section (Parent-Child Task Management) - Only show when subtasks exist or expanded
                        if (totalSubs > 0 || isExpanded) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            // Subtasks Bar: Progress & Expand
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (doneSubs == totalSubs) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "☑ $doneSubs/$totalSubs việc con",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (doneSubs == totalSubs) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { doneSubs.toFloat() / totalSubs },
                                        modifier = Modifier.weight(1f).height(4.dp),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            expandedTasks = if (isExpanded) expandedTasks - task.id else expandedTasks + task.id
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (isExpanded) "Thu gọn ▲" else "Chi tiết ▼",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Expanded Subtasks Content
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // List of Subtasks
                                subtasks.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Checkbox(
                                                checked = item.isDone,
                                                onCheckedChange = { onDoneChecklist(item, it) },
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                item.title,
                                                modifier = Modifier.weight(1f),
                                                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                                color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            IconButton(
                                                onClick = { editingSubtask = item },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("✎", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(
                                                onClick = { onDeleteChecklist(item) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("✕", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                // Quick Add Subtask Input
                                var newChildTitle by remember { mutableStateOf("") }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newChildTitle,
                                        onValueChange = { newChildTitle = it },
                                        placeholder = { Text(stringResource(R.string.quick_add_subtask_hint), style = MaterialTheme.typography.bodySmall) },
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (newChildTitle.isNotBlank()) {
                                                    onSaveChecklist(ChecklistItem(taskId = task.id, title = newChildTitle.trim(), order = totalSubs))
                                                    newChildTitle = ""
                                                }
                                            }
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(
                                        enabled = newChildTitle.isNotBlank(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        onClick = {
                                            if (newChildTitle.isNotBlank()) {
                                                onSaveChecklist(ChecklistItem(taskId = task.id, title = newChildTitle.trim(), order = totalSubs))
                                                newChildTitle = ""
                                            }
                                        }
                                    ) {
                                        Text("+ Thêm", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Inline Edit Subtask Dialog
    editingSubtask?.let { subtask ->
        var editedTitle by remember(subtask.id) { mutableStateOf(subtask.title) }
        AlertDialog(
            onDismissRequest = { editingSubtask = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.edit_subtask_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text(stringResource(R.string.subtask_title)) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = editedTitle.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        onSaveChecklist(subtask.copy(title = editedTitle.trim()))
                        editingSubtask = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSubtask = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TaskEditor(
    task: TaskItem, checklists: List<ChecklistItem> = emptyList(),
    onDismiss: () -> Unit, onSave: (TaskItem) -> Unit, onDelete: (() -> Unit)?,
    onSaveChecklist: ((ChecklistItem) -> Unit)? = null,
    onDoneChecklist: ((ChecklistItem, Boolean) -> Unit)? = null,
    onDeleteChecklist: ((ChecklistItem) -> Unit)? = null
) {
    val today = remember { LocalDate.now() }
    val isNewTask = onDelete == null || task.title.isBlank()
    val defaultPlanned = if (isNewTask) today.toString() else ""
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var note by rememberSaveable(task.id) { mutableStateOf(task.note) }
    var planned by rememberSaveable(task.id) { mutableStateOf(task.plannedDate ?: defaultPlanned) }
    var due by rememberSaveable(task.id) { mutableStateOf(task.dueDate.orEmpty()) }
    var estimate by rememberSaveable(task.id) { mutableStateOf(task.estimate?.toString().orEmpty()) }
    var priority by rememberSaveable(task.id) { mutableIntStateOf(task.priority) }
    var newSubtask by rememberSaveable(task.id) { mutableStateOf("") }
    var editingSubtaskInDialog by remember { mutableStateOf<ChecklistItem?>(null) }
    var showDatePickerForPlanned by remember { mutableStateOf(false) }
    var showDatePickerForDue by remember { mutableStateOf(false) }
    var showCustomEstimateDialog by remember { mutableStateOf(false) }

    val valid = title.isNotBlank() && TaskRules.validDate(planned.ifBlank { null }) && TaskRules.validDate(due.ifBlank { null }) && (estimate.isBlank() || estimate.toIntOrNull()?.let { it in 1..10000 } == true)

    val quickTitles = listOf("Đọc sách 30p", "Học tập / Lập trình", "Tập thể dục", "Viết báo cáo", "Dọn dẹp bàn", "Lên kế hoạch tuần")
    val weekendDate = today.plusDays(((6 - today.dayOfWeek.value % 7 + 7) % 7).toLong().coerceAtLeast(1L)).toString()
    val nextWeekDate = today.plusWeeks(1).toString()

    val quickPlannedDates = listOf(
        stringResource(R.string.today) to today.toString(),
        stringResource(R.string.tomorrow) to today.plusDays(1).toString(),
        stringResource(R.string.this_weekend) to weekendDate,
        stringResource(R.string.next_week) to nextWeekDate,
        stringResource(R.string.no_date) to ""
    )

    val quickDeadlines = listOf(
        stringResource(R.string.today) to today.toString(),
        stringResource(R.string.tomorrow) to today.plusDays(1).toString(),
        stringResource(R.string.this_weekend) to weekendDate,
        stringResource(R.string.next_week) to nextWeekDate,
        stringResource(R.string.no_date) to ""
    )

    fun submitTask() {
        if (valid) {
            onSave(
                task.copy(
                    title = title.trim(),
                    note = note.trim(),
                    plannedDate = planned.ifBlank { null },
                    dueDate = due.ifBlank { null },
                    priority = priority,
                    estimate = estimate.toIntOrNull()
                )
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (isNewTask) "➕" else "📝", fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (isNewTask) R.string.add_task else R.string.edit_task),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isNewTask) {
                        IconButton(onClick = { onDelete?.invoke() }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete_task),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Scrollable content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick task suggestions (When creating a new task)
                if (isNewTask) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTitles.forEach { item ->
                            SuggestionChip(
                                onClick = { title = item },
                                label = { Text(item, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // Task Title Input Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title)) },
                    placeholder = { Text(stringResource(R.string.task_hint)) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitTask() }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Note Input Field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note)) },
                    placeholder = { Text(stringResource(R.string.note_hint)) },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Smart Attribute Section (Chips 1-tap, no tedious form fields!)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Ngày lên lịch (Planned Date)
                        Text(
                            "📅 " + stringResource(R.string.planned_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickPlannedDates.forEach { (label, dateVal) ->
                                val isSelected = (planned == dateVal && dateVal.isNotEmpty()) || (planned.isEmpty() && dateVal.isEmpty())
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { planned = dateVal },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            val isCustomPlanned = planned.isNotEmpty() && quickPlannedDates.none { it.second == planned }
                            FilterChip(
                                selected = isCustomPlanned,
                                onClick = { showDatePickerForPlanned = true },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text(
                                        if (isCustomPlanned) formatFriendlyDate(planned, today) else stringResource(R.string.pick_date),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }

                        // 2. Độ ưu tiên (Priority)
                        Text(
                            "🚩 " + stringResource(R.string.priority),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                0 to ("⚪ " + stringResource(R.string.low)),
                                1 to ("🟡 " + stringResource(R.string.normal)),
                                2 to ("🚩 " + stringResource(R.string.high))
                            ).forEach { (level, text) ->
                                FilterChip(
                                    selected = priority == level,
                                    onClick = { priority = level },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text(text, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        // 3. Dự tính Pomodoro (Estimate)
                        Text(
                            "🍅 " + stringResource(R.string.estimate),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1, 2, 4, 6).forEach { count ->
                                val isSelected = estimate == count.toString()
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        estimate = if (isSelected) "" else count.toString()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text("$count 🍅", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            val customSelected = estimate.isNotEmpty() && estimate !in listOf("1", "2", "4", "6")
                            FilterChip(
                                selected = customSelected,
                                onClick = { showCustomEstimateDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text(
                                        if (customSelected) "$estimate 🍅" else "+ " + stringResource(R.string.custom_minutes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }

                        // 4. Hạn chót (Deadline)
                        Text(
                            "⏰ " + stringResource(R.string.deadline_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            quickDeadlines.forEach { (label, dateVal) ->
                                val isSelected = (due == dateVal && dateVal.isNotEmpty()) || (due.isEmpty() && dateVal.isEmpty())
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { due = dateVal },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            val isCustomDue = due.isNotEmpty() && quickDeadlines.none { it.second == due }
                            FilterChip(
                                selected = isCustomDue,
                                onClick = { showDatePickerForDue = true },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text(
                                        if (isCustomDue) formatFriendlyDate(due, today) else stringResource(R.string.pick_date),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }

                        // Warning if deadline is earlier than planned date
                        val hasDateConflict = due.isNotBlank() && planned.isNotBlank() && due < planned
                        if (hasDateConflict) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("⚠️", fontSize = 13.sp)
                                    Text(
                                        stringResource(R.string.deadline_before_planned),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Subtasks / Checklist (When editing an existing task)
                if (onDelete != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.subtasks),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            checklists.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(checked = item.isDone, onCheckedChange = { onDoneChecklist?.invoke(item, it) })
                                    Text(
                                        item.title,
                                        modifier = Modifier.weight(1f),
                                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                        color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    IconButton(onClick = { editingSubtaskInDialog = item }) {
                                        Text("✎", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onDeleteChecklist?.invoke(item) }) {
                                        Text("✕", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = newSubtask, onValueChange = { newSubtask = it },
                                    placeholder = { Text(stringResource(R.string.subtask_title)) },
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true, modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    enabled = newSubtask.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    onClick = {
                                        onSaveChecklist?.invoke(ChecklistItem(taskId = task.id, title = newSubtask.trim(), order = checklists.size))
                                        newSubtask = ""
                                    }
                                ) { Text(stringResource(R.string.add_subtask), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }

                    // Delete button inside scroll area (can be scrolled to by performScrollTo)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!valid && title.isNotEmpty()) {
                    Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Pinned Bottom Button - ALWAYS visible on screen!
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    enabled = valid,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = { submitTask() }
                ) {
                    Text(
                        stringResource(R.string.save),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // Material 3 DatePickerDialog for Planned Date
    if (showDatePickerForPlanned) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                if (planned.isNotBlank()) {
                    LocalDate.parse(planned).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } else {
                    System.currentTimeMillis()
                }
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerForPlanned = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        planned = local.toString()
                    }
                    showDatePickerForPlanned = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForPlanned = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 DatePickerDialog for Due Date (Deadline)
    if (showDatePickerForDue) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                if (due.isNotBlank()) {
                    LocalDate.parse(due).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } else {
                    System.currentTimeMillis()
                }
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerForDue = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val local = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        due = local.toString()
                    }
                    showDatePickerForDue = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForDue = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Custom Estimate Number Dialog
    if (showCustomEstimateDialog) {
        var tempEstimate by remember { mutableStateOf(estimate) }
        AlertDialog(
            onDismissRequest = { showCustomEstimateDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.estimate), style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = tempEstimate,
                    onValueChange = { tempEstimate = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số Pomodoro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        estimate = tempEstimate
                        showCustomEstimateDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomEstimateDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Inline Edit Subtask Dialog from TaskEditor
    editingSubtaskInDialog?.let { subtask ->
        var editedTitle by remember(subtask.id) { mutableStateOf(subtask.title) }
        AlertDialog(
            onDismissRequest = { editingSubtaskInDialog = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.edit_subtask_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text(stringResource(R.string.subtask_title)) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = editedTitle.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        onSaveChecklist?.invoke(subtask.copy(title = editedTitle.trim()))
                        editingSubtaskInDialog = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSubtaskInDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
