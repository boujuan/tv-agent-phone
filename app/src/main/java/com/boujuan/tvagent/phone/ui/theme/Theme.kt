package com.boujuan.tvagent.phone.ui.theme

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

// The panel's colours (tvpanel/common.py): green ok, amber warning, red error, blue accent.
val Green = Color(0xFF2FB170)
val Amber = Color(0xFFE0A030)
val Red = Color(0xFFE05555)
private val Blue = Color(0xFF4C8DFF)
private val Purple = Color(0xFF9B6CF5)

private val Light = lightColorScheme(primary = Color(0xFF2F63D6), secondary = Purple, tertiary = Green)
private val Dark = darkColorScheme(primary = Blue, secondary = Purple, tertiary = Green)

@Composable
fun TvAgentTheme(theme: String = "system", dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
