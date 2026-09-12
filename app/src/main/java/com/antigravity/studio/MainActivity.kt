package com.antigravity.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.antigravity.studio.agent.AgentStreamEvent
import com.antigravity.studio.agent.RealAgentEngine
import com.antigravity.studio.model.AgentSessionState
import com.antigravity.studio.model.AgentStatus
import com.antigravity.studio.model.AntigravityModelCatalog
import com.antigravity.studio.model.TerminalSession
import com.antigravity.studio.theme.AntigravityTheme
import com.antigravity.studio.theme.AppThemePreset
import com.antigravity.studio.theme.CyberObsidian
import com.antigravity.studio.ui.WorkspaceScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MainActivity"

private const val PROMPT = "\u001b[1;36m> \u001b[0m"

private fun buildWelcomeBanner(activeUserEmail: String?): ByteArray {
    val emailDisplay = activeUserEmail ?: "Sin sesión activa"
    val modelDisplay = AntigravityModelCatalog.selectedModel.value.displayName
    return (
        "\r\n" +
        "\u001b[1;36m    ___         __  _                         _  __\u001b[0m\r\n" +
        "\u001b[1;36m   /   |  ____  / /_(_)___ __________ __   __ (_)/ /___  __\u001b[0m\r\n" +
        "\u001b[1;35m  / /| | / __ \\/ __/ / __ `/ ___/ __ `/ | / // // __/ / / /\u001b[0m\r\n" +
        "\u001b[1;35m / ___ |/ / / / /_/ / /_/ / /  / /_/ /| |/ // // /_/ /_/ /\u001b[0m\r\n" +
        "\u001b[1;36m/_/  |_/_/ /_/\\__/_/\\__, /_/   \\__,_/ |___//_/ \\__/\\__, /\u001b[0m\r\n" +
        "\u001b[1;36m                   /____/                         /____/\u001b[0m\r\n\r\n" +
        "\u001b[38;2;139;92;246mAntigravity Studio\u001b[0m \u001b[38;2;139;148;158m• Autonomous Agent Development Station\u001b[0m\r\n" +
        "\u001b[38;2;34;197;94m● Google Cloud:\u001b[0m \u001b[38;2;248;250;252m$emailDisplay\u001b[0m\r\n" +
        "\u001b[38;2;34;197;94m● Agent Engine:\u001b[0m \u001b[38;2;248;250;252m$modelDisplay\u001b[0m\r\n\r\n" +
        PROMPT
    ).toByteArray(Charsets.UTF_8)
}

private val HELP_MENU = (
    "\r\n\u001b[1mAntigravity Studio (Xiaomi Pad 6 Edition)\u001b[0m\r\n" +
    "Escribe directamente cualquier instrucción o pregunta en lenguaje natural.\r\n" +
    "Comandos del sistema:\r\n" +
    "  ! <comando>   Ejecuta comandos de shell en el espacio de trabajo (ej: !ls, !pwd)\r\n" +
    "  /model        Muestra y permite cambiar el modelo de IA activo\r\n" +
    "  clear         Limpia la pantalla de la terminal\r\n\r\n" +
    PROMPT
).toByteArray(Charsets.UTF_8)

internal fun formatModelListMessage(): String {
    val currentSelected = AntigravityModelCatalog.selectedModel.value
    val sb = StringBuilder()
    sb.append("\r\n\u001b[1;36mModelos disponibles en Antigravity Studio:\u001b[0m\r\n\r\n")
    for (model in AntigravityModelCatalog.models) {
        val isActive = model.id == currentSelected.id
        val tagsStr = if (model.tags.isNotEmpty()) " \u001b[38;2;139;92;246m[${model.tags.joinToString(", ")}]\u001b[0m" else ""
        if (isActive) {
            sb.append("  \u001b[1;32m● ${model.displayName}\u001b[0m \u001b[38;2;139;148;158m(${model.id})\u001b[0m$tagsStr \u001b[1;32m* ACTIVO\u001b[0m\r\n")
        } else {
            sb.append("    \u001b[38;2;248;250;252m${model.displayName}\u001b[0m \u001b[38;2;139;148;158m(${model.id})\u001b[0m$tagsStr\r\n")
        }
    }
    sb.append("\r\n\u001b[38;2;139;148;158mUsa /model <nombre> para cambiar o toca el badge inferior.\u001b[0m\r\n\r\n$PROMPT")
    return sb.toString()
}

