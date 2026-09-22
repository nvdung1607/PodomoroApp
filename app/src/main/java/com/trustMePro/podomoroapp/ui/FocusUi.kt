package com.trustMePro.podomoroapp.ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustMePro.podomoroapp.AppViewModel
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import java.util.Locale

@Composable fun FocusScreen(
    data: StoreSnapshot, config: AppSettings, access: DeviceAccess,
    elapsed: Long, selectedTask: String?,
    onChoose: (String?) -> Unit, model: AppViewModel
) {
    var chooser by rememberSaveable { mutableStateOf(false) }
    var stop by rememberSaveable { mutableStateOf(false) }
    var showCustomFocusDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomBreakDialog by rememberSaveable { mutableStateOf(false) }
    var idleMode by rememberSaveable { mutableStateOf("FOCUS") }
    val state = data.timer
    var selectedBreakMinutes by rememberSaveable(state.breakMinutes) { mutableIntStateOf(state.breakMinutes) }
    val live = state.status in listOf("RUNNING", "PAUSED")
    val isPaused = state.status == "PAUSED"
    val isBreakActive = live && state.phase == "BREAK"
    val isBreakMode = isBreakActive || (!live && idleMode == "BREAK")
    val session = data.sessions.find { it.id == state.sessionId }
    val task = data.tasks.find { it.id == selectedTask && it.deletedAt == null && it.status != "DONE" }
    val left = when {
        live -> TimerRules.remaining(state, elapsed)
        idleMode == "BREAK" -> selectedBreakMinutes * 60_000L
        else -> config.focus * 60_000L
    }
    val seconds = (left + 999) / 1000

    val primaryColor = when {
        isBreakMode -> MaterialTheme.colorScheme.secondary
        isPaused -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val containerColor = when {
        isBreakMode -> MaterialTheme.colorScheme.secondaryContainer
        isPaused -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Timer Dial & Status Pill
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (live || isBreakActive || isPaused) {
                    PhasePillBadge(containerColor, isBreakActive, !live && idleMode == "BREAK", isPaused, live)
                    Spacer(Modifier.height(10.dp))
                }
                ZenDialTimer(
                    live = live,
                    left = left,
                    plannedMs = if (live) state.plannedMs else (if (idleMode == "BREAK") selectedBreakMinutes * 60_000L else config.focus * 60_000L),
                    primaryColor = primaryColor,
                    seconds = seconds,
                    isPaused = isPaused,
                    completedInCycle = state.completedInCycle,
                    isBreak = isBreakMode,
                    maxDialSize = 190.dp
                )
            }

            // Right Column: Controls, presets, task info, break card
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.status == "AWAITING_BREAK") {
                    BreakCardContent(
                        selectedBreakMinutes = selectedBreakMinutes,
                        onSelectBreak = { selectedBreakMinutes = it },
                        onCustomBreak = { showCustomBreakDialog = true },
                        onStartBreak = { model.startBreak(selectedBreakMinutes) },
                        onSkipBreak = model::stop,
                        currentTask = data.tasks.find { it.id == session?.taskId && it.deletedAt == null && it.status != "DONE" },
                        onFinishTask = { model.done(it, true) }
                    )
                } else {
                    if (!live) {
                        ModeSelectorPill(currentMode = idleMode, onSelectMode = { idleMode = it })
                        if (idleMode == "BREAK") {
                            QuickBreakPresetsContent(
                                currentBreak = selectedBreakMinutes,
                                onSelectBreak = { selectedBreakMinutes = it },
                                onCustomBreak = { showCustomBreakDialog = true }
                            )
                            BreakTipCardContent()
                        } else {
                            QuickFocusPresetsContent(
                                currentFocus = config.focus,
                                onSelectFocus = { model.setFocusDuration(it) },
                                onCustomFocus = { showCustomFocusDialog = true }
                            )
                            TaskInfoCardContent(
                                live = live,
                                sessionTitle = session?.title,
                                taskTitle = task?.title,
                                onChoose = { chooser = true }
                            )
                        }
                    } else {
                        if (isBreakActive) {
                            BreakTipCardContent()
                        } else {
                            TaskInfoCardContent(
                                live = live,
                                sessionTitle = session?.title,
                                taskTitle = task?.title,
                                onChoose = { chooser = true }
                            )
                        }
                    }

                    FocusControlButtonsContent(
                        live = live,
                        isRunning = state.status == "RUNNING",
                        isBreak = isBreakActive || (!live && idleMode == "BREAK"),
                        onPauseResume = if (state.status == "RUNNING") model::pause else model::resume,
                        onExtend1m = { model.extendTimer(60_000L) },
                        onExtend5m = { model.extendTimer(300_000L) },
                        onStop = { if (isBreakActive) model.stop() else stop = true },
                        onStart = {
                            if (idleMode == "BREAK") model.startBreak(selectedBreakMinutes)
                            else model.start(task?.id, null)
                        }
                    )
                }

                if (isBreakActive) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🍃", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.break_dnd_off_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    DndWarningBannerContent(
                        showDndHint = config.useDnd && access.dndActive,
                        showAlarmWarning = !access.exact
                    )
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (live || isBreakActive || isPaused) {
                PhasePillBadge(containerColor, isBreakActive, !live && idleMode == "BREAK", isPaused, live)
            }
            if (state.status == "AWAITING_BREAK") {
                BreakCardContent(
                    selectedBreakMinutes = selectedBreakMinutes,
                    onSelectBreak = { selectedBreakMinutes = it },
                    onCustomBreak = { showCustomBreakDialog = true },
                    onStartBreak = { model.startBreak(selectedBreakMinutes) },
                    onSkipBreak = model::stop,
                    currentTask = data.tasks.find { it.id == session?.taskId && it.deletedAt == null && it.status != "DONE" },
                    onFinishTask = { model.done(it, true) }
                )
            } else {
                if (!live) {
                    ModeSelectorPill(currentMode = idleMode, onSelectMode = { idleMode = it })
                }
                ZenDialTimer(
                    live = live,
                    left = left,
                    plannedMs = if (live) state.plannedMs else (if (idleMode == "BREAK") selectedBreakMinutes * 60_000L else config.focus * 60_000L),
                    primaryColor = primaryColor,
                    seconds = seconds,
                    isPaused = isPaused,
                    completedInCycle = state.completedInCycle,
                    isBreak = isBreakMode,
                    maxDialSize = 220.dp
                )
                if (!live) {
                    if (idleMode == "BREAK") {
                        QuickBreakPresetsContent(
                            currentBreak = selectedBreakMinutes,
                            onSelectBreak = { selectedBreakMinutes = it },
                            onCustomBreak = { showCustomBreakDialog = true }
                        )
                        BreakTipCardContent()
                    } else {
                        QuickFocusPresetsContent(
                            currentFocus = config.focus,
                            onSelectFocus = { model.setFocusDuration(it) },
                            onCustomFocus = { showCustomFocusDialog = true }
                        )
                        TaskInfoCardContent(
                            live = live,
                            sessionTitle = session?.title,
                            taskTitle = task?.title,
                            onChoose = { chooser = true }
                        )
                    }
                } else {
                    if (isBreakActive) {
                        BreakTipCardContent()
                    } else {
                        TaskInfoCardContent(
                            live = live,
                            sessionTitle = session?.title,
                            taskTitle = task?.title,
                            onChoose = { chooser = true }
                        )
                    }
                }

                FocusControlButtonsContent(
                    live = live,
                    isRunning = state.status == "RUNNING",
                    isBreak = isBreakActive || (!live && idleMode == "BREAK"),
                    onPauseResume = if (state.status == "RUNNING") model::pause else model::resume,
                    onExtend1m = { model.extendTimer(60_000L) },
                    onExtend5m = { model.extendTimer(300_000L) },
                    onStop = { if (isBreakActive) model.stop() else stop = true },
                    onStart = {
                        if (idleMode == "BREAK") model.startBreak(selectedBreakMinutes)
                        else model.start(task?.id, null)
                    }
                )
            }

            if (isBreakActive) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🍃", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.break_dnd_off_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                DndWarningBannerContent(
                    showDndHint = config.useDnd && access.dndActive,
                    showAlarmWarning = !access.exact
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (stop) AlertDialog(
        onDismissRequest = { stop = false },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.stop_confirm), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.stop_hint), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                onClick = { model.stop(); stop = false }
            ) { Text(stringResource(R.string.stop)) }
        },
        dismissButton = { TextButton(onClick = { stop = false }) { Text(stringResource(R.string.cancel)) } }
    )

    if (chooser) AlertDialog(
        onDismissRequest = { chooser = false },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.choose), style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val tasks = data.tasks.filter { it.deletedAt == null && it.status != "DONE" }
                item {
                    FilledTonalButton(
                        onClick = { onChoose(null); chooser = false },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.no_task_selected))
                    }
                }
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_focus_choices),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.tasks),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(tasks, key = { "task-${it.id}" }) { taskItem ->
                        val subtasks = data.checklists.filter { it.taskId == taskItem.id }
                        OutlinedButton(
                            onClick = { onChoose(taskItem.id); chooser = false },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    taskItem.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (subtasks.isNotEmpty()) {
                                    val doneSubs = subtasks.count { it.isDone }
                                    Text(
                                        "☑ $doneSubs/${subtasks.size} việc con",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { chooser = false }) { Text(stringResource(R.string.close)) } }
    )

    if (showCustomFocusDialog) {
        CustomDurationDialog(
            initial = config.focus,
            title = stringResource(R.string.custom_duration_title),
            onDismiss = { showCustomFocusDialog = false },
            onConfirm = { mins -> model.setFocusDuration(mins) }
        )
    }

    if (showCustomBreakDialog) {
        CustomDurationDialog(
            initial = selectedBreakMinutes,
            title = stringResource(R.string.custom_break_title),
            onDismiss = { showCustomBreakDialog = false },
            onConfirm = { mins -> selectedBreakMinutes = mins }
        )
    }
}

