package com.trustMePro.podomoroapp.core

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trustMePro.podomoroapp.MainActivity
import com.trustMePro.podomoroapp.R
import kotlinx.coroutines.launch

class AndroidTime(private val context: Context) : TimeSource {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}

interface EndScheduler {
    fun schedule(state: TimerState)
    fun schedule(state: TimerState, title: String?) { schedule(state) }
    fun cancel()
    fun completed(focus: Boolean)
}

class AndroidScheduler(private val context: Context) : EndScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val timerNotifId = 42

    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    private fun pending(generation: String = "") = PendingIntent.getBroadcast(context, 41,
        Intent(context, TimerReceiver::class.java).setAction("FOCUS_END").putExtra("generation", generation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun warnPending(generation: String = "") = PendingIntent.getBroadcast(context, 44,
        Intent(context, TimerReceiver::class.java).setAction("FOCUS_WARNING_3MIN").putExtra("generation", generation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun actionPending(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun schedule(state: TimerState) {
        schedule(state, null)
    }

    override fun schedule(state: TimerState, title: String?) {
        if (state.status != "RUNNING") {
            alarms.cancel(pending())
            alarms.cancel(warnPending())
            if (state.status == "PAUSED") {
                showLiveNotification(state, title)
            } else {
                cancelLiveNotification()
            }
            return
        }
        val at = state.segmentElapsed + state.remainingMs
        try {
            if (exactAvailable()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(state.generation))
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(state.generation))
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(state.generation))
        }

        // Schedule 3-minute warning if in FOCUS phase and remaining > 3 minutes (180,000 ms)
        if (state.phase == "FOCUS" && state.remainingMs > 180_000L) {
            val warnAt = state.segmentElapsed + (state.remainingMs - 180_000L)
            try {
                if (exactAvailable()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, warnAt, warnPending(state.generation))
                else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, warnAt, warnPending(state.generation))
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, warnAt, warnPending(state.generation))
            }
        } else {
            alarms.cancel(warnPending())
        }

        showLiveNotification(state, title)
    }

    private fun showLiveNotification(state: TimerState, title: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching { manager.deleteNotificationChannel("timer_live") }
            runCatching { manager.deleteNotificationChannel("timer_live_v2") }
            val liveChannel = NotificationChannel(
                "timer_live_v3",
                context.getString(R.string.notification_timer_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_timer_channel_desc)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(liveChannel)
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = "OPEN_FOCUS"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isBreak = state.phase == "BREAK"
        val isPaused = state.status == "PAUSED"
        val displayTitle = when {
            isBreak -> context.getString(R.string.break_phase)
            !title.isNullOrBlank() -> title
            else -> context.getString(R.string.no_task_selected)
        }

        val primaryColor = if (isBreak) 0xFF2E7D32.toInt() else 0xFFE64A19.toInt()
        val badgeText = if (isBreak) "☕ " + context.getString(R.string.mode_break) else "🔥 " + context.getString(R.string.mode_focus)
        val cycleText = context.getString(R.string.cycle_count, (state.completedInCycle % 4) + 1)
        val secs = (state.remainingMs + 999) / 1000
        val timeStr = String.format(java.util.Locale.ROOT, "%02d:%02d", secs / 60, secs % 60)
        val targetElapsed = state.segmentElapsed + state.remainingMs

        val smallView = RemoteViews(context.packageName, R.layout.notif_timer_small).apply {
            setTextViewText(R.id.notif_phase_small, badgeText)
            setTextColor(R.id.notif_phase_small, primaryColor)
            setTextViewText(R.id.notif_title_small, displayTitle)
            setTextViewText(R.id.notif_sub_small, cycleText)

            if (isPaused) {
                setViewVisibility(R.id.notif_chronometer_small, View.GONE)
                setViewVisibility(R.id.notif_timer_text_small, View.VISIBLE)
                setTextViewText(R.id.notif_timer_text_small, timeStr)
                setTextColor(R.id.notif_timer_text_small, primaryColor)
            } else {
                setViewVisibility(R.id.notif_chronometer_small, View.VISIBLE)
                setViewVisibility(R.id.notif_timer_text_small, View.GONE)
                setTextColor(R.id.notif_chronometer_small, primaryColor)
                setChronometerCountDown(R.id.notif_chronometer_small, true)
                setChronometer(R.id.notif_chronometer_small, targetElapsed, null, true)
            }
        }

        val largeView = RemoteViews(context.packageName, R.layout.notif_timer_large).apply {
            setTextViewText(R.id.notif_badge_large, badgeText)
            setTextColor(R.id.notif_badge_large, primaryColor)
            setTextViewText(R.id.notif_cycle_large, cycleText)
            setTextViewText(R.id.notif_title_large, displayTitle)

            if (isPaused) {
                setViewVisibility(R.id.notif_chronometer_large, View.GONE)
                setViewVisibility(R.id.notif_timer_text_large, View.VISIBLE)
                setTextViewText(R.id.notif_timer_text_large, timeStr)
                setTextColor(R.id.notif_timer_text_large, primaryColor)
                setTextViewText(R.id.notif_status_large, context.getString(R.string.notification_paused_badge))
            } else {
                setViewVisibility(R.id.notif_chronometer_large, View.VISIBLE)
                setViewVisibility(R.id.notif_timer_text_large, View.GONE)
                setTextColor(R.id.notif_chronometer_large, primaryColor)
                setChronometerCountDown(R.id.notif_chronometer_large, true)
                setChronometer(R.id.notif_chronometer_large, targetElapsed, null, true)
                setTextViewText(R.id.notif_status_large, context.getString(R.string.notification_remaining_time))
            }
        }

        val builder = NotificationCompat.Builder(context, "timer_live_v3")
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setColor(primaryColor)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(smallView)
            .setCustomBigContentView(largeView)
            .setContentTitle(displayTitle)
            .setContentText(timeStr)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isPaused) {
            builder.addAction(0, context.getString(R.string.notification_action_resume), actionPending("TIMER_RESUME", 103))
        } else {
            builder.addAction(0, context.getString(R.string.notification_action_pause), actionPending("TIMER_PAUSE", 101))
        }

        if (isBreak) {
            builder.addAction(0, context.getString(R.string.extend_1_minute), actionPending("TIMER_EXTEND_1", 105))
            builder.addAction(0, context.getString(R.string.notification_action_skip_break), actionPending("TIMER_STOP", 104))
        } else {
            builder.addAction(0, context.getString(R.string.extend_1_minute), actionPending("TIMER_EXTEND_1", 105))
            builder.addAction(0, context.getString(R.string.notification_action_stop), actionPending("TIMER_STOP", 104))
        }

        try {
            manager.notify(timerNotifId, builder.build())
        } catch (_: Throwable) {
        }
    }

    private fun cancelLiveNotification() {
        manager.cancel(timerNotifId)
    }

    override fun cancel() {
        alarms.cancel(pending())
        alarms.cancel(warnPending())
        cancelLiveNotification()
        AlarmPlayer.stopAlarm(context)
    }

    override fun completed(focus: Boolean) {
        cancelLiveNotification()
        alarms.cancel(warnPending())

        // 1. Play continuous loud alarm and vibration
        AlarmPlayer.startAlarm(context, focus)

        // 2. Setup notification with action to dismiss alarm
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= 26) {
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel("timer_alarm_v1", "Báo thức FocusDo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chuông báo thức khi hoàn thành phiên"
                enableVibration(true)
                setSound(soundUri, audioAttr)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                action = "OPEN_FOCUS"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (focus) context.getString(R.string.focus_finished) else context.getString(R.string.break_finished_title)
        val hint = if (focus) context.getString(R.string.finished_hint) else context.getString(R.string.break_finished_hint)
        val notification = NotificationCompat.Builder(context, "timer_alarm_v1")
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("🔔 $title")
            .setContentText(hint)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, "🔕 Tắt chuông", actionPending("TIMER_DISMISS_ALARM", 106))
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        try { manager.notify(43, notification) } catch (_: SecurityException) { /* Permission may be revoked between checks. */ }
    }
}

