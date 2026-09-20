package com.trustMePro.podomoroapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.trustMePro.podomoroapp.ui.AppShell
import com.trustMePro.podomoroapp.ui.theme.PodomoroAppTheme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        handleIntent(intent)
        setContent {
            val config by model.settings.collectAsStateWithLifecycle()
            val targetRoute by pendingRoute
            PodomoroAppTheme(themeMode = config.theme) {
                AppShell(
                    model = model,
                    targetRoute = targetRoute,
                    onRouteHandled = { pendingRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_FOCUS") {
            pendingRoute.value = "focus"
        }
    }

    override fun onResume() { super.onResume(); model.refresh() }
    override fun onStart() { super.onStart(); model.setVisible(true) }
    override fun onStop() { model.setVisible(false); super.onStop() }
}
