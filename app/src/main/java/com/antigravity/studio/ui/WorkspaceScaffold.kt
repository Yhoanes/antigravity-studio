package com.antigravity.studio.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.antigravity.studio.model.AgentSessionState
import com.antigravity.studio.model.AgentStatus
import com.antigravity.studio.model.AntigravityModelCatalog
import com.antigravity.studio.model.TerminalSession
import com.antigravity.studio.model.ide.CanvasDisplayMode
import com.antigravity.studio.model.ide.CanvasTabType
import com.antigravity.studio.project.ProjectItem
import com.antigravity.studio.project.ProjectManager
import com.antigravity.studio.settings.AgentSettingsManager
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
import com.antigravity.studio.ui.auth.GoogleAuthTopBarAction
import com.antigravity.studio.updater.UpdateManager
import com.antigravity.studio.updater.UpdateStatus
import java.io.File

/**
 * Root Workspace Scaffold for Antigravity Studio IDE on Xiaomi Pad 6 (11" 2.8K 144Hz).
 *
 * Estructura de 3 paneles y Dual Canvas:
 * - TopBar: Branding Neón Cyan, breadcrumb `~/projects/<name>`, cuenta Google, badge dorado `Google AI Ultra`,
 *   badge de modelo activo, sesiones y OTA updater.
 * - LeftSidebar: `ProjectsSidebar` con proyectos y árbol jerárquico de archivos.
 * - Central Dual Canvas: Pestañas conmutables entre `Agent Terminal (144Hz)` y `Workspace Canvas (Code/Diff)`.
 * - RightDrawer: `AuxiliaryDrawer` con subagentes activos, archivos modificados y telemetría HyperOS.
 * - Bottom: `ProductivityBar` con `[✓ Aprobar (Ctrl+K)]`, `[⚡ Modelo]`, `[📁 Archivos]`, `[⏹ Detener]`, `[⚙ Ajustes]`.
 * - Diálogos integrados: `SettingsDialog`, `CreateProjectDialog` y `ModelSelectionSheet`.
 *
 * Conforme a SPEC-003 §4.
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

    // Gestor de proyectos reactivo
    val projectManager = remember { ProjectManager.getInstance() }
    val activeProject by projectManager.activeProject.collectAsState()

    // Actualizaciones OTA
    val updateStatus by UpdateManager.updateStatus.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Catálogo de modelos
    val currentModel by AntigravityModelCatalog.selectedModel.collectAsState()
    var showModelSheet by remember { mutableStateOf(false) }

    // Diálogo de Ajustes y Permisos
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Visibilidad de paneles laterales
    var isLeftSidebarVisible by remember { mutableStateOf(true) }
    var isRightDrawerVisible by remember { mutableStateOf(true) }

    // Dual Canvas: Estado de pestañas centrales
    var activeCanvasTab by remember { mutableStateOf(CanvasTabType.AGENT_TERMINAL) }
    var currentOpenFile by remember { mutableStateOf<File?>(null) }

    // Modificadores de teclado
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isKeyboardShown by remember { mutableStateOf(false) }

    // Métricas de terminal activa
    val cols by activeSession.cols.collectAsState()
    val rows by activeSession.rows.collectAsState()
    val pid by activeSession.pid.collectAsState()
    val cwd by activeSession.cwd.collectAsState()

    // Selección por defecto del archivo principal al cambiar de proyecto
    LaunchedEffect(activeProject?.id) {
        val root = activeProject?.let { File(it.absolutePath) }
        if (root != null && root.exists()) {
            val candidate = File(root, "main.py").takeIf { it.exists() }
                ?: File(root, "app.py").takeIf { it.exists() }
                ?: File(root, "analyzer.py").takeIf { it.exists() }
                ?: File(root, "README.md").takeIf { it.exists() }
                ?: root.listFiles()?.firstOrNull { it.isFile }
            currentOpenFile = candidate
        }
    }

    // Comprobación de actualizaciones OTA al iniciar
    LaunchedEffect(Unit) {
        UpdateManager.checkForUpdates(coroutineScope)
    }

    LaunchedEffect(updateStatus) {
        if (updateStatus is UpdateStatus.AVAILABLE) {
            showUpdateDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ====================================================================
        // 1. TOPBAR MULTI-COMPONENTE CON BREADCRUMB Y BADGE GOLDEN ULTRA
        // ====================================================================
        TopBarSection(
            activeSession = activeSession,
            sessions = sessions,
            onSelectSession = onSelectSession,
            onNewSession = onNewSession,
            onCloseSession = onCloseSession,
            agentStatus = agentState.status,
            activeProject = activeProject,
            activeUserEmail = activeUserEmail,
            activeModelName = currentModel.displayName,
            isLeftSidebarVisible = isLeftSidebarVisible,
            onToggleLeftSidebar = { isLeftSidebarVisible = !isLeftSidebarVisible },
            isRightDrawerVisible = isRightDrawerVisible,
            onToggleRightDrawer = { isRightDrawerVisible = !isRightDrawerVisible },
            onSelectTheme = onThemePresetSelected,
            updateStatus = updateStatus,
            onShowUpdateDialog = { showUpdateDialog = true },
            onCheckUpdates = { UpdateManager.checkForUpdates(coroutineScope) },
            onGoogleSignInClick = onGoogleSignInClick,
            onSignOutClick = onSignOutClick,
            onOpenModelSheet = { showModelSheet = true },
            onOpenSettings = { showSettingsDialog = true }
        )

        // ====================================================================
        // 2. DISPOSICIÓN PRINCIPAL DE 3 PANELES (TRIPLE PANEL WORKSPACE)
        // ====================================================================
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val totalWidth = maxWidth
            val isWideScreen = totalWidth >= 800.dp

            Row(modifier = Modifier.fillMaxSize()) {
                // --- PANEL IZQUIERDO: Projects & Files Sidebar ---
                AnimatedVisibility(
                    visible = isLeftSidebarVisible,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    ProjectsSidebar(
                        projectManager = projectManager,
                        onFileSelected = { file ->
                            currentOpenFile = file
                            activeCanvasTab = CanvasTabType.WORKSPACE_CODE
                        },
                        onProjectChanged = { proj ->
                            val root = File(proj.absolutePath)
                            currentOpenFile = root.listFiles()?.firstOrNull { it.isFile }
                        }
                    )
                }

                // --- PANEL CENTRAL: Dual Canvas (Tabs Terminal / Code) ---
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(CyberObsidian)
                ) {
                    // Selector superior de pestañas del Dual Canvas
                    DualCanvasTabBar(
                        activeTab = activeCanvasTab,
                        onTabSelected = { activeCanvasTab = it },
                        currentOpenFile = currentOpenFile
                    )

                    // Contenido del Canvas Central según pestaña activa
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (activeTabState(activeCanvasTab)) {
                            CanvasTabType.AGENT_TERMINAL -> {
                                TerminalSurface(
                                    session = activeSession,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            CanvasTabType.WORKSPACE_CODE -> {
                                WorkspaceCodeCanvas(
                                    file = currentOpenFile,
                                    projectName = activeProject?.name,
                                    onRunFile = { file ->
                                        activeCanvasTab = CanvasTabType.AGENT_TERMINAL
                                        activeSession.writeCommand("python3 ${file.name}\r")
                                    }
                                )
                            }
                        }
                    }
                }

                // --- PANEL DERECHO: AuxiliaryDrawer (Subagents & HyperOS) ---
                AnimatedVisibility(
                    visible = isRightDrawerVisible && isWideScreen,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    AuxiliaryDrawer(
                        onFileClick = { path ->
                            val root = activeProject?.let { File(it.absolutePath) }
                            val targetFile = if (root != null) File(root, path) else File(path)
                            currentOpenFile = targetFile
                            activeCanvasTab = CanvasTabType.WORKSPACE_CODE
                        },
                        onCloseDrawer = { isRightDrawerVisible = false }
                    )
                }
            }
        }

        // ====================================================================
        // 3. BARRA INFERIOR: ProductivityBar y TerminalStatusBar
        // ====================================================================
        Column(modifier = Modifier.fillMaxWidth()) {
            ProductivityBar(
                isCtrlActive = isCtrlActive,
                isAltActive = isAltActive,
                onApprove = {
                    // Emite \u000B (Ctrl+K) directamente al stream PTY
                    activeSession.writeInput(byteArrayOf(0x0B))
                },
                onOpenModelSelector = {
                    showModelSheet = true
                },
                onToggleSidebarFiles = {
                    isLeftSidebarVisible = !isLeftSidebarVisible
                },
                onStopExecution = {
                    // Emite \u0003 (SIGINT / Ctrl+C)
                    activeSession.writeInput(byteArrayOf(0x03))
                },
                onOpenSettings = {
                    showSettingsDialog = true
                },
                onKeyAction = { action ->
                    when (action) {
                        is KeyAction.ToggleCtrl -> isCtrlActive = !isCtrlActive
                        is KeyAction.ToggleAlt -> isAltActive = !isAltActive
                        is KeyAction.ShortcutCommand -> {
                            activeSession.writeCommand(action.commandText)
                        }
                        is KeyAction.Approve -> {
                            activeSession.writeInput(byteArrayOf(0x0B))
                        }
                        is KeyAction.StopExecution -> {
                            activeSession.writeInput(byteArrayOf(0x03))
                        }
                        is KeyAction.OpenModelSelector -> {
                            showModelSheet = true
                        }
                        is KeyAction.ToggleSidebarFiles -> {
                            isLeftSidebarVisible = !isLeftSidebarVisible
                        }
                        is KeyAction.OpenSettings -> {
                            showSettingsDialog = true
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

            // Status bar de terminal
            TerminalStatusBar(
                cols = cols,
                rows = rows,
                pid = pid,
                cwd = cwd,
                activeModel = currentModel.displayName,
                onModelClick = { showModelSheet = true }
            )
        }
    }

    // Diálogo de Ajustes y Permisos de Gobernanza
    if (showSettingsDialog) {
        SettingsDialog(
            isOpen = showSettingsDialog,
            onDismissRequest = { showSettingsDialog = false }
        )
    }

    // Modal Bottom Sheet de Selección de Modelo Generativo
    if (showModelSheet) {
        ModelSelectionSheet(
            selectedModel = currentModel,
            onSelectModel = { model ->
                AntigravityModelCatalog.selectModel(model)
                showModelSheet = false
            },
            onDismissRequest = { showModelSheet = false }
        )
    }

    // Diálogo de Actualización In-App OTA
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

private fun activeTabState(tab: CanvasTabType): CanvasTabType = tab

/**
 * Selector superior de pestañas del Dual Canvas: [Agent Terminal (144Hz)] | [Workspace Canvas (Code/Diff)].
 */
