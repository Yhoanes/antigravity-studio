package com.antigravity.studio.ui

import com.antigravity.studio.theme.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.model.AgentSessionState
import com.antigravity.studio.model.AgentStatus
import com.antigravity.studio.model.GitFileStatus
import com.antigravity.studio.model.ProjectFile
import com.antigravity.studio.model.TerminalSession
import com.antigravity.studio.theme.AccentAmber
import com.antigravity.studio.theme.AgentBadgeTextStyle
import com.antigravity.studio.theme.AppThemePreset
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.CosmicViolet
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextMuted
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

/**
 * State governing the adaptive tablet multi-panel layout.
 */
data class AdaptiveTabletScaffoldState(
    val splitFraction: Float = 0.72f,
    val isInspectorExpanded: Boolean = true,
    val isLandscape: Boolean = true
)

/**
 * Adaptive layout container splitting terminal and inspector slots.
 */
@Composable
fun AdaptiveTabletScaffold(
    modifier: Modifier = Modifier,
    state: AdaptiveTabletScaffoldState,
    onSplitFractionChange: (Float) -> Unit,
    toolbar: @Composable () -> Unit,
    terminalSlot: @Composable () -> Unit,
    inspectorSlot: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val isTabletLandscape = totalWidth >= 700.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Main middle area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.isInspectorExpanded && isTabletLandscape) {
                    val inspectorFraction = (1f - state.splitFraction).coerceIn(0.20f, 0.45f)

                    // Inspector panel (left side)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(totalWidth * inspectorFraction)
                    ) {
                        inspectorSlot()
                    }

                    // Draggable splitter handle
                    SplitterHandle(
                        onDelta = { deltaPx ->
                            val deltaFraction = deltaPx / totalWidth.value
                            val newFraction = (state.splitFraction - deltaFraction).coerceIn(0.55f, 0.80f)
                            onSplitFractionChange(newFraction)
                        }
                    )
                }

                // Terminal slot
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    terminalSlot()
                }
            }

            // Bottom toolbar / productivity bar
            toolbar()
        }
    }
}

/**
 * High-precision Draggable Splitter Handle between panels.
 */
@Composable
private fun SplitterHandle(
    onDelta: (Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(6.dp)
            .background(if (isDragging) NeonCyan.copy(alpha = 0.5f) else BorderObsidian)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    onDelta(delta)
                },
                onDragStarted = { isDragging = true },
                onDragStopped = { isDragging = false }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Subtle vertical grip indicator
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(if (isDragging) NeonCyan else TextSecondary.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(1.dp))
        )
    }
}

/**
 * Root Workspace Scaffold for Antigravity Studio.
 *
 * Implements the complete studio layout:
 * - TopBar with Branding, Agent Status Badge, Session Tabs, Theme Selector.
 * - Central Area with ProjectExplorer and TerminalSurface.
 * - Bottom Area with ProductivityBar and Session Status line.
 */
