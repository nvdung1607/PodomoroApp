package com.trustMePro.podomoroapp.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustMePro.podomoroapp.AppViewModel
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun formatFocusDuration(ms: Long): String {
    val totalMins = ms / 60_000
    val hours = totalMins / 60
    val mins = totalMins % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}p"
        hours > 0 -> "${hours}h"
        else -> "${mins} phút"
    }
}

@Composable fun StatsScreen(data: StoreSnapshot) {
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val period = Period.entries[periodIndex]
    val date = LocalDate.parse(dateText)
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val report = remember(data, date, period, zone) { Statistics.report(data, date, period, zone) }
    val streak = remember(data, today) { Statistics.dailyStreak(data, today, zone) }
    val weeklyStats = remember(data, today) { Statistics.weeklyPomodoroStats(data, today, zone) }
    var selectedCalendarMonth by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }
    val currentMonth = java.time.YearMonth.parse(selectedCalendarMonth)
    val monthHeatmap = remember(data, currentMonth) { Statistics.monthHeatmap(data, currentMonth, zone) }

    val (start, end) = Statistics.bounds(date, period)
    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = end.atStartOfDay(zone).toInstant().toEpochMilli()
    val tasks = data.tasks.filter { it.deletedAt == null }
    val history = data.sessions.filter { (it.endedAt ?: it.startedAt) in from until to }

    fun move(amount: Long) {
        dateText = when (period) {
            Period.DAY -> date.plusDays(amount)
            Period.WEEK -> date.plusWeeks(amount)
            Period.MONTH -> date.plusMonths(amount)
        }.toString()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle(stringResource(R.string.stats)) }

        // Period filter chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.day, R.string.week, R.string.month).forEachIndexed { index, label ->
                    FilterChip(
                        selected = index == periodIndex,
                        onClick = { periodIndex = index },
                        shape = RoundedCornerShape(14.dp),
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        // Period navigation bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { move(-1) },
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        val centerLabel = when (period) {
                            Period.DAY -> if (date == today) stringResource(R.string.today) else date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            Period.WEEK -> if (today in start..<end) stringResource(R.string.this_week) else (start.format(DateTimeFormatter.ofPattern("dd/MM")) + " - " + end.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM")))
                            Period.MONTH -> if (date.year == today.year && date.month == today.month) stringResource(R.string.this_month) else date.format(DateTimeFormatter.ofPattern("MM/yyyy"))
                        }
                        TextButton(onClick = { dateText = LocalDate.now().toString() }) {
                            Text(centerLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalIconButton(
                            onClick = { move(1) },
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        stringResource(R.string.today_summary, report.completed, report.focusMs / 60000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
                    )
                }
            }
        }

        // Hero Metric Cards (4 metrics, responsive for landscape)
        if (isLandscape) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("🍅 ${report.completed}", R.string.pomodoros, Modifier.weight(1f))
                    MetricCard("⏱ ${formatFocusDuration(report.focusMs)}", R.string.stats_total_hours, Modifier.weight(1f))
                    MetricCard("🔥 $streak", R.string.stats_current_streak, Modifier.weight(1f))
                    MetricCard("✓ ${report.completedTasks}", R.string.completed_tasks, Modifier.weight(1f))
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("🍅 ${report.completed}", R.string.pomodoros, Modifier.weight(1f))
                    MetricCard("⏱ ${formatFocusDuration(report.focusMs)}", R.string.stats_total_hours, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("🔥 $streak", R.string.stats_current_streak, Modifier.weight(1f))
                    MetricCard("✓ ${report.completedTasks}", R.string.completed_tasks, Modifier.weight(1f))
                }
            }
        }

        // 1. Biểu đồ 7 ngày gần nhất (Weekly Bar Chart)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.daily_chart),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    WeeklyBarChartView(weeklyStats, today)
                }
            }
        }

        // 2. Lịch Pomodoro Heatmap tương tác (Interactive Calendar Heatmap)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.stats_calendar) + " (" + currentMonth.format(DateTimeFormatter.ofPattern("MM/yyyy")) + ")",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = { selectedCalendarMonth = currentMonth.minusMonths(1).toString() },
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape
                            ) {
                                Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalIconButton(
                                onClick = { selectedCalendarMonth = currentMonth.plusMonths(1).toString() },
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape
                            ) {
                                Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    CalendarHeatmapView(currentMonth, monthHeatmap, today)
                }
            }
        }

        // Current tasks summary with visual breakdown
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.current_tasks),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val todoCount = tasks.count { it.status == "TODO" }
                    val inProgressCount = tasks.count { it.status == "IN_PROGRESS" }
                    val doneCount = tasks.count { it.status == "DONE" }
                    val totalTasks = tasks.size.coerceAtLeast(1)

                    Row(
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (doneCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(doneCount.toFloat() / totalTasks)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            )
                        }
                        if (inProgressCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(inProgressCount.toFloat() / totalTasks)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            )
                        }
                        if (todoCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(todoCount.toFloat() / totalTasks)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chờ: $todoCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Đang làm: $inProgressCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("Đã xong: $doneCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    val overdueCount = tasks.count { it.status != "DONE" && it.dueDate?.let { d -> d < today.toString() } == true }
                    if (overdueCount > 0) {
                        Text(
                            stringResource(R.string.overdue_count, overdueCount),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // History Sessions List
        item {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (history.isEmpty()) {
            item {
                Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(history, key = { it.id }) { session ->
            val isCompleted = session.status == "COMPLETED"
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val (badgeBg, badgeFg, statusLabel) = when (session.status) {
                            "COMPLETED" -> Triple(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                                R.string.session_completed
                            )
                            "RUNNING" -> Triple(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                R.string.session_running
                            )
                            "ABORTED" -> Triple(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                R.string.session_aborted
                            )
                            else -> Triple(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                R.string.session_interrupted
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeBg
                        ) {
                            Text(
                                stringResource(statusLabel),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = badgeFg
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            Instant.ofEpochMilli(session.startedAt).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.minutes_value, session.activeMs / 60000),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun MetricCard(value: String, label: Int, modifier: Modifier) {
    Card(
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun WeeklyBarChartView(stats: List<DailyPomodoroStat>, today: LocalDate) {
    val maxMinutes = (stats.maxOfOrNull { it.focusMs / 60000 } ?: 0L).coerceAtLeast(60L)
    val dayLabels = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun)

    Row(
        modifier = Modifier.fillMaxWidth().height(148.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEachIndexed { index, item ->
            val isToday = item.date == today
            val minutes = item.focusMs / 60000

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                if (minutes > 0) {
                    Text(
                        "${minutes}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                } else if (item.pomodoroCount > 0) {
                    Text(
                        "${item.pomodoroCount}🍅",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(Modifier.height(14.dp))
                }
                Spacer(Modifier.height(4.dp))
                val hasActivity = minutes > 0 || item.pomodoroCount > 0
                val barHeight = if (hasActivity) (85 * (minutes.toFloat() / maxMinutes).coerceIn(0.08f, 1f)).dp else 4.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (hasActivity) 0.5f else 0.25f)
                        .height(barHeight)
                        .background(
                            color = when {
                                isToday && hasActivity -> MaterialTheme.colorScheme.primary
                                hasActivity -> MaterialTheme.colorScheme.primaryContainer
                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            },
                            shape = if (hasActivity) RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp) else CircleShape
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(dayLabels.getOrElse(index) { R.string.mon }),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable private fun CalendarHeatmapView(
    yearMonth: java.time.YearMonth,
    heatmap: Map<LocalDate, Int>,
    today: LocalDate
) {
    var selectedDateInfo by remember { mutableStateOf<Pair<LocalDate, Int>?>(null) }
    val dayHeaders = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun)
    val firstDayOfMonth = yearMonth.atDay(1)
    val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 (Mon) to 7 (Sun)
    val daysInMonth = yearMonth.lengthOfMonth()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Thứ header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            dayHeaders.forEach { headerRes ->
                Text(
                    stringResource(headerRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Calendar grid
        val totalCells = (startDayOfWeek - 1) + daysInMonth
        val rows = (totalCells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in 1..7) {
                    val cellIndex = r * 7 + c
                    val dayNum = cellIndex - (startDayOfWeek - 1)
                    if (dayNum in 1..daysInMonth) {
                        val cellDate = yearMonth.atDay(dayNum)
                        val count = heatmap[cellDate] ?: 0
                        val isCurrentDay = cellDate == today
                        val isSelected = selectedDateInfo?.first == cellDate

                        val cellBg = when {
                            count >= 5 -> MaterialTheme.colorScheme.primary
                            count in 3..4 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            count in 1..2 -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        val textColor = when {
                            count >= 3 -> MaterialTheme.colorScheme.onPrimary
                            count in 1..2 -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val cellModifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .background(
                                color = cellBg,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .let {
                                if (isSelected) it.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else if (isCurrentDay) it.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                else it
                            }
                            .clickable {
                                selectedDateInfo = cellDate to count
                            }

                        Column(
                            modifier = cellModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrentDay || count > 0) FontWeight.Bold else FontWeight.Normal,
                                color = textColor
                            )
                            if (count > 0) {
                                Text(
                                    text = "${count}🍅",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                    }
                }
            }
        }

        // Tap info banner
        selectedDateInfo?.let { (selDate, selCount) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📅", fontSize = 16.sp)
                    Text(
                        text = "${selDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}: " +
                                if (selCount > 0) "$selCount Pomodoro (${selCount * 25}p)" else "Chưa có phiên tập trung",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable fun SettingsScreen(config: AppSettings, access: DeviceAccess, model: AppViewModel) {
    var focus by rememberSaveable(config.focus) { mutableStateOf(config.focus.toString()) }
    var short by rememberSaveable(config.shortBreak) { mutableStateOf(config.shortBreak.toString()) }
    var long by rememberSaveable(config.longBreak) { mutableStateOf(config.longBreak.toString()) }
    var target by rememberSaveable(config.dailyTarget) { mutableStateOf(config.dailyTarget.toString()) }
    var useDnd by rememberSaveable(config.useDnd) { mutableStateOf(config.useDnd) }
    var theme by rememberSaveable(config.theme) { mutableStateOf(config.theme) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri) } }
    val exportPrevious = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri, true) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::prepareRestore) }
    val valid = listOf(focus, short, long).all { it.toIntOrNull()?.let { n -> n in 1..180 } == true } && target.toIntOrNull()?.let { it in 1..100 } == true

    fun applySettings(f: String = focus, s: String = short, l: String = long, t: String = target, dnd: Boolean = useDnd, th: String = theme) {
        val fVal = f.toIntOrNull()
        val sVal = s.toIntOrNull()
        val lVal = l.toIntOrNull()
        val tVal = t.toIntOrNull()
        if (fVal != null && fVal in 1..180 && sVal != null && sVal in 1..180 && lVal != null && lVal in 1..180 && tVal != null && tVal in 1..100) {
            model.saveSettings(AppSettings(fVal, sVal, lVal, tVal, dnd, th))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Nhóm Giao diện (Theme Mode)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.theme_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            "SYSTEM" to R.string.theme_system,
                            "LIGHT" to R.string.theme_light,
                            "DARK" to R.string.theme_dark
                        )
                        themes.forEach { (mode, labelRes) ->
                            val isSelected = theme == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    theme = mode
                                    applySettings(th = mode)
                                },
                                shape = RoundedCornerShape(12.dp),
                                label = {
                                    Text(
                                        stringResource(labelRes),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Nhóm Thời lượng
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.timing_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TimingSettingRow(R.string.focus_minutes, focus, { focus = it; applySettings(f = it) }, listOf(15, 25, 45, 60))
                    TimingSettingRow(R.string.short_minutes, short, { short = it; applySettings(s = it) }, listOf(3, 5, 10))
                    TimingSettingRow(R.string.long_minutes, long, { long = it; applySettings(l = it) }, listOf(15, 20, 30))
                    NumberField(target, { target = it; applySettings(t = it) }, R.string.daily_target)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.use_dnd), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(useDnd, { useDnd = it; applySettings(dnd = it) })
                    }
                    if (useDnd) {
                        Text(stringResource(R.string.dnd_ott_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { PermissionCard(access, model::refresh) }

        // Nhóm Sao lưu & Khôi phục
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(R.string.backup), stringResource(R.string.backup_hint))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { export.launch("podomoro-${LocalDate.now()}.json") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("📤 " + stringResource(R.string.export_backup), style = MaterialTheme.typography.labelLarge)
                        }
                        FilledTonalButton(
                            onClick = { import.launch(arrayOf("application/json", "text/plain")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("📥 " + stringResource(R.string.import_backup), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    TextButton(
                        onClick = { exportPrevious.launch("podomoro-before-restore.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_previous), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable private fun NumberField(value: String, change: (String) -> Unit, label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable private fun TimingSettingRow(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    presets: List<Int>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(value, onValueChange, label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val selected = value == preset.toString()
                FilterChip(
                    selected = selected,
                    onClick = { onValueChange(preset.toString()) },
                    shape = RoundedCornerShape(10.dp),
                    label = {
                        Text(
                            "${preset}p",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    }
}
