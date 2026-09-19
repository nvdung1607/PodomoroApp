package com.trustMePro.podomoroapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.trustMePro.podomoroapp.ui.AppShell
import com.trustMePro.podomoroapp.ui.theme.PodomoroAppTheme

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PodomoroAppTheme { AppShell(model) } }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    override fun onStart() { super.onStart(); model.setVisible(true) }
    override fun onStop() { model.setVisible(false); super.onStop() }
}
