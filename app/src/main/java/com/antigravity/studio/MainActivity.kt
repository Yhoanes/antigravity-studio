package com.antigravity.studio

import android.content.Intent
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.antigravity.studio.model.AgentSessionState
import com.antigravity.studio.model.AgentStatus
import com.antigravity.studio.model.TerminalSession
import com.antigravity.studio.theme.AntigravityTheme
import com.antigravity.studio.theme.AppThemePreset
import com.antigravity.studio.theme.CyberObsidian
import com.antigravity.studio.ui.WorkspaceScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

private val WELCOME_BANNER = (
    "\u001b[1;36m       ___          __  _                         _  __           \r\n" +
    "\u001b[1;36m      /   |  ____  / /_(_)___ __________ __   __ (_)/ /___  __    \r\n" +
    "\u001b[1;35m     / /| | / __ \\/ __/ / __ `/ ___/ __ `/ | / // // __/ / / /    \r\n" +
    "\u001b[1;35m    / ___ |/ / / / /_/ / /_/ / /  / /_/ /| |/ // // /_/ /_/ /     \r\n" +
    "\u001b[1;36m   /_/  |_/_/ /_/\\__/_/\\__, /_/   \\__,_/ |___//_/ \\__/\\__, /      \r\n" +
    "\u001b[1;36m                      /____/                         /____/       \r\n" +
    "\u001b[38;2;139;92;246m   [Antigravity Studio v1.0.0 | Xiaomi Pad 6 2.8K 144Hz WebGL]\u001b[0m\r\n" +
    "\u001b[38;2;34;197;94m   ● Local Agent Session Ready | PTY Engine Connected\u001b[0m\r\n" +
    "\u001b[90m   Type 'agy --help' or use the Productivity Bar below.\u001b[0m\r\n\r\n" +
    "\u001b[1;36magy:workspace$ \u001b[0m"
).toByteArray(Charsets.UTF_8)

/**
 * MainActivity - Primary entry point for Antigravity Studio on Android.
 *
 * Configured specifically for the Xiaomi Pad 6 (11" 2.8K 144Hz tablet display)
 * managing hardware acceleration, terminal sessions, and orientation lifecycles.
 */
class MainActivity : ComponentActivity() {

    private val activeSessions = mutableStateListOf<TerminalSession>()
    private val activeSessionState = mutableStateOf<TerminalSession?>(null)
    private val currentThemePreset = mutableStateOf(AppThemePreset.CYBER_OBSIDIAN)
    private val agentState = mutableStateOf(AgentSessionState(status = AgentStatus.READY))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Bootstrap local Antigravity environment (bin/agy, workspace, .profile)
        com.antigravity.studio.core.AgyBootstrap.setupEnvironment(this)

        // 0.1 Initialize Google OAuth PKCE manager
        com.antigravity.studio.auth.GoogleOAuthManager.init(this)
        handleOAuthIntent(intent)

        // 0.2 Observe Google OAuth state to announce authentication events in terminal
        lifecycleScope.launch {
            var previousState: com.antigravity.studio.core.auth.AuthState = com.antigravity.studio.core.auth.AuthState.Unauthenticated
            com.antigravity.studio.auth.GoogleOAuthManager.authState.collect { state ->
                if (previousState is com.antigravity.studio.core.auth.AuthState.Authenticating && state is com.antigravity.studio.core.auth.AuthState.Authenticated) {
                    val msg = (
                        "\r\n\u001b[1;32m✔ [Google OAuth 2.0 PKCE] Sesión iniciada con éxito.\u001b[0m\r\n" +
                        "\u001b[38;2;139;92;246m● Cuenta activa: ${state.email}\u001b[0m\r\n" +
                        "\u001b[1;36m● Modelos Gemini 1.5 Pro / Flash conectados directamente.\u001b[0m\r\n\r\n" +
                        "\u001b[1;36magy:workspace$ \u001b[0m"
                    ).toByteArray(Charsets.UTF_8)
                    activeSessionState.value?.emitOutput(msg)
                } else if (previousState is com.antigravity.studio.core.auth.AuthState.Authenticating && state is com.antigravity.studio.core.auth.AuthState.Error) {
                    val msg = (
                        "\r\n\u001b[1;31m✖ [Google OAuth Error] Fallo al completar la autenticación: ${state.message}\u001b[0m\r\n\r\n" +
                        "\u001b[1;36magy:workspace$ \u001b[0m"
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
                        },
                        onNewSession = {
                            val newSessionNum = activeSessions.size + 1
                            val newSession = createSession(title = "Terminal $newSessionNum")
                            activeSessions.add(newSession)
                            activeSessionState.value = newSession
                        },
                        onCloseSession = { sessionToClose ->
                            if (activeSessions.size > 1) {
                                sessionToClose.close()
                                activeSessions.remove(sessionToClose)
                                if (activeSessionState.value?.id == sessionToClose.id) {
                                    activeSessionState.value = activeSessions.last()
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
                                currentSession.writeCommand("echo 'Google Account Signed Out.'\r")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun createSession(title: String): TerminalSession {
        var ptySession: com.antigravity.studio.pty.TerminalSession? = null
        val filesDirPath = applicationContext.filesDir.absolutePath
        val workspacePath = java.io.File(applicationContext.filesDir, "workspace").absolutePath

        try {
            val session = com.antigravity.studio.pty.TerminalSession(
                executable = "/system/bin/sh",
                args = arrayOf("-i"),
                cwd = workspacePath,
                filesDir = filesDirPath,
                envp = com.antigravity.studio.pty.TerminalSession.defaultEnvironment(filesDirPath),
                initialRows = 24,
                initialCols = 80
            )
            session.start()
            if (session.isRunning) {
                ptySession = session
                Log.i(TAG, "Native PTY session started successfully for $title (PID: ${session.pid})")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Native PTY unavailable for $title, using fallback engine: ${t.message}")
        }

        val finalPty = ptySession
        val uiSession = TerminalSession(
            title = title,
            initialCwd = workspacePath,
            onWriteNative = { bytes ->
                if (finalPty != null && finalPty.isRunning) {
                    finalPty.tryWrite(bytes)
                }
            },
            onResizeNative = { cols, rows ->
                finalPty?.resize(rows, cols)
            },
            onCloseNative = {
                finalPty?.close()
            }
        )

        // Stream native PTY bytes into UI session
        if (finalPty != null) {
            lifecycleScope.launch {
                finalPty.output.collect { chunk ->
                    uiSession.emitOutput(chunk)
                }
            }
        }

        // Emit futuristic welcome banner on terminal startup
        lifecycleScope.launch {
            if (finalPty != null && finalPty.isRunning) {
                finalPty.tryWrite(". \"$filesDirPath/.mkshrc\" 2>/dev/null; clear\r".toByteArray(Charsets.UTF_8))
            }
            delay(100)
            uiSession.emitOutput(WELCOME_BANNER)
            if (finalPty != null && finalPty.isRunning) {
                finalPty.tryWrite("\r".toByteArray(Charsets.UTF_8))
            }
        }

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

    override fun onDestroy() {
        super.onDestroy()
        for (session in activeSessions) {
            session.close()
        }
        activeSessions.clear()
    }
}