internal fun handleModelSelectionCommand(query: String): String {
    val changed = AntigravityModelCatalog.selectModelById(query)
    return if (changed) {
        "\r\n\u001b[1;32m✓ Modelo cambiado a: ${AntigravityModelCatalog.selectedModel.value.displayName}\u001b[0m\r\n\r\n$PROMPT"
    } else {
        "\r\n\u001b[1;31m✖ Modelo no encontrado: '$query'. Escribe /model para ver la lista.\u001b[0m\r\n\r\n$PROMPT"
    }
}

/**
 * MainActivity - Primary entry point for Antigravity Studio on Android.
 *
 * Configured specifically for the Xiaomi Pad 6 (11" 2.8K 144Hz tablet display)
 * managing hardware acceleration, terminal sessions, and orientation lifecycles.
 */
class MainActivity : ComponentActivity() {

    var activeNativePty: com.antigravity.studio.pty.TerminalSession? = null

    private val activeSessions = mutableStateListOf<TerminalSession>()
    private val activeSessionState = mutableStateOf<TerminalSession?>(null)
    private val nativePtyMap = mutableMapOf<String, com.antigravity.studio.pty.TerminalSession>()
    private val currentThemePreset = mutableStateOf(AppThemePreset.CYBER_OBSIDIAN)
    private val agentState = mutableStateOf(AgentSessionState(status = AgentStatus.READY))

    private val agentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.antigravity.studio.RUN_AGENT") {
                val prompt = intent.getStringExtra("prompt")
                if (prompt.isNullOrBlank()) return

