package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioColorScheme = darkColorScheme(
  primary = CyanAccent,
  onPrimary = Color.Black,
  primaryContainer = StudioSurfaceVariant,
  onPrimaryContainer = CyanAccent,
  secondary = PurpleAccent,
  onSecondary = Color.White,
  secondaryContainer = StudioSurfaceVariant,
  onSecondaryContainer = PurpleAccent,
  tertiary = PinkAccent,
  onTertiary = Color.White,
  background = StudioDarkBg,
  onBackground = TextPrimary,
  surface = StudioSurface,
  onSurface = TextPrimary,
  surfaceVariant = StudioSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = StudioBorder,
  error = RedAccent,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = StudioColorScheme,
    typography = Typography,
    content = content
  )
}

