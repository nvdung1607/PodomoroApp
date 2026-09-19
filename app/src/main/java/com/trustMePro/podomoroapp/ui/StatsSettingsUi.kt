package com.trustMePro.podomoroapp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trustMePro.podomoroapp.AppViewModel
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun StatsScreen(data: StoreSnapshot) {
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }; var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var goal by rememberSaveable { mutableStateOf<String?>(null) }; var menu by remember { mutableStateOf(false) }
    val period = Period.entries[periodIndex]; val date = LocalDate.parse(dateText); val zone = ZoneId.systemDefault()
    val report = remember(data, date, period, goal, zone) { Statistics.report(data, date, period, zone, goal) }
    val (start, end) = Statistics.bounds(date, period)
    val from = start.atStartOfDay(zone).toInstant().toEpochMilli(); val to = end.atStartOfDay(zone).toInstant().toEpochMilli()
    val tasks = data.tasks.filter { it.deletedAt == null && (goal == null || it.goalId == goal) }
    val history = data.sessions.filter { (goal == null || it.goalId == goal) && (it.endedAt ?: it.startedAt) in from until to }
    fun move(amount: Long) { dateText = when (period) { Period.DAY -> date.plusDays(amount); Period.WEEK -> date.plusWeeks(amount); Period.MONTH -> date.plusMonths(amount) }.toString() }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(stringResource(R.string.stats)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(R.string.day, R.string.week, R.string.month).forEachIndexed { index, label -> FilterChip(selected = index == periodIndex, onClick = { periodIndex = index }, label = { Text(stringResource(label)) }) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { move(-1) }) { Text(stringResource(R.string.previous)) }; TextButton(onClick = { dateText = LocalDate.now().toString() }) { Text(stringResource(R.string.today)) }; TextButton(onClick = { move(1) }) { Text(stringResource(R.string.next)) }
        }; Text(stringResource(R.string.period_range, start.toString(), end.minusDays(1).toString()), style = MaterialTheme.typography.titleMedium) }
        item { Box { OutlinedButton(onClick = { menu = true }) { Text(data.goals.find { it.id == goal }?.title ?: stringResource(R.string.all)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.all)) }, onClick = { goal = null; menu = false })
                data.goals.forEach { g -> DropdownMenuItem(text = { Text(g.title) }, onClick = { goal = g.id; menu = false }) }
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric(report.completed.toString(), R.string.pomodoros, Modifier.weight(1f)); Metric((report.focusMs / 60000).toString(), R.string.focus_time, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric(report.completedTasks.toString(), R.string.completed_tasks, Modifier.weight(1f)); Metric(report.interrupted.toString(), R.string.aborted, Modifier.weight(1f)) } }
        item { Text(stringResource(R.string.current_tasks), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.task_summary, tasks.count { it.status == "TODO" }, tasks.count { it.status == "IN_PROGRESS" }, tasks.count { it.status == "DONE" })) }
        item { Text(stringResource(R.string.overdue_count, tasks.count { it.status != "DONE" && it.dueDate?.let { day -> day < LocalDate.now().toString() } == true }), color = MaterialTheme.colorScheme.error) }
        item { Text(stringResource(R.string.daily_chart), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val max = (report.daily.maxOfOrNull { it.second } ?: 0).coerceAtLeast(60_000)
                report.daily.forEach { (day, duration) -> Column(Modifier.width(44.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text((duration / 60000).toString(), style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.height(100.dp).width(22.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                        Surface(Modifier.fillMaxWidth().height((100f * duration / max).coerceAtLeast(2f).dp), color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {}
                    }
                    Text(day.format(DateTimeFormatter.ofPattern("dd/MM")), style = MaterialTheme.typography.labelSmall)
                } }
            }
        }
        item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge) }
        if (history.isEmpty()) item { Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history, key = { it.id }) { session -> Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            session.goalTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            Text(Instant.ofEpochMilli(session.startedAt).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")))
            Text(stringResource(R.string.minutes_value, session.activeMs / 60000) + " · " + stringResource(when (session.status) { "COMPLETED" -> R.string.session_completed; "ABORTED" -> R.string.session_aborted; "INTERRUPTED" -> R.string.session_interrupted; else -> R.string.session_running }), style = MaterialTheme.typography.bodySmall)
        } } }
    }
}
@Composable private fun Metric(value: String, label: Int, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary); Text(stringResource(label), style = MaterialTheme.typography.bodySmall) } } }

@Composable fun SettingsScreen(config: AppSettings, access: DeviceAccess, model: AppViewModel) {
    var focus by rememberSaveable(config.focus) { mutableStateOf(config.focus.toString()) }; var short by rememberSaveable(config.shortBreak) { mutableStateOf(config.shortBreak.toString()) }
    var long by rememberSaveable(config.longBreak) { mutableStateOf(config.longBreak.toString()) }; var target by rememberSaveable(config.dailyTarget) { mutableStateOf(config.dailyTarget.toString()) }
    var useDnd by rememberSaveable(config.useDnd) { mutableStateOf(config.useDnd) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri) } }
    val exportPrevious = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri, true) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::prepareRestore) }
    val valid = listOf(focus, short, long).all { it.toIntOrNull()?.let { n -> n in 1..180 } == true } && target.toIntOrNull()?.let { it in 1..100 } == true
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(stringResource(R.string.settings), stringResource(R.string.privacy_note)) }
        item { Text(stringResource(R.string.timing_settings), style = MaterialTheme.typography.titleLarge) }
        item { NumberField(focus, { focus = it }, R.string.focus_minutes) }; item { NumberField(short, { short = it }, R.string.short_minutes) }
        item { NumberField(long, { long = it }, R.string.long_minutes) }; item { NumberField(target, { target = it }, R.string.daily_target) }
        item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(stringResource(R.string.use_dnd), Modifier.weight(1f)); Switch(useDnd, { useDnd = it }) }; Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodySmall) }
        item { Button(enabled = valid, onClick = { model.saveSettings(AppSettings(focus.toInt(), short.toInt(), long.toInt(), target.toInt(), useDnd)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) } }
        item { PermissionCard(access, model::refresh) }
        item { HorizontalDivider(); SectionTitle(stringResource(R.string.backup), stringResource(R.string.backup_hint)) }
        item { OutlinedButton(onClick = { export.launch("nhip-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_backup)) } }
        item { OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_backup)) } }
        item { TextButton(onClick = { exportPrevious.launch("nhip-before-restore.json") }) { Text(stringResource(R.string.export_previous)) } }
    }
}
@Composable private fun NumberField(value: String, change: (String) -> Unit, label: Int) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
}