data class DeviceAccess(val exact: Boolean = false, val notifications: Boolean = false, val dndPermission: Boolean = false, val dndSupported: Boolean = Build.VERSION.SDK_INT >= 29, val dndActive: Boolean = false)

class FocusDnd(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val uri = Uri.parse("condition://${context.packageName}/focus")
    private val prefs = context.getSharedPreferences("platform_state", Context.MODE_PRIVATE)
    fun hasPermission() = manager.isNotificationPolicyAccessGranted
    fun active(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 35 && hasPermission()) {
            val id = prefs.getString("rule", null) ?: return false
            manager.getAutomaticZenRule(id)?.isEnabled == true && manager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE
        } else false
    }.getOrDefault(false)

    fun apply(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 29 || !hasPermission()) return
        runCatching {
            var id = prefs.getString("rule", null)
            val policy = ZenPolicy.Builder()
                .disallowAllSounds()
                .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                .allowRepeatCallers(true)
                .allowAlarms(true)
                .showAllVisualEffects()
                .build()

            val expectedName = context.getString(R.string.dnd_rule)
            var rule = if (id != null) runCatching { manager.getAutomaticZenRule(id) }.getOrNull() else null
            if (rule != null && rule.name != expectedName) {
                runCatching { manager.removeAutomaticZenRule(id) }
                rule = null
                id = null
                prefs.edit().remove("rule").apply()
            }
            if (rule == null && enabled) {
                val newRule = AutomaticZenRule(
                    expectedName,
                    null,
                    ComponentName(context, MainActivity::class.java),
                    uri,
                    policy,
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true
                )
                id = manager.addAutomaticZenRule(newRule)
                prefs.edit().putString("rule", id).apply()
            } else if (rule != null && rule.zenPolicy != policy) {
                rule.zenPolicy = policy
                manager.updateAutomaticZenRule(id, rule)
            }
            if (id != null) manager.setAutomaticZenRuleState(id,
                Condition(uri, context.getString(R.string.dnd_rule), if (enabled) Condition.STATE_TRUE else Condition.STATE_FALSE))
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as FocusApplication
        app.scope.launch {
            try {
                when (intent.action) {
                    "TIMER_PAUSE" -> app.container.repository.pause()
                    "TIMER_RESUME" -> app.container.repository.resume()
                    "TIMER_EXTEND" -> app.container.repository.extendCurrentTimer(300_000L)
                    "TIMER_EXTEND_1" -> app.container.repository.extendCurrentTimer(60_000L)
                    "TIMER_STOP" -> {
                        AlarmPlayer.stopAlarm(context)
                        app.container.repository.stop()
                    }
                    "TIMER_DISMISS_ALARM" -> {
                        AlarmPlayer.stopAlarm(context)
                    }
                    "FOCUS_WARNING_3MIN" -> {
                        val timer = app.container.database.dao().timer()
                        if (timer != null && timer.status == "RUNNING" && timer.phase == "FOCUS") {
                            AlarmPlayer.playWarningTing(context)
                            showWarningNotification(context)
                        }
                    }
                    else -> app.container.repository.reconcile(intent.getStringExtra("generation"), reschedule = intent.action != "FOCUS_END")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showWarningNotification(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("timer_warning_3min", "Cảnh báo thời gian", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Thông báo cảnh báo trước 3 phút"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, "timer_warning_3min")
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("⏱ Còn 3 phút nữa!")
            .setContentText("Phiên tập trung sắp kết thúc, chuẩn bị hoàn thành công việc nhé.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try { manager.notify(45, notif) } catch (_: Exception) { }
    }
}
