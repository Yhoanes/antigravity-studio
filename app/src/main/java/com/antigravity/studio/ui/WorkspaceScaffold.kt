package com.antigravity.studio.ui

import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.core.auth.AuthState
import com.antigravity.studio.ui.auth.GoogleAuthTopBarAction
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
import com.antigravity.studio.theme.CyberObsidian
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.StatusError
import com.antigravity.studio.theme.StatusSuccess
import com.antigravity.studio.theme.SurfaceElevated
import com.antigravity.studio.theme.SurfaceObsidian
import com.antigravity.studio.theme.TextMuted
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary
import com.antigravity.studio.updater.UpdateManager
import com.antigravity.studio.updater.UpdateStatus

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
 * - TopBar with Branding, Agent Status Badge, Session Tabs, Theme Selector & In-App OTA Updater.
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
    activeUserEmail: String? = null,
    onGoogleSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val updateStatus by UpdateManager.updateStatus.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    var isExplorerVisible by remember { mutableStateOf(false) }
    var splitFraction by remember { mutableFloatStateOf(0.72f) }
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isKeyboardShown by remember { mutableStateOf(false) }

    val cols by activeSession.cols.collectAsState()
    val rows by activeSession.rows.collectAsState()
    val pid by activeSession.pid.collectAsState()
    val cwd by activeSession.cwd.collectAsState()

    // Check updates once on initial launch
    LaunchedEffect(Unit) {
        UpdateManager.checkForUpdates(coroutineScope)
    }

    // Auto-prompt when update is discovered
    LaunchedEffect(updateStatus) {
        if (updateStatus is UpdateStatus.AVAILABLE) {
            showUpdateDialog = true
        }
    }

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
            .statusBarsPadding()
            .navigationBarsPadding()
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
            onSelectTheme = onThemePresetSelected,
            updateStatus = updateStatus,
            onShowUpdateDialog = { showUpdateDialog = true },
            onCheckUpdates = { UpdateManager.checkForUpdates(coroutineScope) },
            activeUserEmail = activeUserEmail,
            onGoogleSignInClick = onGoogleSignInClick,
            onSignOutClick = onSignOutClick
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
                            activeSession.writeCommand("!cat ${file.name}\r")
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
                                    if (isKeyboardShown) {
                                        keyboardController?.hide()
                                        isKeyboardShown = false
                                    } else {
                                        keyboardController?.show()
                                        isKeyboardShown = true
                                    }
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

    // In-App OTA Update Dialog
    if (showUpdateDialog) {
        UpdateDialog(
            status = updateStatus,
            onDismiss = { showUpdateDialog = false },
            onDownload = { url ->
                UpdateManager.downloadAndInstall(context, url, coroutineScope)
            },
            onRetry = {
                UpdateManager.checkForUpdates(coroutineScope)
            }
        )
    }
}

/**
 * TopBar Section containing Logo, Agent Badge, Tabs, OTA Updater, and Settings.
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
    onSelectTheme: (AppThemePreset) -> Unit,
    updateStatus: UpdateStatus,
    onShowUpdateDialog: () -> Unit,
    onCheckUpdates: () -> Unit,
    activeUserEmail: String? = null,
    onGoogleSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {}
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

            // Separator
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp)
                    .background(BorderObsidian)
            )

            // Google OAuth Action Component (SPEC-002)
            val authState = if (activeUserEmail != null) {
                AuthState.Authenticated(email = activeUserEmail)
            } else {
                AuthState.Unauthenticated
            }
            GoogleAuthTopBarAction(
                authState = authState,
                onSignInClick = onGoogleSignInClick,
                onSignOutClick = onSignOutClick,
                onSwitchAccountClick = onGoogleSignInClick
            )
        }

        // Right: In-App OTA Update Button & Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // OTA Update Pill when status is active
            when (val s = updateStatus) {
                is UpdateStatus.AVAILABLE -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentAmber.copy(alpha = 0.2f))
                            .border(1.dp, AccentAmber.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .clickable { onShowUpdateDialog() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✨", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPDATE ${s.version}",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                is UpdateStatus.DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCyan.copy(alpha = 0.2f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .clickable { onShowUpdateDialog() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⬇", color = NeonCyan, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${(s.progress * 100).toInt()}%",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                is UpdateStatus.READY_TO_INSTALL -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusSuccess.copy(alpha = 0.2f))
                            .border(1.dp, StatusSuccess.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .clickable { onShowUpdateDialog() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🚀 INSTALAR",
                            color = StatusSuccess,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                else -> Unit
            }

            // Settings & Theme Preset Menu
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
                    // Check for updates action
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🔄 Buscar Actualizaciones",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            onCheckUpdates()
                            showThemeMenu = false
                            onShowUpdateDialog()
                        }
                    )

                    HorizontalDivider(color = BorderObsidian)

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

/**
 * In-App OTA Update Dialog styled with Cyber-Obsidian.
 */
@Composable
private fun UpdateDialog(
    status: UpdateStatus,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        titleContentColor = NeonCyan,
        textContentColor = TextPrimary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🚀", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (status) {
                        is UpdateStatus.AVAILABLE -> "Actualización Disponible"
                        is UpdateStatus.DOWNLOADING -> "Descargando Actualización"
                        is UpdateStatus.READY_TO_INSTALL -> "Listo para Instalar"
                        is UpdateStatus.ERROR -> "Error de Actualización"
                        is UpdateStatus.UP_TO_DATE -> "Sistema al Día"
                        else -> "Buscando Actualizaciones"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (status) {
                    is UpdateStatus.AVAILABLE -> {
                        Text(
                            text = "Nueva versión: ${status.version} (Actual: ${UpdateManager.CURRENT_VERSION})",
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Novedades y cambios:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceObsidian)
                                .border(1.dp, BorderObsidian, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = status.releaseNotes,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    is UpdateStatus.DOWNLOADING -> {
                        Text(
                            text = "Descargando paquete OTA desde GitHub...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NeonCyan,
                            trackColor = SurfaceObsidian
                        )
                        Text(
                            text = "${(status.progress * 100).toInt()}% completado",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    is UpdateStatus.READY_TO_INSTALL -> {
                        Text(
                            text = "La descarga se completó. El instalador del paquete Android se abrirá a continuación para instalar la actualización.",
                            color = StatusSuccess,
                            fontSize = 12.sp
                        )
                    }
                    is UpdateStatus.ERROR -> {
                        Text(
                            text = "Ocurrió un error: ${status.message}",
                            color = StatusError,
                            fontSize = 12.sp
                        )
                    }
                    is UpdateStatus.UP_TO_DATE -> {
                        Text(
                            text = "¡Estás al día! Tienes la última versión (${status.currentVersion}) instalada en tu Xiaomi Pad 6.",
                            color = StatusSuccess,
                            fontSize = 12.sp
                        )
                    }
                    else -> {
                        Text(
                            text = "Consultando repositorio GitHub...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (status) {
                is UpdateStatus.AVAILABLE -> {
                    Button(
                        onClick = { onDownload(status.downloadUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = CyberObsidian
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Descargar e Instalar", fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.ERROR -> {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = CyberObsidian
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Reintentar")
                    }
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (status is UpdateStatus.AVAILABLE) "Más tarde" else "Cerrar",
                    color = TextSecondary
                )
            }
        }
    )
}
