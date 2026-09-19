package com.trustMePro.podomoroapp.core

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
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

interface EndScheduler { fun schedule(state: TimerState); fun cancel(); fun completed(focus: Boolean) }

class AndroidScheduler(private val context: Context) : EndScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun exactAvailable() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    private fun pending(generation: String = "") = PendingIntent.getBroadcast(context, 41,
        Intent(context, TimerReceiver::class.java).setAction("FOCUS_END").putExtra("generation", generation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun schedule(state: TimerState) {
        if (state.status != "RUNNING") { cancel(); return }
        val at = state.segmentElapsed + state.remainingMs
        try {
            if (exactAvailable()) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(state.generation))
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(state.generation))
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(state.generation))
        }
    }
    override fun cancel() { alarms.cancel(pending()) }
    override fun completed(focus: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("timer_end", context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_HIGH))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "timer_end")
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(context.getString(if (focus) R.string.focus_finished else R.string.break_finished))
            .setContentText(context.getString(R.string.finished_hint)).setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        try { manager.notify(42, notification) } catch (_: SecurityException) { /* Permission may be revoked between checks. */ }
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
            if (id != null && manager.getAutomaticZenRule(id) == null) id = null
            if (id == null && enabled) {
                val policy = ZenPolicy.Builder().disallowAllSounds().allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                    .allowAlarms(true).hideAllVisualEffects().build()
                val rule = AutomaticZenRule(context.getString(R.string.dnd_rule), null,
                    ComponentName(context, MainActivity::class.java), uri, policy, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
                id = manager.addAutomaticZenRule(rule)
                prefs.edit().putString("rule", id).apply()
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
            try { app.container.repository.reconcile(intent.getStringExtra("generation"), reschedule = intent.action != "FOCUS_END") }
            finally { pending.finish() }
        }
    }
}
