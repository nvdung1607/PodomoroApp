package com.trustMePro.podomoroapp.core

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlarmPlayer {
    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    private var activeRingtone: Ringtone? = null
    private var activeVibrator: Any? = null
    private var autoStopJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val alarmVibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)

    @Synchronized
    fun startAlarm(context: Context, focus: Boolean = true) {
        stopAlarm(context)
        _isRinging.value = true

        val appContext = context.applicationContext
        val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // 1. Play continuous alarm sound with USAGE_ALARM to ensure loud volume and DND bypass
        try {
            val ringtone = RingtoneManager.getRingtone(appContext, soundUri)
            if (Build.VERSION.SDK_INT >= 21) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= 28) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
            activeRingtone = ringtone
        } catch (_: Exception) { }

        // 2. Hardware vibration with repeating pattern
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val v = vm?.defaultVibrator
                v?.vibrate(VibrationEffect.createWaveform(alarmVibrationPattern, 0)) // repeat from index 0
                activeVibrator = v
            } else {
                @Suppress("DEPRECATION")
                val v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    v?.vibrate(VibrationEffect.createWaveform(alarmVibrationPattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(alarmVibrationPattern, 0)
                }
                activeVibrator = v
            }
        } catch (_: Exception) { }

        // 3. Auto-stop after 30 seconds if user leaves phone unattended
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(30_000L)
            stopAlarm(appContext)
        }
    }

    @Synchronized
    fun stopAlarm(context: Context? = null) {
        autoStopJob?.cancel()
        autoStopJob = null

        try {
            activeRingtone?.stop()
        } catch (_: Exception) { }
        activeRingtone = null

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                (activeVibrator as? Vibrator)?.cancel()
            } else {
                @Suppress("DEPRECATION")
                (activeVibrator as? Vibrator)?.cancel()
            }
        } catch (_: Exception) { }
        activeVibrator = null

        _isRinging.value = false

        if (context != null) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(43) // alarm notification ID
            } catch (_: Exception) { }
        }
    }

    fun playWarningTing(context: Context) {
        val appContext = context.applicationContext
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // 1. Play single gentle notification sound
        try {
            val ringtone = RingtoneManager.getRingtone(appContext, soundUri)
            if (Build.VERSION.SDK_INT >= 21) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
        } catch (_: Exception) { }

        // 2. Gentle single vibration tick (150ms)
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= 26) {
                @Suppress("DEPRECATION")
                val v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) { }
    }
}
