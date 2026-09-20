package com.trustMePro.podomoroapp

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import com.trustMePro.podomoroapp.ui.AppShell
import com.trustMePro.podomoroapp.ui.theme.PodomoroAppTheme
import com.trustMePro.podomoroapp.core.FocusApplication
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class PresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(compose.activity.filesDir, "ui-proof").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun lightDarkAndLargeTextScreens() {
        val repo = (compose.activity.application as FocusApplication).container.repository
        runBlocking { if (repo.dao.timer()?.status in listOf("RUNNING", "PAUSED", "AWAITING_BREAK")) repo.stop() }
        compose.onNodeWithTag("nav_tasks").assertIsDisplayed()
        screenshot("tasks-light")
        compose.onNodeWithTag("nav_focus").performClick()
        compose.onNodeWithText("Bắt đầu tập trung").assertExists()
        screenshot("focus-light")
        compose.activity.runOnUiThread {
            val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    PodomoroAppTheme(darkTheme = true) { AppShell(model) }
                }
            }
        }
        compose.waitForIdle()
        screenshot("tasks-dark-large")
        compose.onNodeWithTag("nav_focus").performClick()
        screenshot("focus-dark-large")
        compose.onNodeWithText("Bắt đầu tập trung").performScrollTo().assertIsDisplayed()
    }
}
