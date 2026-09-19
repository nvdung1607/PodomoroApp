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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

private data class Destination(val route: String, val label: Int, val icon: Int)
private val destinations = listOf(Destination("tasks", R.string.tasks, R.string.task_icon), Destination("focus", R.string.focus, R.string.focus_icon), Destination("goals", R.string.goals, R.string.goal_icon), Destination("stats", R.string.stats, R.string.stats_icon))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppShell(model: AppViewModel) {
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
    val goalSaver = remember { Saver<GoalItem?, String>(save = { Gson().toJson(it) }, restore = { Gson().fromJson(it, GoalItem::class.java) }) }
    var editTask by rememberSaveable(stateSaver = taskSaver) { mutableStateOf<TaskItem?>(null) }
    var editGoal by rememberSaveable(stateSaver = goalSaver) { mutableStateOf<GoalItem?>(null) }
    var selectedTask by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGoal by rememberSaveable { mutableStateOf<String?>(null) }
    fun focus(task: TaskItem) { selectedTask = task.id; selectedGoal = null; nav.navigate("focus") { launchSingleTop = true } }
    LaunchedEffect(model, resources) {
        for (feedback in model.feedback) {
            val result = snack.showSnackbar(resources.getString(feedback.message), if (feedback.undo != null) resources.getString(R.string.undo) else null)
            if (result == SnackbarResult.ActionPerformed) feedback.undo?.let(model::undo)
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.brand), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }, actions = {
            TextButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) { Text(stringResource(R.string.settings)) }
        }) },
        bottomBar = { NavigationBar { destinations.forEach { item -> NavigationBarItem(selected = route == item.route, onClick = {
            nav.navigate(item.route) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
        }, modifier = Modifier.testTag("nav_${item.route}"), icon = { Text(stringResource(item.icon), style = MaterialTheme.typography.titleLarge) }, label = { Text(stringResource(item.label)) }) } } },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { when (route) {
            "tasks" -> ExtendedFloatingActionButton(onClick = { editTask = TaskItem() }) { Text(stringResource(R.string.add_task)) }
            "goals" -> ExtendedFloatingActionButton(onClick = { editGoal = GoalItem() }) { Text(stringResource(R.string.add_goal)) }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            NavHost(navController = nav, startDestination = "tasks", modifier = Modifier.weight(1f)) {
                composable("tasks") { TasksScreen(data, config, onEdit = { editTask = it }, onDone = model::done, onFocus = ::focus, onAdd = { editTask = TaskItem() }) }
                composable("goals") { GoalsScreen(data, onEdit = { editGoal = it }, onAddTask = { editTask = TaskItem(goalId = it.id) }, onFocus = { selectedGoal = it.id; selectedTask = null; nav.navigate("focus") }) }
                composable("focus") { FocusScreen(data, config, access, elapsed, selectedTask, selectedGoal, onChoose = { task, goal -> selectedTask = task; selectedGoal = goal }, model = model) }
                composable("stats") { StatsScreen(data) }
                composable("settings") { SettingsScreen(config, access, model) }
            }
        }
    }
    editTask?.let { task -> TaskEditor(task, data.goals.filter { it.deletedAt == null }, onDismiss = { editTask = null }, onSave = { model.saveTask(it); editTask = null }, onDelete = if (data.tasks.any { it.id == task.id }) ({ model.delete(task); editTask = null }) else null) }
    editGoal?.let { goal -> GoalEditor(goal, onDismiss = { editGoal = null }, onSave = { model.saveGoal(it); editGoal = null }, onDelete = if (data.goals.any { it.id == goal.id }) ({ model.delete(goal); editGoal = null }) else null) }
    restore?.let { backup -> AlertDialog(onDismissRequest = { model.pendingRestore.value = null }, title = { Text(stringResource(R.string.restore_title)) }, text = { Text(stringResource(R.string.restore_hint, backup.tasks.size, backup.goals.size, backup.sessions.size)) }, confirmButton = { TextButton(enabled = !busy, onClick = model::confirmRestore) { Text(stringResource(R.string.restore_action)) } }, dismissButton = { TextButton(onClick = { model.pendingRestore.value = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun EmptyCard(title: String, hint: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
