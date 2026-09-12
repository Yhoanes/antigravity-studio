package com.antigravity.studio.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.model.ide.FileChangeSummary
import com.antigravity.studio.model.ide.SubagentCardData
import com.antigravity.studio.model.ide.SubagentExecutionStatus
import com.antigravity.studio.service.HyperOsTelemetryState
import com.antigravity.studio.service.StudioBackgroundService
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
 * Panel Auxiliar Derecho de Subagentes, Archivos Modificados y Persistencia HyperOS.
 * Diseñado para la visualización e inspección agéntica en Xiaomi Pad 6.
 * Conforme a SPEC-003 §4.4 y §6.3.
 */
@Composable
fun AuxiliaryDrawer(
    modifier: Modifier = Modifier,
    subagents: List<SubagentCardData> = emptyList(),
    filesChanged: List<FileChangeSummary> = emptyList(),
    onFileClick: ((String) -> Unit)? = null,
    onCloseDrawer: (() -> Unit)? = null
) {
    val telemetry by StudioBackgroundService.telemetryFlow.collectAsState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(SurfaceObsidian)
            .border(width = 1.dp, color = BorderObsidian)
    ) {
        // ====================================================================
        // 1. HEADER: "Active Session & Agents"
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(SurfaceElevated)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "🤖", fontSize = 14.sp)
                Text(
                    text = "Active Session & Agents",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (onCloseDrawer != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SurfaceObsidian)
                        .clickable(onClick = onCloseDrawer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "×",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ====================================================================
        // CUERPO SCROLLABLE: Subagents + Files Changed
        // ====================================================================
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- SECCIÓN: SUBAGENTS ---
            item {
                SectionHeaderTitle(
                    title = "SUBAGENTS",
                    count = subagents.size,
                    accentColor = CosmicViolet
                )
            }

            if (subagents.isEmpty()) {
                item {
                    Text(
                        text = "No hay subagentes activos",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            } else {
                items(subagents, key = { it.id }) { agent ->
                    SubagentCard(subagent = agent)
                }
            }

            // --- SECCIÓN: FILES CHANGED ---
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SectionHeaderTitle(
                    title = "FILES CHANGED",
                    count = filesChanged.size,
                    accentColor = NeonCyan
                )
            }

            if (filesChanged.isEmpty()) {
                item {
                    Text(
                        text = "Sin archivos modificados",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            } else {
                items(filesChanged, key = { it.relativePath }) { fileChange ->
                    FilesChangedCard(
                        change = fileChange,
                        onClick = { onFileClick?.invoke(fileChange.relativePath) }
                    )
                }
            }
        }

        HorizontalDivider(color = BorderObsidian, thickness = 1.dp)

        // ====================================================================
        // PIE: INDICADOR DE PERSISTENCIA HYPEROS
        // ====================================================================
        HyperOsPersistenceFooter(telemetry = telemetry)
    }
}

@Composable
private fun SectionHeaderTitle(
    title: String,
    count: Int,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.18f))
                .border(0.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = count.toString(),
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Tarjeta para visualizar el estado y duración de cada Subagente.
 */
@Composable
fun SubagentCard(
    subagent: SubagentCardData,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    val transition = rememberInfiniteTransition(label = "pulseExecuting")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isRunning = subagent.status == SubagentExecutionStatus.EXECUTING

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isRunning) NeonCyan.copy(alpha = 0.6f * pulseAlpha) else BorderObsidian,
                shape = shape
            )
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Fila superior: Nombre con duración + Badge de Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${subagent.name} (${subagent.durationFormatted})",
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Badge de estado
                SubagentStatusBadge(
                    status = subagent.status,
                    pulseAlpha = pulseAlpha
                )
            }

            // Descripción o rol del subagente
            Text(
                text = subagent.roleDescription,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Barra de progreso si está ejecutando o con progreso > 0
            if (isRunning || subagent.progressPercent > 0f) {
                val progress = if (isRunning && subagent.progressPercent <= 0f) {
                    0.65f
                } else {
                    subagent.progressPercent
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = NeonCyan,
                    trackColor = SurfaceObsidian
                )
            }
        }
    }
}

@Composable
private fun SubagentStatusBadge(
    status: SubagentExecutionStatus,
    pulseAlpha: Float
) {
    val shape = RoundedCornerShape(4.dp)

    val (badgeText, badgeColor, bgAlpha) = when (status) {
        SubagentExecutionStatus.COMPLETED -> Triple("[Completed ✓]", StatusSuccess, 0.18f)
        SubagentExecutionStatus.EXECUTING -> Triple("[Running ⟳]", NeonCyan, 0.20f * pulseAlpha)
        SubagentExecutionStatus.REASONING -> Triple("[Reasoning 🟣]", CosmicViolet, 0.18f)
        SubagentExecutionStatus.IDLE -> Triple("[idle]", TextSecondary, 0.12f)
        SubagentExecutionStatus.FAILED -> Triple("[Failed 🔴]", StatusError, 0.18f)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(badgeColor.copy(alpha = bgAlpha))
            .border(0.5.dp, badgeColor.copy(alpha = 0.5f), shape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Tarjeta para visualizar archivos modificados en la sesión activa.
 */
@Composable
fun FilesChangedCard(
    change: FileChangeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceElevated)
            .border(1.dp, BorderObsidian, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = "📝",
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            Text(
                text = change.relativePath,
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Badge con número de líneas añadidas (+124 en verde)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(StatusSuccess.copy(alpha = 0.15f))
                .border(0.5.dp, StatusSuccess.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = change.linesSummary,
                color = StatusSuccess,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Pie del panel auxiliar con la telemetría del servicio en segundo plano de HyperOS.
 */
@Composable
private fun HyperOsPersistenceFooter(
    telemetry: HyperOsTelemetryState
) {
    val isWakelockActive = telemetry.isWakeLockHeld || telemetry.isServiceRunning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Chip de estado elegante
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceObsidian)
                .border(
                    width = 0.5.dp,
                    color = if (isWakelockActive) StatusSuccess.copy(alpha = 0.4f) else BorderObsidian,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isWakelockActive) StatusSuccess else AccentAmber)
            )

            Text(
                text = if (isWakelockActive) {
                    "Running background service (HyperOS WakeLock active)"
                } else {
                    "HyperOS background service (Idle / Ready)"
                },
                color = if (isWakelockActive) StatusSuccess else TextSecondary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Fila de telemetría de memoria RAM y Locks
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // RAM Usada
            val ramDisplay = if (telemetry.processRamUsageMb > 0L) {
                "${telemetry.processRamUsageMb} MB / ${telemetry.totalDeviceRamMb} MB"
            } else {
                "480 MB / 6144 MB"
            }

            Text(
                text = "RAM: $ramDisplay",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            // WakeLock status pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonCyan.copy(alpha = 0.12f))
                    .border(0.5.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "⚡ WakeLock ON",
                    color = NeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