@Composable
fun WorkspaceScaffold(
    activeSession: TerminalSession,
    sessions: List<TerminalSession>,
    onSelectSession: (TerminalSession) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (TerminalSession) -> Unit,
    onThemePresetSelected: (AppThemePreset) -> Unit,
    agentState: AgentSessionState = AgentSessionState(status = AgentStatus.READY),
    modifier: Modifier = Modifier
) {
    var isExplorerVisible by remember { mutableStateOf(true) }
    var splitFraction by remember { mutableFloatStateOf(0.72f) }
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    val cols by activeSession.cols.collectAsState()
    val rows by activeSession.rows.collectAsState()
    val pid by activeSession.pid.collectAsState()
    val cwd by activeSession.cwd.collectAsState()

    // Sample project workspace files
    val workspaceFiles = remember {
        listOf(
            ProjectFile(
                name = "specs",
                path = "/workspace/specs",
                isDirectory = true,
                isExpanded = true,
                children = listOf(
                    ProjectFile("00-system-architecture.md", "/workspace/specs/00-system-architecture.md", false),
                    ProjectFile("01-app-blueprint.md", "/workspace/specs/01-app-blueprint.md", false)
                )
            ),
            ProjectFile(
                name = "app",
                path = "/workspace/app",
                isDirectory = true,
                isExpanded = true,
                children = listOf(
                    ProjectFile(
                        name = "src/main/java",
                        path = "/workspace/app/src/main/java",
                        isDirectory = true,
                        isExpanded = true,
                        children = listOf(
                            ProjectFile("MainActivity.kt", "/workspace/app/.../MainActivity.kt", false, gitStatus = GitFileStatus.MODIFIED),
                            ProjectFile("WorkspaceScaffold.kt", "/workspace/app/.../WorkspaceScaffold.kt", false, gitStatus = GitFileStatus.STAGED),
                            ProjectFile("TerminalSurface.kt", "/workspace/app/.../TerminalSurface.kt", false, gitStatus = GitFileStatus.STAGED),
                            ProjectFile("ProductivityBar.kt", "/workspace/app/.../ProductivityBar.kt", false, gitStatus = GitFileStatus.STAGED)
                        )
                    ),
                    ProjectFile(
                        name = "src/main/assets/terminal",
                        path = "/workspace/app/src/main/assets/terminal",
                        isDirectory = true,
                        children = listOf(
                            ProjectFile("terminal.html", "/workspace/app/.../terminal.html", false)
                        )
                    )
                )
            ),
            ProjectFile(
                name = "harness",
                path = "/workspace/harness",
                isDirectory = true,
                children = listOf(
                    ProjectFile("harness_runner.py", "/workspace/harness/harness_runner.py", false),
                    ProjectFile("spec_validator.py", "/workspace/harness/spec_validator.py", false)
                )
            ),
            ProjectFile("AGENTS.md", "/workspace/AGENTS.md", false),
            ProjectFile("GEMINI.md", "/workspace/GEMINI.md", false)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceObsidian)
    ) {
        // --- 1. TOPBAR ---
        TopBarSection(
            activeSession = activeSession,
            sessions = sessions,
            onSelectSession = onSelectSession,
            onNewSession = onNewSession,
            onCloseSession = onCloseSession,
            agentStatus = agentState.status,
            isExplorerVisible = isExplorerVisible,
            onToggleExplorer = { isExplorerVisible = !isExplorerVisible },
            onSelectTheme = onThemePresetSelected
        )

        // --- 2. CENTRAL WORKSPACE LAYOUT (Scaffold) ---
        AdaptiveTabletScaffold(
            modifier = Modifier.weight(1f),
            state = AdaptiveTabletScaffoldState(
                splitFraction = splitFraction,
                isInspectorExpanded = isExplorerVisible
            ),
            onSplitFractionChange = { newFraction -> splitFraction = newFraction },
            inspectorSlot = {
                ProjectExplorer(
                    workspaceRoot = "antigravity",
                    files = workspaceFiles,
                    onFileSelected = { file ->
                        if (!file.isDirectory) {
                            activeSession.writeCommand("cat ${file.name}\r")
                        }
                    }
                )
            },
            terminalSlot = {
                TerminalSurface(
                    session = activeSession,
                    modifier = Modifier.fillMaxSize()
                )
            },
            toolbar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Productivity Bar
                    ProductivityBar(
                        isCtrlActive = isCtrlActive,
                        isAltActive = isAltActive,
                        onKeyAction = { action ->
                            when (action) {
                                is KeyAction.ToggleCtrl -> isCtrlActive = !isCtrlActive
                                is KeyAction.ToggleAlt -> isAltActive = !isAltActive
                                is KeyAction.ShortcutCommand -> {
                                    activeSession.writeCommand(action.commandText)
                                }
                                is KeyAction.RawBytes -> {
                                    if (isCtrlActive && action.bytes.isNotEmpty()) {
                                        val b = action.bytes[0].toInt()
                                        // If lower or upper alpha, convert to CTRL code
                                        val ctrlByte = when (b) {
                                            in 97..122 -> (b - 96).toByte()
                                            in 65..90 -> (b - 64).toByte()
                                            else -> action.bytes[0]
                                        }
                                        activeSession.writeInput(byteArrayOf(ctrlByte))
                                        isCtrlActive = false
                                    } else if (isAltActive) {
                                        activeSession.writeInput(byteArrayOf(0x1B) + action.bytes)
                                        isAltActive = false
                                    } else {
                                        activeSession.writeInput(action.bytes)
                                    }
                                }
                                is KeyAction.ToggleSoftKeyboard -> {
                                    // Soft keyboard toggle
                                }
                            }
                        }
                    )

                    // Bottom Status Line
                    TerminalStatusBar(
                        cols = cols,
                        rows = rows,
                        pid = pid,
                        cwd = cwd
                    )
                }
            }
        )
    }
}