                val pty = activeNativePty
                if (pty != null && pty.isRunning) {
                    lifecycleScope.launch {
                        RealAgentEngine.attachToPtyStream(pty.fd, prompt)
                    }
                } else {
                    activeSessionState.value?.let { uiSession ->
                        lifecycleScope.launch {
                            uiSession.emitOutput(
                                "\r\n\u001b[38;2;139;148;158m• Thought for 1s, planning...\u001b[0m\r\n\u001b[1;36m∷ Generating...\u001b[0m\r\n\r\n"
                                    .toByteArray(Charsets.UTF_8)
                            )
                            var lastDeltaEndsWithPrompt = false
                            try {
                                RealAgentEngine.executeAgentTask(prompt).collect { event ->
                                    when (event) {
                                        is AgentStreamEvent.TextDelta -> {
                                            lastDeltaEndsWithPrompt = event.text.trimEnd().endsWith(">")
                                            val formatted = event.text.replace("\r\n", "\n").replace("\n", "\r\n")
                                            uiSession.emitOutput(formatted.toByteArray(Charsets.UTF_8))
                                        }
                                        is AgentStreamEvent.Completed -> {
                                            if (!lastDeltaEndsWithPrompt) {
                                                uiSession.emitOutput("\r\n\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                                            }
                                        }
                                        is AgentStreamEvent.Error -> {
                                            if (!lastDeltaEndsWithPrompt) {
                                                uiSession.emitOutput("\r\n\u001b[1;31m✖ [Error]: ${event.error.message}\u001b[0m\r\n\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                                            }
                                        }
                                        is AgentStreamEvent.ToolCallStarted -> {
                                            uiSession.emitOutput("\r\n\u001b[38;2;245;158;11m⚙ [Antigravity Agent] Ejecutando herramienta: ${event.toolName}...\u001b[0m\r\n".toByteArray(Charsets.UTF_8))
                                        }
                                        is AgentStreamEvent.ToolCallFinished -> {
                                            uiSession.emitOutput("\u001b[38;2;34;197;94m✔ [Antigravity Agent] ${event.toolName} finalizada: ${event.resultSummary}\u001b[0m\r\n".toByteArray(Charsets.UTF_8))
                                        }
                                        is AgentStreamEvent.ApprovalRequired -> {
                                            uiSession.emitOutput(
                                                ("\r\n\u001b[1;33m[!] Aprobación Requerida [✓ Aprobar (Ctrl+K)]:\u001b[0m\r\n" +
                                                 "\u001b[38;2;245;158;11m• Acción: ${event.toolName}\u001b[0m\r\n" +
                                                 "\u001b[38;2;245;158;11m• Detalle: ${event.description}\u001b[0m\r\n" +
                                                 "\u001b[38;2;139;148;158mPulsa [✓ Aprobar (Ctrl+K)] en la barra inferior para continuar.\u001b[0m\r\n\r\n")
                                                    .toByteArray(Charsets.UTF_8)
                                            )
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                uiSession.emitOutput("\r\n\u001b[1;31m✖ [Error]: ${t.message}\u001b[0m\r\n\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Bootstrap local Antigravity environment (bin/agy, workspace, .profile)
        com.antigravity.studio.core.AgyBootstrap.setupEnvironment(this)

        // 0.1 Initialize Google OAuth PKCE manager
        com.antigravity.studio.auth.GoogleOAuthManager.init(this)
        handleOAuthIntent(intent)

        // 0.15 Initialize ProjectManager & AgentSettingsManager
        val projectsRoot = File(filesDir, "projects")
        val projectManager = com.antigravity.studio.project.ProjectManager.init(
            projectsDir = projectsRoot,
            onProjectChanged = { selectedProject: com.antigravity.studio.project.ProjectItem ->
                activeNativePty?.tryWrite("cd \"${selectedProject.absolutePath}\" && clear\n".toByteArray(Charsets.UTF_8))
            }
        )
        projectManager.setPtySessionProvider { activeNativePty }
        com.antigravity.studio.settings.AgentSettingsManager.init(this)

        // Hook stop execution callback from StudioBackgroundService
        com.antigravity.studio.service.StudioBackgroundService.onStopExecutionRequested = {
            activeNativePty?.tryWrite(byteArrayOf(0x03))
        }

        // 0.2 Register dynamic BroadcastReceiver for native agent invocation
        val filter = IntentFilter("com.antigravity.studio.RUN_AGENT")
        ContextCompat.registerReceiver(
            this,
            agentReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // 0.3 Observe Google OAuth state to announce authentication events in terminal
        lifecycleScope.launch {
            var previousState: com.antigravity.studio.core.auth.AuthState = com.antigravity.studio.core.auth.AuthState.Unauthenticated
            com.antigravity.studio.auth.GoogleOAuthManager.authState.collect { state ->
                if (previousState is com.antigravity.studio.core.auth.AuthState.Authenticating && state is com.antigravity.studio.core.auth.AuthState.Authenticated) {
                    val msg = (
                        "\r\n\u001b[1;32m✔ [Google OAuth 2.0 PKCE] Sesión iniciada con éxito.\u001b[0m\r\n" +
                        "\u001b[38;2;139;92;246m● Cuenta activa: ${state.email}\u001b[0m\r\n" +
                        "\u001b[1;36m● Modelo ${AntigravityModelCatalog.selectedModel.value.displayName} conectado directamente.\u001b[0m\r\n\r\n" +
                        PROMPT
                    ).toByteArray(Charsets.UTF_8)
                    activeSessionState.value?.emitOutput(msg)
                } else if (previousState is com.antigravity.studio.core.auth.AuthState.Authenticating && state is com.antigravity.studio.core.auth.AuthState.Error) {
                    val msg = (
                        "\r\n\u001b[1;31m✖ [Google OAuth Error] Fallo al completar la autenticación: ${state.message}\u001b[0m\r\n\r\n" +
                        PROMPT
                    ).toByteArray(Charsets.UTF_8)
                    activeSessionState.value?.emitOutput(msg)
                }
                previousState = state
            }
        }

        // 1. Hardware acceleration & edge-to-edge window insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // 2. Initialize default primary terminal session
        val primarySession = createSession(title = "Terminal 1")
        activeSessions.add(primarySession)
        activeSessionState.value = primarySession

        // 3. Mount complete Antigravity Studio UI
        setContent {
            AntigravityTheme(preset = currentThemePreset.value) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberObsidian
                ) {
                    val currentSession = activeSessionState.value ?: primarySession

                    val activeUserEmail by com.antigravity.studio.auth.GoogleOAuthManager.activeAccountEmail.collectAsState()

                    WorkspaceScaffold(
                        activeSession = currentSession,
                        sessions = activeSessions,
                        onSelectSession = { session ->
                            activeSessionState.value = session
                            activeNativePty = nativePtyMap[session.id]
                        },
                        onNewSession = {
                            val newSessionNum = activeSessions.size + 1
                            val newSession = createSession(title = "Terminal $newSessionNum")
                            activeSessions.add(newSession)
                            activeSessionState.value = newSession
                            activeNativePty = nativePtyMap[newSession.id]
                        },
                        onCloseSession = { sessionToClose ->
                            if (activeSessions.size > 1) {
                                sessionToClose.close()
                                activeSessions.remove(sessionToClose)
                                nativePtyMap.remove(sessionToClose.id)
                                if (activeSessionState.value?.id == sessionToClose.id) {
                                    val nextSession = activeSessions.last()
                                    activeSessionState.value = nextSession
                                    activeNativePty = nativePtyMap[nextSession.id]
                                }
                            }
                        },
                        onThemePresetSelected = { preset ->
                            currentThemePreset.value = preset
                        },
                        agentState = agentState.value,
                        activeUserEmail = activeUserEmail,
                        onGoogleSignInClick = {
                            com.antigravity.studio.auth.GoogleOAuthManager.startLogin(this@MainActivity)
                        },
                        onSignOutClick = {
                            lifecycleScope.launch {
                                com.antigravity.studio.auth.GoogleOAuthManager.signOut()
                                currentSession.emitOutput("\r\n\u001b[33m● Sesión de Google cerrada.\u001b[0m\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                            }
                        }
                    )
                }
            }
        }
    }

    private fun createSession(title: String): TerminalSession {
        val workspacePath = File(applicationContext.filesDir, "workspace").absolutePath
        val filesDirPath = applicationContext.filesDir.absolutePath
        val inputLineBuffer = StringBuilder()
        var lastWasCr = false
        var currentJob: Job? = null
        var inEscapeSequence = false

        lateinit var uiSession: TerminalSession

        fun handleEnter() {
            uiSession.emitOutput("\r\n".toByteArray(Charsets.UTF_8))
            val prompt = inputLineBuffer.toString().trim()
            inputLineBuffer.clear()

            if (prompt.isEmpty()) {
                uiSession.emitOutput(PROMPT.toByteArray(Charsets.UTF_8))
                return
            }

            if (prompt == "clear") {
                uiSession.emitOutput("\u001b[2J\u001b[H$PROMPT".toByteArray(Charsets.UTF_8))
                return
            }

            if (prompt == "help" || prompt == "--help" || prompt == "agy" || prompt == "agy --help" || prompt == "agy help") {
                uiSession.emitOutput(HELP_MENU)
                return
            }

            if (prompt == "/model" || prompt == "model") {
                uiSession.emitOutput(formatModelListMessage().toByteArray(Charsets.UTF_8))
                return
            }

            if (prompt.startsWith("/model ")) {
                val query = prompt.removePrefix("/model ").trim()
                uiSession.emitOutput(handleModelSelectionCommand(query).toByteArray(Charsets.UTF_8))
                return
            }

            if (prompt.startsWith("!")) {
                val cmd = prompt.removePrefix("!").trim()
                currentJob?.cancel()
                currentJob = lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val workspaceDir = File(workspacePath).apply { if (!exists()) mkdirs() }
                        val pb = ProcessBuilder("sh", "-c", cmd)
                            .directory(workspaceDir)
                            .redirectErrorStream(true)

                        val env = pb.environment()
                        env["HOME"] = filesDirPath
                        env["WORKSPACE"] = workspacePath
                        env["PATH"] = "$filesDirPath/bin:" + (System.getenv("PATH") ?: "/system/bin")
                        env["TERM"] = "xterm-256color"

                        val process = pb.start()
                        val reader = process.inputStream.bufferedReader()
                        val buffer = CharArray(1024)
                        var read: Int
                        while (reader.read(buffer).also { read = it } != -1) {
                            val outText = String(buffer, 0, read).replace("\r\n", "\n").replace("\n", "\r\n")
                            uiSession.emitOutput(outText.toByteArray(Charsets.UTF_8))
                        }
                        process.waitFor()
                    } catch (t: Throwable) {
                        uiSession.emitOutput("\r\n\u001b[1;31m✖ [Error shell]: ${t.message}\u001b[0m\r\n".toByteArray(Charsets.UTF_8))
                    } finally {
                        uiSession.emitOutput("\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                    }
                }
                return
            }

            // Natural language -> RealAgentEngine
            currentJob?.cancel()
            currentJob = lifecycleScope.launch {
                uiSession.emitOutput(
                    "\r\n\u001b[38;2;139;148;158m• Thought for 1s, planning...\u001b[0m\r\n\u001b[1;36m∷ Generating...\u001b[0m\r\n\r\n"
                        .toByteArray(Charsets.UTF_8)
                )
                var lastDeltaEndsWithPrompt = false
                try {
                    RealAgentEngine.executeAgentTask(prompt).collect { event ->
                        when (event) {
                            is AgentStreamEvent.TextDelta -> {
                                lastDeltaEndsWithPrompt = event.text.trimEnd().endsWith(">")
                                val formatted = event.text.replace("\r\n", "\n").replace("\n", "\r\n")
                                uiSession.emitOutput(formatted.toByteArray(Charsets.UTF_8))
                            }
                            is AgentStreamEvent.Completed -> {
                                if (!lastDeltaEndsWithPrompt) {
                                    uiSession.emitOutput("\r\n\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                                }
                            }
                            is AgentStreamEvent.Error -> {
                                if (!lastDeltaEndsWithPrompt) {
                                    uiSession.emitOutput("\r\n\u001b[1;31m✖ [Error]: ${event.error.message}\u001b[0m\r\n\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                                }
                            }
                            is AgentStreamEvent.ToolCallStarted -> {
                                uiSession.emitOutput("\r\n\u001b[38;2;245;158;11m⚙ [Antigravity Agent] Ejecutando herramienta: ${event.toolName}...\u001b[0m\r\n".toByteArray(Charsets.UTF_8))
                            }
                            is AgentStreamEvent.ToolCallFinished -> {
                                uiSession.emitOutput("\u001b[38;2;34;197;94m✔ [Antigravity Agent] ${event.toolName} finalizada: ${event.resultSummary}\u001b[0m\r\n".toByteArray(Charsets.UTF_8))
                            }
                            is AgentStreamEvent.ApprovalRequired -> {
                                uiSession.emitOutput(
                                    ("\r\n\u001b[1;33m[!] Aprobación Requerida [✓ Aprobar (Ctrl+K)]:\u001b[0m\r\n" +
                                     "\u001b[38;2;245;158;11m• Acción: ${event.toolName}\u001b[0m\r\n" +
                                     "\u001b[38;2;245;158;11m• Detalle: ${event.description}\u001b[0m\r\n" +
                                     "\u001b[38;2;139;148;158mPulsa [✓ Aprobar (Ctrl+K)] en la barra inferior para continuar.\u001b[0m\r\n\r\n")
                                        .toByteArray(Charsets.UTF_8)
                                )
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Prompt was cancelled by user
                } catch (t: Throwable) {
                    uiSession.emitOutput("\r\n\u001b[1;31m✖ [Error]: ${t.message}\u001b[0m\r\n\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                }
            }
        }

        uiSession = TerminalSession(
            title = title,
            initialCwd = workspacePath,
            onWriteNative = { bytes ->
                synchronized(inputLineBuffer) {
                    val inputString = String(bytes, Charsets.UTF_8)
                    var i = 0
                    while (i < inputString.length) {
                        val ch = inputString[i]

                        if (inEscapeSequence) {
                            if ((ch in 'A'..'Z') || (ch in 'a'..'z') || ch == '~') {
                                inEscapeSequence = false
                            }
                            i++
                            continue
                        }

                        if (ch == '\u001b') {
                            if (i + 1 < inputString.length) {
                                inEscapeSequence = true
                            }
                            i++
                            continue
                        }

                        when {
                            ch == '\r' -> {
                                lastWasCr = true
                                handleEnter()
                            }
                            ch == '\n' -> {
                                if (lastWasCr) {
                                    lastWasCr = false
                                } else {
                                    handleEnter()
                                }
                            }
                            ch == '\u007F' || ch == '\b' || ch.code == 0x7F || ch.code == 0x08 -> {
                                lastWasCr = false
                                if (inputLineBuffer.isNotEmpty()) {
                                    inputLineBuffer.deleteCharAt(inputLineBuffer.length - 1)
                                    uiSession.emitOutput("\b \b".toByteArray(Charsets.UTF_8))
                                }
                            }
                            ch == '\u0003' || ch.code == 0x03 -> {
                                lastWasCr = false
                                currentJob?.cancel()
                                currentJob = null
                                inputLineBuffer.clear()
                                uiSession.emitOutput("^C\r\n$PROMPT".toByteArray(Charsets.UTF_8))
                            }
                            ch >= ' ' || ch == '\t' -> {
                                lastWasCr = false
                                inputLineBuffer.append(ch)
                                uiSession.emitOutput(ch.toString().toByteArray(Charsets.UTF_8))
                            }
                            else -> {
                                lastWasCr = false
                            }
                        }
                        i++
                    }
                }
            },
            onResizeNative = { _, _ -> },
            onCloseNative = {
                currentJob?.cancel()
                currentJob = null
            }
        )

        val activeUserEmail = com.antigravity.studio.auth.GoogleOAuthManager.activeAccountEmail.value
            ?: com.antigravity.studio.auth.GoogleOAuthManager.getActiveAccount()
        uiSession.emitOutput(buildWelcomeBanner(activeUserEmail))

        return uiSession
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle tablet orientation changes without destroying active terminal sessions
        activeSessionState.value?.let { session ->
            session.resize(session.cols.value, session.rows.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "antigravity") {
            when (data.host) {
                "workspace", "auth-complete" -> {
                    Log.i(TAG, "Retorno al espacio de trabajo recibido desde OAuth: $data")
                }
                "login" -> {
                    com.antigravity.studio.auth.GoogleOAuthManager.startLogin(this)
                }
                "oauth2callback" -> {
                    lifecycleScope.launch {
                        val result = com.antigravity.studio.auth.GoogleOAuthManager.handleAuthCallback(data)
                        result.onSuccess { email ->
                            Log.i(TAG, "Google OAuth fallback login successful: $email")
                        }
                        result.onFailure { error ->
                            Log.e(TAG, "Google OAuth callback error", error)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.antigravity.studio.service.StudioBackgroundService.updateTelemetry(this)
    }

    override fun onStop() {
        super.onStop()
        val activeProj = com.antigravity.studio.project.ProjectManager.getInstance().activeProject.value
        com.antigravity.studio.service.StudioBackgroundService.startService(
            this,
            activeProj?.name ?: "tateti"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(agentReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister agent receiver: ${e.message}")
        }
        for (session in activeSessions) {
            session.close()
        }
        activeSessions.clear()
        nativePtyMap.clear()
        activeNativePty = null
    }
}