@Composable
private fun DualCanvasTabBar(
    activeTab: CanvasTabType,
    onTabSelected: (CanvasTabType) -> Unit,
    currentOpenFile: File?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(SurfaceElevated)
            .border(width = 0.5.dp, color = BorderObsidian)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pestaña 1: Agent Terminal
        CanvasTabPill(
            title = "⚡ Agent Terminal (144Hz)",
            isSelected = activeTab == CanvasTabType.AGENT_TERMINAL,
            onClick = { onTabSelected(CanvasTabType.AGENT_TERMINAL) }
        )

        // Pestaña 2: Workspace Canvas (Code/Diff)
        val codeTitle = if (currentOpenFile != null) {
            "📄 ${currentOpenFile.name}"
        } else {
            "Workspace Canvas"
        }
        CanvasTabPill(
            title = codeTitle,
            isSelected = activeTab == CanvasTabType.WORKSPACE_CODE,
            onClick = { onTabSelected(CanvasTabType.WORKSPACE_CODE) }
        )
    }
}

@Composable
private fun CanvasTabPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val bg = if (isSelected) CyberObsidian else SurfaceElevated.copy(alpha = 0.5f)
    val border = if (isSelected) NeonCyan.copy(alpha = 0.8f) else BorderObsidian

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(width = 1.dp, color = border, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) NeonCyan else TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Visor de Código Fuente y Archivos (Workspace Canvas) a alta resolución.
 */
