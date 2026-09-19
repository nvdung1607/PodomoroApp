package com.trustMePro.podomoroapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5D200E),
    primaryContainer = Color(0xFF7D331C),
    onPrimaryContainer = Color(0xFFFFDACC),
    secondary = Color(0xFFD1C1B3),
    secondaryContainer = Color(0xFF51443A),
    onSecondaryContainer = Color(0xFFEEDFD3),
    tertiary = Color(0xFFBBCBA7),
    background = Color(0xFF1A1410),
    surface = Color(0xFF1A1410),
    onSurface = Color(0xFFF1E3D9),
    onSurfaceVariant = Color(0xFFD3C3B7),
    surfaceContainer = Color(0xFF271F19),
    surfaceContainerHighest = Color(0xFF3E332B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFA94729),
    primaryContainer = Color(0xFFFFDACC),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF65301D),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF30251F),
    onSurfaceVariant = Color(0xFF675B50),
    secondary = Color(0xFF675B50),
    secondaryContainer = Color(0xFFE8DDD3),
    onSecondaryContainer = Color(0xFF3B3027),
    tertiary = Color(0xFF596547),
    surfaceContainer = Color(0xFFF8EFE8),
    surfaceContainerLow = Color(0xFFFCF4EE),
    surfaceContainerHigh = Color(0xFFF1E5DB),
    surfaceContainerHighest = Color(0xFFEBDDD1),
    outline = Color(0xFF87786D)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun PodomoroAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
