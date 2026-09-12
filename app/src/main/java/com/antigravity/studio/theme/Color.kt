package com.antigravity.studio.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ============================================================================
// Cyber-Obsidian Core Palette (Primary Design System)
// ============================================================================
val CyberObsidian = Color(0xFF0B0F19)       // Main window & terminal viewport background
val SurfaceObsidian = Color(0xFF111622)     // Side panels, toolbars, topbar, bottom sheets
val SurfaceElevated = Color(0xFF182030)     // Cards, hover states, secondary buttons
val BorderObsidian = Color(0xFF1E2638)      // Panel dividers, separators, stroke borders

// Neon & Cosmic Accents
val NeonCyan = Color(0xFF00F0FF)           // Primary accent, terminal cursor, agent prompts
val NeonCyanMuted = Color(0x3300F0FF)      // Subtle cyan glow / inactive accent
val CosmicViolet = Color(0xFF8B5CF6)       // Secondary accent, agent reasoning badges
val CosmicVioletMuted = Color(0x338B5CF6)  // Subtle violet glow
val AccentAmber = Color(0xFFF59E0B)        // Linter alerts, warnings, executing states

// High-Contrast Typography (W3C AAA)
val TextPrimary = Color(0xFFE6EDF3)        // High contrast readable text
val TextSecondary = Color(0xFF8B949E)      // Metadata, line numbers, inactive tabs
val TextMuted = Color(0xFF5A6270)          // Disabled state, placeholders

// Functional Status Tokens
val StatusSuccess = Color(0xFF22C55E)      // Agent ready, tests passed (EXIT_SUCCESS)
val StatusError = Color(0xFFEF4444)        // Errors, compilation failure, SIGSEGV
val StatusWarning = Color(0xFFF59E0B)      // Warnings, linter alerts
val StatusExecuting = Color(0xFF00F0FF)    // Active command execution

// ============================================================================
// High-End Theme Presets: Tokyo Night, Catppuccin Mocha, Dracula
// ============================================================================
enum class AppThemePreset(val displayName: String) {
    CYBER_OBSIDIAN("Cyber-Obsidian"),
    TOKYO_NIGHT("Tokyo Night"),
    CATPPUCCIN_MOCHA("Catppuccin Mocha"),
    DRACULA("Dracula")
}

// Tokyo Night Palette
val TokyoNightBackground = Color(0xFF1A1B26)
val TokyoNightSurface = Color(0xFF1F2335)
val TokyoNightBorder = Color(0xFF292E42)
val TokyoNightAccent = Color(0xFF7AA2F7)
val TokyoNightSecondary = Color(0xFFBB9AF7)

// Catppuccin Mocha Palette
val CatppuccinMochaBackground = Color(0xFF1E1E2E)
val CatppuccinMochaSurface = Color(0xFF25263A)
val CatppuccinMochaBorder = Color(0xFF313244)
val CatppuccinMochaAccent = Color(0xFF89B4FA)
val CatppuccinMochaSecondary = Color(0xFFCBA6F7)

// Dracula Palette
val DraculaBackground = Color(0xFF282A36)
val DraculaSurface = Color(0xFF343746)
val DraculaBorder = Color(0xFF44475A)
val DraculaAccent = Color(0xFF8BE9FD)
val DraculaSecondary = Color(0xFFBD93F9)

// ============================================================================
// Material 3 Dark ColorScheme Mappings
// ============================================================================
val CyberObsidianColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberObsidian,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = NeonCyan,
    secondary = CosmicViolet,
    onSecondary = Color.White,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = CosmicViolet,
    tertiary = AccentAmber,
    onTertiary = CyberObsidian,
    background = CyberObsidian,
    onBackground = TextPrimary,
    surface = SurfaceObsidian,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderObsidian,
    outlineVariant = BorderObsidian,
    error = StatusError,
    onError = Color.White
)

val TokyoNightColorScheme = darkColorScheme(
    primary = TokyoNightAccent,
    onPrimary = TokyoNightBackground,
    primaryContainer = TokyoNightSurface,
    onPrimaryContainer = TokyoNightAccent,
    secondary = TokyoNightSecondary,
    onSecondary = Color.White,
    background = TokyoNightBackground,
    onBackground = TextPrimary,
    surface = TokyoNightSurface,
    onSurface = TextPrimary,
    surfaceVariant = TokyoNightSurface,
    onSurfaceVariant = TextSecondary,
    outline = TokyoNightBorder,
    error = StatusError
)

val CatppuccinMochaColorScheme = darkColorScheme(
    primary = CatppuccinMochaAccent,
    onPrimary = CatppuccinMochaBackground,
    primaryContainer = CatppuccinMochaSurface,
    onPrimaryContainer = CatppuccinMochaAccent,
    secondary = CatppuccinMochaSecondary,
    onSecondary = Color.White,
    background = CatppuccinMochaBackground,
    onBackground = TextPrimary,
    surface = CatppuccinMochaSurface,
    onSurface = TextPrimary,
    surfaceVariant = CatppuccinMochaSurface,
    onSurfaceVariant = TextSecondary,
    outline = CatppuccinMochaBorder,
    error = StatusError
)

val DraculaColorScheme = darkColorScheme(
    primary = DraculaAccent,
    onPrimary = DraculaBackground,
    primaryContainer = DraculaSurface,
    onPrimaryContainer = DraculaAccent,
    secondary = DraculaSecondary,
    onSecondary = Color.White,
    background = DraculaBackground,
    onBackground = TextPrimary,
    surface = DraculaSurface,
    onSurface = TextPrimary,
    surfaceVariant = DraculaSurface,
    onSurfaceVariant = TextSecondary,
    outline = DraculaBorder,
    error = StatusError
)