@Composable
private fun WorkspaceCodeCanvas(
    file: File?,
    projectName: String?,
    onRunFile: ((File) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (file == null || !file.exists() || file.isDirectory) {
        // Estado inicial / vacío
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(CyberObsidian)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "🌌", fontSize = 32.sp)
                Text(
                    text = "Workspace Canvas",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Proyecto activo: ${projectName ?: "sin seleccionar"}\nSelecciona un archivo del explorador lateral para inspeccionar su código.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val content = remember(file.lastModified(), file.absolutePath) {
        try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            "// Error al leer archivo: ${e.message}"
        }
    }

    val lines = remember(content) { content.lines() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberObsidian)
    ) {
        // Barra superior del archivo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(SurfaceObsidian)
                .border(width = 0.5.dp, color = BorderObsidian)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${projectName ?: ""}/${file.name} (${lines.size} líneas)",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            if (file.name.endsWith(".py") && onRunFile != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusSuccess.copy(alpha = 0.15f))
                        .border(0.5.dp, StatusSuccess.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable { onRunFile(file) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "▶ Run with Python",
                        color = StatusSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Vista de líneas de código numeradas
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                ) {
                    // Número de línea
                    Text(
                        text = (index + 1).toString().padStart(4, ' '),
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(36.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Línea de código
                    Text(
                        text = line,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * TopBar con Branding, Breadcrumb de proyecto, Google OAuth, Badge Golden Ultra,
 * badge de modelo activo, sesiones y settings.
 */
@Composable
private fun TopBarSection(
    activeSession: TerminalSession,
    sessions: List<TerminalSession>,
    onSelectSession: (TerminalSession) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (TerminalSession) -> Unit,
    agentStatus: AgentStatus,
    activeProject: ProjectItem?,
    activeUserEmail: String?,
    activeModelName: String,
    isLeftSidebarVisible: Boolean,
    onToggleLeftSidebar: () -> Unit,
    isRightDrawerVisible: Boolean,
    onToggleRightDrawer: () -> Unit,
    onSelectTheme: (AppThemePreset) -> Unit,
    updateStatus: UpdateStatus,
    onShowUpdateDialog: () -> Unit,
    onCheckUpdates: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onOpenSettings: () -> Unit
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
        // IZQUIERDA: Toggle Sidebar, Logo y Breadcrumb de proyecto
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón toggle barra lateral izquierda
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLeftSidebarVisible) NeonCyan.copy(alpha = 0.2f) else SurfaceObsidian)
                    .border(
                        1.dp,
                        if (isLeftSidebarVisible) NeonCyan.copy(alpha = 0.6f) else BorderObsidian,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleLeftSidebar() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📁",
                    fontSize = 15.sp
                )
            }

            // Antigravity Logo & Branding
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ANTIGRAVITY",
                    color = NeonCyan,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "STUDIO",
                    color = CosmicViolet,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Breadcrumb del proyecto activo: ~/projects/<name>
            val breadcrumbText = activeProject?.name?.let { "~/projects/$it" } ?: "~/projects"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceObsidian)
                    .border(0.5.dp, BorderObsidian, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = breadcrumbText,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Agent Status Badge
            AgentStatusBadge(status = agentStatus)
        }

        // CENTRO: Pestañas de sesión + Google OAuth Action + Badge Golden Ultra
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
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

            // Botón (+) nueva sesión
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

            // Separador
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
                AuthState.Authenticated(email = "shadrick1212@gmail.com") // fallback usuario activo
            }
            GoogleAuthTopBarAction(
                authState = authState,
                onSignInClick = onGoogleSignInClick,
                onSignOutClick = onSignOutClick,
                onSwitchAccountClick = onGoogleSignInClick
            )

            // ================================================================
            // BADGE DORADO GOOGLE AI ULTRA
            // ================================================================
            GoogleAiUltraBadge()
        }

        // DERECHA: Badge Modelo Activo + Botón Drawer Derecho + Settings/Themes
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Badge de modelo activo
            ActiveModelBadge(
                modelName = activeModelName,
                onClick = onOpenModelSheet
            )

            // Toggle Drawer Derecho (Subagentes)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isRightDrawerVisible) CosmicViolet.copy(alpha = 0.2f) else SurfaceObsidian)
                    .border(
                        1.dp,
                        if (isRightDrawerVisible) CosmicViolet.copy(alpha = 0.6f) else BorderObsidian,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleRightDrawer() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🤖", fontSize = 14.sp)
            }

            // Menu de Temas, Updater y Ajustes
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
                    Text(text = "⚙️", fontSize = 14.sp)
                }

                DropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { showThemeMenu = false },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    // Ajustes de Gobernanza
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "⚙ Permisos y Gobernanza",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        onClick = {
                            showThemeMenu = false
                            onOpenSettings()
                        }
                    )

                    HorizontalDivider(color = BorderObsidian)

                    // Buscar actualizaciones OTA
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "🔄 Buscar Actualizaciones",
                                color = AccentAmber,
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

                    // Presets de Tema
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
 * Badge dorado Google AI Ultra para suscripciones de alta gama.
 */
