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
    primary = NeonGold,
    secondary = NeonOrange,
    tertiary = MintGreen,
    background = CharcoalBg,
    surface = CharcoalBg,
    onPrimary = CharcoalBg,
    onSecondary = CharcoalBg,
    onTertiary = White,
    onBackground = White,
    onSurface = White,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = GrayText
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  // We disable dynamicColor to enforce our striking, consistent professional Dark Esports identity
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
