package com.antigravity.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.KeyCapTextStyle
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

/**
 * KeyAction represents any input event generated from the ProductivityBar.
 */
sealed interface KeyAction {
    data class RawBytes(val bytes: ByteArray) : KeyAction {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RawBytes
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class ShortcutCommand(val commandText: String) : KeyAction
    data object ToggleCtrl : KeyAction
    data object ToggleAlt : KeyAction
    data object ToggleSoftKeyboard : KeyAction
}

/**
 * ProductivityBar provides tactile quick-access virtual keys and commands
 * specifically engineered for mobile agent workflows on the Xiaomi Pad 6.
 */
@Composable
fun ProductivityBar(
    modifier: Modifier = Modifier,
    isCtrlActive: Boolean = false,
    isAltActive: Boolean = false,
    onKeyAction: (KeyAction) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    fun triggerAction(action: KeyAction) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKeyAction(action)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(SurfaceObsidian)
            .border(
                width = 1.dp,
                color = BorderObsidian
            )
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- 1. LATCH MODIFIERS & ESSENTIAL ESC/TAB ---
        KeyCapButton(
            label = "CTRL",
            isActive = isCtrlActive,
            activeColor = NeonCyan,
            onClick = { triggerAction(KeyAction.ToggleCtrl) }
        )

        KeyCapButton(
            label = "ALT",
            isActive = isAltActive,
            activeColor = CosmicViolet,
            onClick = { triggerAction(KeyAction.ToggleAlt) }
        )

        KeyCapButton(
            label = "ESC",
            onClick = { triggerAction(KeyAction.RawBytes(byteArrayOf(0x1B))) }
        )

        KeyCapButton(
            label = "TAB",
            onClick = { triggerAction(KeyAction.RawBytes(byteArrayOf(0x09))) }
        )

        VerticalBarDivider()

        // --- 2. ANTIGRAVITY AGENT ACTIONS (PROMINENT AT START) ---
        KeyCapButton(
            label = "⚡ agy",
            accentColor = NeonCyan,
            textColor = NeonCyan,
            customBackground = NeonCyan,
            onClick = { triggerAction(KeyAction.ShortcutCommand("agy\r")) }
        )

        KeyCapButton(
            label = "^C",
            accentColor = StatusError,
            textColor = StatusError,
            customBackground = StatusError,
            onClick = { triggerAction(KeyAction.RawBytes(byteArrayOf(0x03))) }
        )

        VerticalBarDivider()

        // --- 3. DIRECTIONAL ARROWS ---
        KeyCapButton(
            label = "↑",
            onClick = { triggerAction(KeyAction.RawBytes("\u001B[A".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "↓",
            onClick = { triggerAction(KeyAction.RawBytes("\u001B[B".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "←",
            onClick = { triggerAction(KeyAction.RawBytes("\u001B[D".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "→",
            onClick = { triggerAction(KeyAction.RawBytes("\u001B[C".toByteArray(Charsets.UTF_8))) }
        )

        VerticalBarDivider()

        // --- 4. NAVIGATION & CODE SYMBOLS ---
        KeyCapButton(
            label = "~",
            onClick = { triggerAction(KeyAction.RawBytes("~".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "|",
            onClick = { triggerAction(KeyAction.RawBytes("|".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "^",
            onClick = { triggerAction(KeyAction.RawBytes("^".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "/",
            onClick = { triggerAction(KeyAction.RawBytes("/".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "-",
            onClick = { triggerAction(KeyAction.RawBytes("-".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "_",
            onClick = { triggerAction(KeyAction.RawBytes("_".toByteArray(Charsets.UTF_8))) }
        )

        VerticalBarDivider()

        // --- 5. SOFT KEYBOARD TOGGLE ---
        KeyCapButton(
            label = "⌨",
            accentColor = CosmicViolet,
            textColor = TextPrimary,
            onClick = { triggerAction(KeyAction.ToggleSoftKeyboard) }
        )
    }
}

/**
 * Ergonomic KeyCapButton styled as a tactile cyber keycap with custom tinting.
 */
@Composable
private fun KeyCapButton(
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = NeonCyan,
    accentColor: Color? = null,
    textColor: Color = TextPrimary,
    customBackground: Color? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)

    val backgroundBrush = when {
        isActive -> Brush.verticalGradient(
            colors = listOf(
                activeColor.copy(alpha = 0.35f),
                activeColor.copy(alpha = 0.15f)
            )
        )
        customBackground != null -> Brush.verticalGradient(
            colors = listOf(
                customBackground.copy(alpha = 0.28f),
                customBackground.copy(alpha = 0.12f)
            )
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                SurfaceElevated,
                SurfaceElevated.copy(alpha = 0.8f)
            )
        )
    }

    val borderColor = when {
        isActive -> activeColor
        accentColor != null -> accentColor.copy(alpha = 0.65f)
        else -> BorderObsidian
    }

    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 38.dp)
            .clip(shape)
            .background(backgroundBrush)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = activeColor),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = KeyCapTextStyle,
            color = if (isActive) activeColor else textColor,
            fontWeight = if (isActive || customBackground != null) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun VerticalBarDivider() {
    Box(
        modifier = Modifier
            .height(24.dp)
            .widthIn(1.dp)
            .background(BorderObsidian)
    )
}
