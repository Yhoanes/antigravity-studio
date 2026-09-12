package com.antigravity.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.settings.AgentPermissionPolicy
import com.antigravity.studio.settings.AgentSettingsManager
import com.antigravity.studio.theme.AccentAmber
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.CyberObsidian
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextMuted
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

/**
 * Diálogo modal de Ajustes y Permisos de Gobernanza Agéntica.
 * Permite configurar las políticas de auto-aprobación del agente (lectura, escritura, bash seguro y destructivo)
 * con la estética Cyber-Obsidian.
 * Conforme a SPEC-003 §5.
 */
@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    settingsManager: AgentSettingsManager = AgentSettingsManager.getInstance(),
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val currentPolicy by settingsManager.policyFlow.collectAsState()

    // Estados locales para permitir cancelar sin guardar
    var autoApproveRead by remember(currentPolicy) { mutableStateOf(currentPolicy.autoApproveRead) }
    var autoApproveWrite by remember(currentPolicy) { mutableStateOf(currentPolicy.autoApproveWrite) }
    var autoApproveSafeBash by remember(currentPolicy) { mutableStateOf(currentPolicy.autoApproveSafeBash) }
    var requireApprovalForDestructive by remember(currentPolicy) {
        mutableStateOf(currentPolicy.requireApprovalForDestructive)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.width(540.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙️", fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = "Ajustes de Agente y Permisos",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                    Text(
                        text = "Gobernanza y auto-aprobación en Xiaomi Pad 6",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = BorderObsidian, thickness = 1.dp)

                // 1. Auto-aprobar lectura de archivos
                PermissionToggleRow(
                    title = "Auto-aprobar lectura de archivos",
                    description = "Ejecución transparente de cat, view_file, grep_search y listados.",
                    checked = autoApproveRead,
                    onCheckedChange = { autoApproveRead = it },
                    accentColor = NeonCyan
                )

                // 2. Auto-aprobar escritura/edición de archivos
                PermissionToggleRow(
                    title = "Auto-aprobar escritura/edición de archivos",
                    description = "Si está desactivado, suspende al agente y solicita [✓ Aprobar (Ctrl+K)].",
                    checked = autoApproveWrite,
                    onCheckedChange = { autoApproveWrite = it },
                    accentColor = AccentAmber
                )

                // 3. Auto-aprobar comandos bash seguros
                PermissionToggleRow(
                    title = "Auto-aprobar comandos bash seguros",
                    description = "Permite comandos de verificación y sólo lectura (ls, pwd, git status).",
                    checked = autoApproveSafeBash,
                    onCheckedChange = { autoApproveSafeBash = it },
                    accentColor = StatusSuccess
                )

                // 4. Requerir aprobación para comandos destructivos
                PermissionToggleRow(
                    title = "Requerir aprobación para comandos destructivos",
                    description = "Lista negra estricta (rm -rf, git reset --hard, dd, etc.). No eludible.",
                    checked = requireApprovalForDestructive,
                    onCheckedChange = { requireApprovalForDestructive = it },
                    accentColor = StatusError
                )

                HorizontalDivider(color = BorderObsidian, thickness = 1.dp)

                // Botón Restablecer valores por defecto
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = {
                            autoApproveRead = true
                            autoApproveWrite = false
                            autoApproveSafeBash = true
                            requireApprovalForDestructive = true
                        }
                    ) {
                        Text(
                            text = "↺ Restablecer valores por defecto",
                            color = CosmicViolet,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = AgentPermissionPolicy(
                        autoApproveRead = autoApproveRead,
                        autoApproveWrite = autoApproveWrite,
                        autoApproveSafeBash = autoApproveSafeBash,
                        requireApprovalForDestructive = requireApprovalForDestructive
                    )
                    settingsManager.savePolicy(updated)
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = CyberObsidian
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Guardar y Cerrar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(BorderObsidian)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text(text = "Cancelar", fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun PermissionToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceObsidian)
            .border(
                width = 1.dp,
                color = if (checked) accentColor.copy(alpha = 0.35f) else BorderObsidian,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberObsidian,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceElevated,
                    uncheckedBorderColor = BorderObsidian
                )
            )
        }
    }
}
