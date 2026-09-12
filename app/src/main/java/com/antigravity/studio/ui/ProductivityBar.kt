package com.antigravity.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.KeyCapTextStyle
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

/**
 * KeyAction representa cualquier evento o comando de entrada emitido desde la ProductivityBar.
 * Conforme a SPEC-003 §4.3 y §6.3.
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

    // Acciones de control y gobernanza agéntica aprobadas en SPEC-003
    data object Approve : KeyAction {
        val controlByte: Byte = 0x0B // Carácter ASCII '\u000B' (Ctrl+K)
    }
    data object OpenModelSelector : KeyAction
    data object ToggleSidebarFiles : KeyAction
    data object StopExecution : KeyAction {
        val controlByte: Byte = 0x03 // Carácter ASCII '\u0003' (Ctrl+C / SIGINT)
    }
    data object OpenSettings : KeyAction
}

/**
 * ProductivityBar provee la fila táctil de alta productividad para la Xiaomi Pad 6 (11" 2.8K 144Hz),
 * con el botón destacado [✓ Aprobar (Ctrl+K)], botones de acceso rápido agéntico y modificadores físicos.
 * Conforme a SPEC-003 §4.3.
 */
@Composable
fun ProductivityBar(
    modifier: Modifier = Modifier,
    isCtrlActive: Boolean = false,
    isAltActive: Boolean = false,
    onApprove: (() -> Unit)? = null,
    onOpenModelSelector: (() -> Unit)? = null,
    onToggleSidebarFiles: (() -> Unit)? = null,
    onStopExecution: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onKeyAction: (KeyAction) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    fun triggerAction(action: KeyAction) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKeyAction(action)
    }

    fun triggerApprove() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (onApprove != null) {
            onApprove()
        } else {
            triggerAction(KeyAction.Approve)
            triggerAction(KeyAction.RawBytes(byteArrayOf(0x0B)))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
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
        // ====================================================================
        // 1. BOTÓN PRINCIPAL DESTACADO: [✓ Aprobar (Ctrl+K)]
        // ====================================================================
        ApprovalButton(
            onClick = { triggerApprove() }
        )

        VerticalBarDivider()

        // ====================================================================
        // 2. ACCESOS RÁPIDOS DE CONTROL AGÉNTICO
        // ====================================================================
        // [⚡ Modelo] (Emite agy model\n)
        KeyCapButton(
            label = "⚡ Modelo",
            accentColor = CosmicViolet,
            textColor = CosmicViolet,
            customBackground = CosmicViolet,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                triggerAction(KeyAction.ShortcutCommand("agy model\n"))
            }
        )

        // [⏹ Detener] (Emite \u0003 Ctrl+C / SIGINT)
        KeyCapButton(
            label = "⏹ Detener",
            accentColor = StatusError,
            textColor = StatusError,
            customBackground = StatusError,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (onStopExecution != null) {
                    onStopExecution()
                } else {
                    triggerAction(KeyAction.StopExecution)
                    triggerAction(KeyAction.RawBytes(byteArrayOf(0x03)))
                }
            }
        )

        // [📁 Proyectos] (Emite cd ~/projects && ls -la\n)
        KeyCapButton(
            label = "📁 Proyectos",
            accentColor = NeonCyan,
            textColor = NeonCyan,
            customBackground = NeonCyan,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                triggerAction(KeyAction.ShortcutCommand("cd ~/projects && ls -la\n"))
            }
        )

        // [⚙ Ajustes]
        KeyCapButton(
            label = "⚙ Ajustes",
            accentColor = TextSecondary,
            textColor = TextPrimary,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenSettings?.invoke() ?: triggerAction(KeyAction.OpenSettings)
            }
        )

        VerticalBarDivider()

        // ====================================================================
        // 3. TECLAS MODIFICADORAS Y SISTEMA: [CTRL], [TAB], [ESC]
        // ====================================================================
        KeyCapButton(
            label = "CTRL",
            isActive = isCtrlActive,
            activeColor = NeonCyan,
            onClick = { triggerAction(KeyAction.ToggleCtrl) }
        )

        KeyCapButton(
            label = "TAB",
            onClick = { triggerAction(KeyAction.RawBytes(byteArrayOf(0x09))) }
        )

        KeyCapButton(
            label = "ESC",
            onClick = { triggerAction(KeyAction.RawBytes(byteArrayOf(0x1B))) }
        )

        KeyCapButton(
            label = "ALT",
            isActive = isAltActive,
            activeColor = CosmicViolet,
            onClick = { triggerAction(KeyAction.ToggleAlt) }
        )

        VerticalBarDivider()

        // ====================================================================
        // 4. DIRECCIÓN Y NAVEGACIÓN
        // ====================================================================
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

        // ====================================================================
        // 5. SÍMBOLOS FRECUENTES DE TERMINAL & CLI
        // ====================================================================
        KeyCapButton(
            label = "~",
            onClick = { triggerAction(KeyAction.RawBytes("~".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "|",
            onClick = { triggerAction(KeyAction.RawBytes("|".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "/",
            onClick = { triggerAction(KeyAction.RawBytes("/".toByteArray(Charsets.UTF_8))) }
        )

        KeyCapButton(
            label = "-",
            onClick = { triggerAction(KeyAction.RawBytes("-".toByteArray(Charsets.UTF_8))) }
        )

        VerticalBarDivider()

        // ====================================================================
        // 6. TOGGLE TECLADO VIRTUAL
        // ====================================================================
        KeyCapButton(
            label = "⌨",
            accentColor = CosmicViolet,
            textColor = TextPrimary,
            onClick = { triggerAction(KeyAction.ToggleSoftKeyboard) }
        )
    }
}

/**
 * Botón principal táctil de aprobación agéntica: [✓ Aprobar (Ctrl+K)].
 * Diseñado con gradiente Neón Cyan, fondo SurfaceElevated, borde cian radiante y feedback háptico.
 */
@Composable
private fun ApprovalButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.25f),
                        SurfaceElevated,
                        NeonCyan.copy(alpha = 0.15f)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = NeonCyan,
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = NeonCyan),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✓",
                color = NeonCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Aprobar",
                color = NeonCyan,
                style = KeyCapTextStyle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Botón de tecla táctil (KeyCap) con estética Cyber-Obsidian.
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
                customBackground.copy(alpha = 0.25f),
                customBackground.copy(alpha = 0.10f)
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
            .height(26.dp)
            .width(1.dp)
            .background(BorderObsidian)
    )
}