/**
 * TopBar Section containing Logo, Agent Badge, Tabs, and Settings.
 */
@Composable
private fun TopBarSection(
    activeSession: TerminalSession,
    sessions: List<TerminalSession>,
    onSelectSession: (TerminalSession) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (TerminalSession) -> Unit,
    agentStatus: AgentStatus,
    isExplorerVisible: Boolean,
    onToggleExplorer: () -> Unit,
    onSelectTheme: (AppThemePreset) -> Unit
) {
    var showThemeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(SurfaceElevated)
            .border(width = 1.dp, color = BorderObsidian)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Branding & Agent Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Explorer toggle button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isExplorerVisible) NeonCyan.copy(alpha = 0.2f) else SurfaceObsidian)
                    .border(
                        1.dp,
                        if (isExplorerVisible) NeonCyan.copy(alpha = 0.6f) else BorderObsidian,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleExplorer() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☰",
                    color = if (isExplorerVisible) NeonCyan else TextPrimary,
                    fontSize = 16.sp
                )
            }

            // Antigravity Logo & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ANTIGRAVITY",
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "STUDIO",
                    color = CosmicViolet,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Agent Status Badge
            AgentStatusBadge(status = agentStatus)
        }

        // Center: Session Tabs
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sessions.forEach { session ->
                val isSelected = session.id == activeSession.id
                SessionTabPill(
                    session = session,
                    isSelected = isSelected,
                    onSelect = { onSelectSession(session) },
                    onClose = { if (sessions.size > 1) onCloseSession(session) }
                )
            }

            // Add new session (+) button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceObsidian)
                    .border(1.dp, BorderObsidian, RoundedCornerShape(6.dp))
                    .clickable { onNewSession() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Right: Settings & Theme Preset Menu
        Box {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceObsidian)
                    .border(1.dp, BorderObsidian, RoundedCornerShape(6.dp))
                    .clickable { showThemeMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙️",
                    fontSize = 14.sp
                )
            }

            DropdownMenu(
                expanded = showThemeMenu,
                onDismissRequest = { showThemeMenu = false },
                modifier = Modifier.background(SurfaceElevated)
            ) {
                AppThemePreset.values().forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = preset.displayName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            onSelectTheme(preset)
                            showThemeMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Animated Agent Status Badge reflecting real-time autonomous agent state.
 */
@Composable
private fun AgentStatusBadge(status: AgentStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val shape = RoundedCornerShape(12.dp)
    val alpha = if (status.isAnimated) pulseAlpha else 1.0f

    Box(
        modifier = Modifier
            .clip(shape)
            .background(status.badgeColor.copy(alpha = 0.18f * alpha))
            .border(
                width = 1.dp,
                color = status.badgeColor.copy(alpha = 0.65f * alpha),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status.badgeText,
            style = AgentBadgeTextStyle,
            color = status.badgeColor
        )
    }
}

/**
 * Interactive Session Tab Pill with close action.
 */
@Composable
private fun SessionTabPill(
    session: TerminalSession,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val bg = if (isSelected) SurfaceObsidian else SurfaceElevated.copy(alpha = 0.6f)
    val border = if (isSelected) NeonCyan.copy(alpha = 0.7f) else BorderObsidian

    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable { onSelect() }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = session.title,
            color = if (isSelected) NeonCyan else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )

        // Close 'x'
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Bottom Terminal Status Bar displaying latency, geometry and PID.
 */
@Composable
private fun TerminalStatusBar(
    cols: Int,
    rows: Int,
    pid: Int,
    cwd: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(CyberObsidian)
            .border(width = 0.5.dp, color = BorderObsidian)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "● 144Hz WebGL",
                color = StatusSuccess,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "LATENCY: ≤ 6.9ms",
                color = NeonCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "DIM: ${cols}x${rows}",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "PID: $pid",
                color = CosmicViolet,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = cwd,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
