package com.antigravity.studio.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAppThemePreset = staticCompositionLocalOf { AppThemePreset.CYBER_OBSIDIAN }

/**
 * Antigravity Studio Theme - Dark-First Cyber-Obsidian Theme.
 *
 * Enforces high-contrast dark aesthetic optimized for Xiaomi Pad 6 11" 2.8K 144Hz display,
 * with selectable high-end theme presets: Cyber-Obsidian, Tokyo Night, Catppuccin Mocha, Dracula.
 */
@Composable
fun AntigravityTheme(
    preset: AppThemePreset = AppThemePreset.CYBER_OBSIDIAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (preset) {
        AppThemePreset.CYBER_OBSIDIAN -> CyberObsidianColorScheme
        AppThemePreset.TOKYO_NIGHT -> TokyoNightColorScheme
        AppThemePreset.CATPPUCCIN_MOCHA -> CatppuccinMochaColorScheme
        AppThemePreset.DRACULA -> DraculaColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalAppThemePreset provides preset
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AntigravityTypography,
            content = content
        )
    }
}
