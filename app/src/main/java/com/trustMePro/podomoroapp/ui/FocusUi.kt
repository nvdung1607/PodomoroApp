package com.trustMePro.podomoroapp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustMePro.podomoroapp.AppViewModel
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import java.util.Locale

@Composable fun FocusScreen(data: StoreSnapshot, config: AppSettings, access: DeviceAccess, elapsed: Long, selectedTask: String?, selectedGoal: String?, onChoose: (String?, String?) -> Unit, model: AppViewModel) {
    var chooser by rememberSaveable { mutableStateOf(false) }; var stop by rememberSaveable { mutableStateOf(false) }
    val state = data.timer
    val live = state.status in listOf("RUNNING", "PAUSED")
    val session = data.sessions.find { it.id == state.sessionId }
    val task = data.tasks.find { it.id == selectedTask && it.deletedAt == null && it.status != "DONE" }
    val goal = data.goals.find { it.id == selectedGoal && it.deletedAt == null && it.status == "ACTIVE" }
    val left = if (live) TimerRules.remaining(state, elapsed) else config.focus * 60_000L
    val seconds = (left + 999) / 1000
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(if (live && state.phase == "BREAK") R.string.break_phase else R.string.focus_phase), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        if (state.status == "AWAITING_BREAK") {
            EmptyCard(stringResource(R.string.ready_break), stringResource(R.string.break_offer, state.breakMinutes))
            Button(onClick = model::startBreak, Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_break)) }
            TextButton(onClick = model::stop) { Text(stringResource(R.string.skip_break)) }
            data.tasks.find { it.id == session?.taskId && it.deletedAt == null && it.status != "DONE" }?.let { current -> OutlinedButton(onClick = { model.done(current, true) }) { Text(stringResource(R.string.finish_task)) } }
        } else {
            Box(Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 280.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { if (live) (left.toFloat() / state.plannedMs).coerceIn(0f, 1f) else 1f }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), fontSize = 52.sp, fontWeight = FontWeight.Light)
                    if (state.status == "PAUSED") Text(stringResource(R.string.paused))
                    Text(stringResource(R.string.cycle_count, state.completedInCycle), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(if (live) session?.title ?: stringResource(R.string.break_phase) else task?.title ?: goal?.title ?: stringResource(R.string.choose_work), style = MaterialTheme.typography.titleLarge)
            if (live) {
                Button(onClick = if (state.status == "RUNNING") model::pause else model::resume, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (state.status == "RUNNING") R.string.pause else R.string.resume)) }
                TextButton(onClick = { if (state.phase == "BREAK") model.stop() else stop = true }) { Text(stringResource(if (state.phase == "BREAK") R.string.skip_break else R.string.stop)) }
            } else {
                OutlinedButton(onClick = { chooser = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose)) }
                Button(enabled = task != null || goal != null, onClick = { model.start(task?.id, if (task == null) goal?.id else null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_focus)) }
            }
        }
        Text(stringResource(when { !config.useDnd -> R.string.dnd_disabled; !access.dndSupported -> R.string.dnd_old; access.dndActive -> R.string.dnd_active; access.dndPermission -> R.string.dnd_ready; else -> R.string.dnd_missing }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!access.exact) Text(stringResource(R.string.alarm_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        PermissionCard(access, model::refresh)
        Spacer(Modifier.height(8.dp))
    }
    if (stop) AlertDialog(onDismissRequest = { stop = false }, title = { Text(stringResource(R.string.stop_confirm)) }, text = { Text(stringResource(R.string.stop_hint)) }, confirmButton = { TextButton(onClick = { model.stop(); stop = false }) { Text(stringResource(R.string.stop)) } }, dismissButton = { TextButton(onClick = { stop = false }) { Text(stringResource(R.string.cancel)) } })
    if (chooser) AlertDialog(onDismissRequest = { chooser = false }, title = { Text(stringResource(R.string.choose)) }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            val tasks = data.tasks.filter { it.deletedAt == null && it.status != "DONE" }; val goals = data.goals.filter { it.deletedAt == null && it.status == "ACTIVE" }
            if (tasks.isEmpty() && goals.isEmpty()) item { Text(stringResource(R.string.no_focus_choices)) }
            if (tasks.isNotEmpty()) item { Text(stringResource(R.string.tasks), color = MaterialTheme.colorScheme.primary) }
            items(tasks, key = { "task-${it.id}" }) { task -> TextButton(onClick = { onChoose(task.id, null); chooser = false }) { Text(task.title) } }
            if (goals.isNotEmpty()) item { Text(stringResource(R.string.goals), color = MaterialTheme.colorScheme.primary) }
            items(goals, key = { "goal-${it.id}" }) { goal -> TextButton(onClick = { onChoose(null, goal.id); chooser = false }) { Text(goal.title) } }
        }
    }, confirmButton = { TextButton(onClick = { chooser = false }) { Text(stringResource(R.string.close)) } })
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
