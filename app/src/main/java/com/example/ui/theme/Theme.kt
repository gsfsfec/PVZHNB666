package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyanNeon,
    secondary = GreenNeon,
    tertiary = PurpleNeon,
    background = QuantumBg,
    surface = QuantumSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = QuantumBorder
  )

private val LightColorScheme = DarkColorScheme // Always premium dark mode for hacker terminal vibe

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force premium dark mode for beautiful cyber aesthetic
  dynamicColor: Boolean = false, // Preserve original cyberpunk palette
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
