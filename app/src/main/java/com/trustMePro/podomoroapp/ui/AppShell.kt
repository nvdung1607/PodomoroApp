package com.trustMePro.podomoroapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.gson.Gson
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.trustMePro.podomoroapp.AppViewModel
import com.trustMePro.podomoroapp.R
import com.trustMePro.podomoroapp.core.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag

private data class Destination(val route: String, val label: Int, @androidx.annotation.DrawableRes val icon: Int)
private val destinations = listOf(
    Destination("tasks", R.string.tasks, R.drawable.ic_nav_tasks),
    Destination("focus", R.string.focus, R.drawable.ic_nav_focus),
    Destination("stats", R.string.stats, R.drawable.ic_nav_stats)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppShell(
    model: AppViewModel,
    targetRoute: String? = null,
    onRouteHandled: () -> Unit = {}
) {
    val data by model.data.collectAsStateWithLifecycle()
    val config by model.settings.collectAsStateWithLifecycle()
    val access by model.access.collectAsStateWithLifecycle()
    val elapsed by model.elapsed.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val restore by model.pendingRestore.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "tasks"
    val snack = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val taskSaver = remember { Saver<TaskItem?, String>(save = { Gson().toJson(it) }, restore = { Gson().fromJson(it, TaskItem::class.java) }) }
    var editTask by rememberSaveable(stateSaver = taskSaver) { mutableStateOf<TaskItem?>(null) }
    var selectedTask by rememberSaveable { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun focus(task: TaskItem) { selectedTask = task.id; nav.navigate("focus") { launchSingleTop = true } }

    LaunchedEffect(targetRoute) {
        if (targetRoute != null) {
            nav.navigate(targetRoute) {
                popUpTo(nav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onRouteHandled()
        }
    }

    LaunchedEffect(model, resources) {
        for (feedback in model.feedback) {
            val result = snack.showSnackbar(resources.getString(feedback.message), if (feedback.undo != null) resources.getString(R.string.undo) else null)
            if (result == SnackbarResult.ActionPerformed) feedback.undo?.let(model::undo)
        }
    }

    Row(Modifier.fillMaxSize()) {
        if (isLandscape && route != "settings") {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                header = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp).padding(top = 4.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🍅", fontSize = 18.sp)
                        }
                    }
                },
                modifier = Modifier.fillMaxHeight()
            ) {
                Spacer(Modifier.height(8.dp))
                destinations.forEach { item ->
                    val selected = route == item.route
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            if (route != item.route) {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(item.icon),
                                contentDescription = stringResource(item.label),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                stringResource(item.label),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.testTag("nav_${item.route}")
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { nav.navigate("settings") { launchSingleTop = true } },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            topBar = {
                if (!isLandscape || route == "settings") {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (route == "settings") {
                                    IconButton(onClick = { nav.popBackStack() }) {
                                        Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.settings),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🍅", fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.brand),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        actions = {
                            if (route != "settings") {
                                IconButton(
                                    onClick = { nav.navigate("settings") { launchSingleTop = true } },
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_settings),
                                                contentDescription = stringResource(R.string.settings),
                                                modifier = Modifier.size(19.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (!isLandscape && route != "settings") {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        destinations.forEach { item ->
                            val selected = route == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (route != item.route) {
                                        nav.navigate(item.route) {
                                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(item.icon),
                                        contentDescription = stringResource(item.label),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(item.label),
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                ),
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            val todayStr = java.time.LocalDate.now().toString()
            if (route == "tasks") {
                ExtendedFloatingActionButton(
                    onClick = { editTask = TaskItem(plannedDate = todayStr) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("➕ ", fontSize = 15.sp)
                    Text(stringResource(R.string.add_task), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(navController = nav, startDestination = "tasks", modifier = Modifier.weight(1f)) {
                composable("tasks") {
                    val todayStr = java.time.LocalDate.now().toString()
                    TasksScreen(
                        data = data,
                        settings = config,
                        onEdit = { editTask = it },
                        onDone = model::done,
                        onFocus = ::focus,
                        onAdd = { editTask = TaskItem(plannedDate = todayStr) },
                        onSaveChecklist = model::saveChecklist,
                        onDoneChecklist = model::doneChecklist,
                        onDeleteChecklist = model::deleteChecklist
                    )
                }
                composable("focus") { FocusScreen(data, config, access, elapsed, selectedTask, onChoose = { task -> selectedTask = task }, model = model) }
                composable("stats") { StatsScreen(data) }
                composable("settings") { SettingsScreen(config, access, model) }
            }
        }
    }
    }
    editTask?.let { task ->
        TaskEditor(
            task = task,
            checklists = data.checklists.filter { it.taskId == task.id },
            onDismiss = { editTask = null },
            onSave = { model.saveTask(it); editTask = null },
            onDelete = if (data.tasks.any { it.id == task.id }) ({ model.delete(task); editTask = null }) else null,
            onSaveChecklist = model::saveChecklist,
            onDoneChecklist = model::doneChecklist,
            onDeleteChecklist = model::deleteChecklist
        )
    }
    restore?.let { backup -> AlertDialog(onDismissRequest = { model.pendingRestore.value = null }, title = { Text(stringResource(R.string.restore_title)) }, text = { Text(stringResource(R.string.restore_hint, backup.tasks.size, backup.goals.size, backup.sessions.size)) }, confirmButton = { TextButton(enabled = !busy, onClick = model::confirmRestore) { Text(stringResource(R.string.restore_action)) } }, dismissButton = { TextButton(onClick = { model.pendingRestore.value = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun EmptyCard(title: String, hint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("🌱", fontSize = 36.sp)
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