@Composable
private fun GoogleAiUltraBadge(
    modifier: Modifier = Modifier
) {
    val goldColor = Color(0xFFFBBF24)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x24F59E0B))
            .border(
                width = 1.dp,
                color = goldColor.copy(alpha = 0.75f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "⭐", fontSize = 10.sp)
            Text(
                text = "Google AI Ultra",
                color = goldColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp
            )
        }
    }
}

/**
 * Badge animado de estado del agente.
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
            .padding(horizontal = 9.dp, vertical = 3.dp),
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
 * Bottom Terminal Status Bar displaying latency, geometry, PID, CWD and Active Model Badge.
 */
@Composable
private fun TerminalStatusBar(
    cols: Int,
    rows: Int,
    pid: Int,
    cwd: String,
    activeModel: String = "Gemini 3.8 Flash",
    onModelClick: () -> Unit = {}
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
                color = TextSecondary,
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            ActiveModelBadge(
                modelName = activeModel,
                onClick = onModelClick
            )
        }
    }
}

/**
 * Active Model Badge indicating the LLM engine powering Antigravity.
 */
@Composable
private fun ActiveModelBadge(
    modelName: String = "Gemini 3.8 Flash",
    dotColor: Color = NeonCyan,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "modelDotPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceElevated.copy(alpha = 0.65f))
            .border(
                width = 0.5.dp,
                color = dotColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier.size(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = 0.35f * glowAlpha))
            )
            Box(
                modifier = Modifier
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = glowAlpha))
            )
        }

        Text(
            text = modelName,
            color = TextPrimary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    }
}

/**
 * In-App OTA Update Dialog.
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