@Composable
private fun CustomDurationDialog(
    initial: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val valid = text.toIntOrNull()?.let { it in 1..180 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    label = { Text(stringResource(R.string.minutes_input_label)) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                onClick = {
                    text.toIntOrNull()?.let { onConfirm(it) }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable fun PermissionCard(access: DeviceAccess, refresh: () -> Unit) {
    val context = LocalContext.current
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    fun open(intent: Intent) { try { context.startActivity(intent) } catch (_: Exception) { Toast.makeText(context, R.string.settings_unavailable, Toast.LENGTH_LONG).show() } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleMedium)
        if (!access.notifications) TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text(stringResource(R.string.notification_permission)) }
        if (!access.exact && Build.VERSION.SDK_INT >= 31) TextButton(onClick = { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.alarm_permission)) }
        if (!access.dndPermission && access.dndSupported) TextButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.dnd_permission)) }
        TextButton(onClick = { open(Intent("android.settings.ZEN_MODE_SETTINGS")) }) { Text(stringResource(R.string.open_dnd)) }
        Text(stringResource(R.string.dnd_details), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

@Composable
private fun PhasePillBadge(
    containerColor: androidx.compose.ui.graphics.Color,
    isBreakActive: Boolean,
    isBreakReady: Boolean,
    isPaused: Boolean,
    live: Boolean
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                when {
                    isBreakActive -> "🍃"
                    isBreakReady -> "☕"
                    isPaused -> "⏸"
                    live -> "🔥"
                    else -> "🍅"
                },
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                stringResource(when {
                    isBreakActive -> R.string.break_in_progress
                    isBreakReady -> R.string.break_ready_title
                    isPaused -> R.string.focus_paused_title
                    live -> R.string.focus_in_progress
                    else -> R.string.focus_ready_title
                }),
                color = when {
                    isBreakActive || isBreakReady -> MaterialTheme.colorScheme.onSecondaryContainer
                    isPaused -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ZenDialTimer(
    live: Boolean,
    left: Long,
    plannedMs: Long,
    primaryColor: androidx.compose.ui.graphics.Color,
    seconds: Long,
    isPaused: Boolean,
    completedInCycle: Int,
    isBreak: Boolean,
    maxDialSize: androidx.compose.ui.unit.Dp
) {
    Box(
        Modifier
            .sizeIn(maxWidth = maxDialSize, maxHeight = maxDialSize)
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val progress = if (live) (left.toFloat() / plannedMs.coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = if (maxDialSize < 220.dp) 8.dp else 10.dp,
            color = primaryColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                style = if (maxDialSize < 220.dp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 4 Cycle Dots
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentInCycle = completedInCycle % 4
                for (i in 0..3) {
                    val dotFilled = i < currentInCycle
                    val dotCurrent = i == currentInCycle && live && !isBreak
                    Box(
                        modifier = Modifier
                            .size(if (maxDialSize < 220.dp) 8.dp else 10.dp)
                            .background(
                                color = when {
                                    dotFilled -> MaterialTheme.colorScheme.primary
                                    dotCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
            Text(
                stringResource(R.string.cycle_count, (completedInCycle % 4) + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun ModeSelectorPill(
    currentMode: String,
    onSelectMode: (String) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isFocus = currentMode == "FOCUS"
            Surface(
                onClick = { onSelectMode("FOCUS") },
                shape = CircleShape,
                color = if (isFocus) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🍅", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.mode_focus),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isFocus) FontWeight.Bold else FontWeight.Medium,
                        color = if (isFocus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                onClick = { onSelectMode("BREAK") },
                shape = CircleShape,
                color = if (!isFocus) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("☕", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.mode_break),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (!isFocus) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isFocus) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFocusPresetsContent(
    currentFocus: Int,
    onSelectFocus: (Int) -> Unit,
    onCustomFocus: () -> Unit
) {
    val focusPresets = listOf(15, 25, 45, 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        focusPresets.forEach { mins ->
            val selected = currentFocus == mins
            FilterChip(
                selected = selected,
                onClick = { onSelectFocus(mins) },
                shape = RoundedCornerShape(12.dp),
                colors = chipColors,
                label = { Text("${mins}p", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
            )
        }
        val isCustomFocus = currentFocus !in focusPresets
        FilterChip(
            selected = isCustomFocus,
            onClick = onCustomFocus,
            shape = RoundedCornerShape(12.dp),
            colors = chipColors,
            label = { Text(if (isCustomFocus) "${currentFocus}p" else stringResource(R.string.custom_minutes), maxLines = 1) }
        )
    }
}

@Composable
private fun QuickBreakPresetsContent(
    currentBreak: Int,
    onSelectBreak: (Int) -> Unit,
    onCustomBreak: () -> Unit
) {
    val breakPresets = listOf(3, 5, 10, 15)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        breakPresets.forEach { mins ->
            val selected = currentBreak == mins
            FilterChip(
                selected = selected,
                onClick = { onSelectBreak(mins) },
                shape = RoundedCornerShape(12.dp),
                colors = chipColors,
                label = {
                    Text(
                        "${mins}p",
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            )
        }
        val isCustomBreak = currentBreak !in breakPresets
        FilterChip(
            selected = isCustomBreak,
            onClick = onCustomBreak,
            shape = RoundedCornerShape(12.dp),
            colors = chipColors,
            label = { Text(if (isCustomBreak) "${currentBreak}p" else stringResource(R.string.custom_minutes), maxLines = 1) }
        )
    }
}

@Composable
private fun BreakTipCardContent() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("☕", fontSize = 22.sp)
            Text(
                text = stringResource(R.string.break_phase_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TaskInfoCardContent(
    live: Boolean,
    sessionTitle: String?,
    taskTitle: String?,
    onChoose: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (live) {
                        sessionTitle ?: stringResource(R.string.break_phase)
                    } else {
                        taskTitle ?: stringResource(R.string.no_task_selected)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!live) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onChoose,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(if (taskTitle != null) R.string.change else R.string.choose), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(
                text = if (live) stringResource(R.string.task_in_progress) else stringResource(R.string.select_task_pill),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusControlButtonsContent(
    live: Boolean,
    isRunning: Boolean,
    isBreak: Boolean,
    onPauseResume: () -> Unit,
    onExtend1m: () -> Unit,
    onExtend5m: () -> Unit,
    onStop: () -> Unit,
    onStart: () -> Unit
) {
    if (live) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPauseResume,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) {
                        if (isBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.tertiary,
                    contentColor = if (isRunning) {
                        if (isBreak) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    } else MaterialTheme.colorScheme.onTertiary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    (if (isRunning) "⏸  " else "▶  ") + stringResource(if (isRunning) R.string.pause else R.string.resume),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isBreak) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onExtend1m,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(
                            "⏱  " + stringResource(R.string.extend_1_minute),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    ) {
                        Text(
                            "⏭  " + stringResource(R.string.skip_break),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onExtend1m,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            stringResource(R.string.extend_1_minute),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilledTonalButton(
                        onClick = onExtend5m,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(
                            stringResource(R.string.extend_5_minute),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Text(
                        "⏹  " + stringResource(R.string.stop),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        Button(
            enabled = true,
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(
                if (isBreak) "☕  " + stringResource(R.string.start_break) else stringResource(R.string.start_focus),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BreakCardContent(
    selectedBreakMinutes: Int,
    onSelectBreak: (Int) -> Unit,
    onCustomBreak: () -> Unit,
    onStartBreak: () -> Unit,
    onSkipBreak: () -> Unit,
    currentTask: TaskItem?,
    onFinishTask: (TaskItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🎉", fontSize = 40.sp)
            Text(
                stringResource(R.string.ready_break),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                stringResource(R.string.break_offer, selectedBreakMinutes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            val breakPresets = listOf(3, 5, 10, 15)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breakPresets.forEach { mins ->
                    val selected = selectedBreakMinutes == mins
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectBreak(mins) },
                        shape = RoundedCornerShape(10.dp),
                        label = { Text("${mins}p", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
                val isCustomBreak = selectedBreakMinutes !in breakPresets
                FilterChip(
                    selected = isCustomBreak,
                    onClick = onCustomBreak,
                    shape = RoundedCornerShape(10.dp),
                    label = { Text(if (isCustomBreak) "${selectedBreakMinutes}p" else stringResource(R.string.custom_minutes)) }
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onStartBreak,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(
                    "☕  " + stringResource(R.string.start_break),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = onSkipBreak) {
                Text(stringResource(R.string.skip_break), style = MaterialTheme.typography.labelLarge)
            }
            currentTask?.let { current ->
                OutlinedButton(
                    onClick = { onFinishTask(current) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✓  " + stringResource(R.string.finish_task), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DndWarningBannerContent(
    showDndHint: Boolean,
    showAlarmWarning: Boolean
) {
    if (showDndHint || showAlarmWarning) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (showAlarmWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (showAlarmWarning) "⚠️" else "🛡️", style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (showDndHint) {
                        Text(
                            stringResource(R.string.dnd_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showAlarmWarning) {
                        Text(
                            stringResource(R.string.alarm_hint),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
