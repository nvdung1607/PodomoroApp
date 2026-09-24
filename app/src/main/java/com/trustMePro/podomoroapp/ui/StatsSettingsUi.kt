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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable fun StatsScreen(data: StoreSnapshot, settings: AppSettings = AppSettings()) {
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val period = Period.entries[periodIndex]
    val date = LocalDate.parse(dateText)
    val zone = ZoneId.systemDefault()
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = LocalDate.now()
                if (today != now) today = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val report = remember(data, date, period, zone) { Statistics.report(data, date, period, zone) }
    val streak = remember(data, today) { Statistics.dailyStreak(data, today, zone) }
    val weeklyStats = remember(data, date, zone) { Statistics.weeklyPomodoroStats(data, date, zone) }
    val todayReport = remember(data, today, zone) { Statistics.report(data, today, Period.DAY, zone) }
    val todayCompleted = todayReport.completed
    val target = settings.dailyTarget.coerceAtLeast(1)
    val targetRatio = (todayCompleted.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val targetPercent = (todayCompleted * 100) / target
    var selectedCalendarMonth by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }
    val currentMonth = java.time.YearMonth.parse(selectedCalendarMonth)
    val monthHeatmap = remember(data, currentMonth) { Statistics.monthHeatmapStats(data, currentMonth, zone) }

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
                        val prevDesc = stringResource(R.string.previous_period)
                        val nextDesc = stringResource(R.string.next_period)
                        FilledTonalIconButton(
                            onClick = { move(-1) },
                            modifier = Modifier.size(36.dp).semantics { contentDescription = prevDesc },
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
                            modifier = Modifier.size(36.dp).semantics { contentDescription = nextDesc },
                            shape = CircleShape
                        ) {
                            Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val summaryText = when (period) {
                        Period.DAY -> if (date == today) {
                            stringResource(R.string.today_summary, report.completed, report.focusMs / 60000)
                        } else {
                            stringResource(R.string.day_summary, report.completed, report.focusMs / 60000)
                        }
                        Period.WEEK, Period.MONTH -> stringResource(R.string.period_summary, report.completed, report.focusMs / 60000)
                    }
                    Text(
                        summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
                    )
                }
            }
        }

        // Daily Target Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (todayCompleted >= target)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fontScale = LocalDensity.current.fontScale
                    if (fontScale >= 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🎯", fontSize = 18.sp)
                                Text(
                                    stringResource(R.string.daily_target_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                stringResource(R.string.daily_target_status, todayCompleted, target, targetPercent),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text("🎯", fontSize = 18.sp)
                                Text(
                                    stringResource(R.string.daily_target_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.daily_target_status, todayCompleted, target, targetPercent),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                softWrap = false
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { targetRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    val hintText = if (todayCompleted >= target) {
                        stringResource(R.string.daily_target_reached)
                    } else {
                        stringResource(R.string.daily_target_keep_going, target - todayCompleted)
                    }
                    Text(
                        hintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    val isCurrentWeek = weeklyStats.isNotEmpty() && today in weeklyStats.first().date..weeklyStats.last().date
                    val chartTitle = if (isCurrentWeek) {
                        stringResource(R.string.daily_chart) + " (" + stringResource(R.string.this_week) + ")"
                    } else if (weeklyStats.isNotEmpty()) {
                        stringResource(R.string.daily_chart) + " (" + weeklyStats.first().date.format(DateTimeFormatter.ofPattern("dd/MM")) + " - " + weeklyStats.last().date.format(DateTimeFormatter.ofPattern("dd/MM")) + ")"
                    } else {
                        stringResource(R.string.daily_chart)
                    }
                    Text(
                        chartTitle,
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
                                modifier = Modifier.size(32.dp).semantics { contentDescription = "Tháng trước" },
                                shape = CircleShape
                            ) {
                                Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalIconButton(
                                onClick = { selectedCalendarMonth = currentMonth.plusMonths(1).toString() },
                                modifier = Modifier.size(32.dp).semantics { contentDescription = "Tháng sau" },
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
    heatmap: Map<LocalDate, DayHeatmapStat>,
    today: LocalDate
) {
    var selectedDateInfo by remember { mutableStateOf<Pair<LocalDate, DayHeatmapStat>?>(null) }
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
                        val stat = heatmap[cellDate] ?: DayHeatmapStat(0, 0L)
                        val count = stat.pomodoroCount
                        val minutes = stat.focusMs / 60000
                        val isCurrentDay = cellDate == today
                        val isSelected = selectedDateInfo?.first == cellDate

                        val cellBg = when {
                            count >= 5 -> MaterialTheme.colorScheme.primary
                            count in 3..4 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            count in 1..2 -> MaterialTheme.colorScheme.primaryContainer
                            minutes > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        val textColor = when {
                            count >= 3 -> MaterialTheme.colorScheme.onPrimary
                            count in 1..2 || minutes > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
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
                                selectedDateInfo = cellDate to stat
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
                                fontWeight = if (isCurrentDay || count > 0 || minutes > 0) FontWeight.Bold else FontWeight.Normal,
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
                            } else if (minutes > 0) {
                                Text(
                                    text = "${minutes}p",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
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
        selectedDateInfo?.let { (selDate, selStat) ->
            val selCount = selStat.pomodoroCount
            val selMins = selStat.focusMs / 60000
            val infoText = when {
                selCount > 0 && selMins > 0 -> "$selCount Pomodoro (${selMins}p)"
                selCount > 0 -> "$selCount Pomodoro"
                selMins > 0 -> "$selMins phút tập trung"
                else -> "Chưa có phiên tập trung"
            }
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
                        text = "${selDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}: $infoText",
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
    val user by model.user.collectAsState()
    val syncStatus by model.syncStatus.collectAsState()
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri) } }
    val exportPrevious = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri, true) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::prepareRestore) }
    val focusInt = focus.toIntOrNull()
    val focusError = focusInt == null || focusInt !in 1..180
    val shortInt = short.toIntOrNull()
    val shortError = shortInt == null || shortInt !in 1..180
    val longInt = long.toIntOrNull()
    val longError = longInt == null || longInt !in 1..180
    val targetInt = target.toIntOrNull()
    val targetError = targetInt == null || targetInt !in 1..100

    if (showAuthDialog) {
        AuthDialog(model = model, onDismiss = { showAuthDialog = false })
    }

    fun applySettings(f: String = focus, s: String = short, l: String = long, t: String = target, dnd: Boolean = useDnd, th: String = theme) {
        val fVal = f.toIntOrNull()?.takeIf { it in 1..180 } ?: config.focus
        val sVal = s.toIntOrNull()?.takeIf { it in 1..180 } ?: config.shortBreak
        val lVal = l.toIntOrNull()?.takeIf { it in 1..180 } ?: config.longBreak
        val tVal = t.toIntOrNull()?.takeIf { it in 1..100 } ?: config.dailyTarget
        model.saveSettings(config.copy(focus = fVal, shortBreak = sVal, longBreak = lVal, dailyTarget = tVal, useDnd = dnd, theme = th))
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
                    TimingSettingRow(
                        R.string.focus_minutes, focus, { focus = it; applySettings(f = it) }, listOf(15, 25, 45, 60),
                        isError = focusError, errorMessage = if (focusError) "Từ 1 đến 180 phút" else null
                    )
                    TimingSettingRow(
                        R.string.short_minutes, short, { short = it; applySettings(s = it) }, listOf(3, 5, 10),
                        isError = shortError, errorMessage = if (shortError) "Từ 1 đến 180 phút" else null
                    )
                    TimingSettingRow(
                        R.string.long_minutes, long, { long = it; applySettings(l = it) }, listOf(15, 20, 30),
                        isError = longError, errorMessage = if (longError) "Từ 1 đến 180 phút" else null
                    )
                    NumberField(
                        target, { target = it; applySettings(t = it) }, R.string.daily_target,
                        isError = targetError, errorMessage = if (targetError) "Từ 1 đến 100 Pomodoro" else null
                    )

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

        // Nhóm Tài khoản & Đồng bộ Đám mây (Firebase 0đ)
        item {
            AccountSyncCard(
                user = user,
                syncStatus = syncStatus,
                onOpenAuth = { showAuthDialog = true },
                onSyncNow = model::manualSync,
                onLogout = model::logout
            )
        }

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

@Composable
private fun AccountSyncCard(
    user: com.google.firebase.auth.FirebaseUser?,
    syncStatus: SyncStatus,
    onOpenAuth: () -> Unit,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                "Tài khoản & Đồng bộ Đám mây",
                "Đồng bộ thời gian thực 0đ giữa các thiết bị qua Firebase"
            )

            if (user == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "📱 Dữ liệu cục bộ (Chưa đồng bộ)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Đăng nhập hoặc đăng ký tài khoản miễn phí để tự động đồng bộ công việc và mục tiêu sang các điện thoại khác.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onOpenAuth,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("🔐 Đăng nhập / Đăng ký đồng bộ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("👤", fontSize = 18.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.email ?: "Tài khoản của bạn",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            when (syncStatus) {
                                is SyncStatus.Syncing -> Text("🔄 Đang đồng bộ dữ liệu...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                is SyncStatus.Synced -> Text("🟢 Đã đồng bộ an toàn (0đ)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                is SyncStatus.Error -> Text("⚠️ ${syncStatus.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                is SyncStatus.Idle -> Text("Đã sẵn sàng đồng bộ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSyncNow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("🔄 Đồng bộ", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Đăng xuất", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable private fun NumberField(
    value: String,
    change: (String) -> Unit,
    label: Int,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        isError = isError,
        supportingText = if (isError && errorMessage != null) {
            { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
        } else null,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable private fun TimingSettingRow(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    presets: List<Int>,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(value, onValueChange, label, isError = isError, errorMessage = errorMessage)
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
