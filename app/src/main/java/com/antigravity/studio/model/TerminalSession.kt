package com.antigravity.studio.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * TerminalSession manages an active PTY session's reactive streams and state.
 *
 * Implements bidirectional communication between Jetpack Compose / WebView and
 * the native Linux PTY engine (or local shell process).
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Terminal 1",
    val initialCwd: String = "/workspace/antigravity",
    val onWriteNative: ((ByteArray) -> Unit)? = null,
    val onResizeNative: ((cols: Int, rows: Int) -> Unit)? = null,
    val onCloseNative: (() -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _outputFlow = MutableSharedFlow<ByteArray>(replay = 50, extraBufferCapacity = 512)
    val outputFlow: SharedFlow<ByteArray> = _outputFlow.asSharedFlow()

    private val _cols = MutableStateFlow(80)
    val cols: StateFlow<Int> = _cols.asStateFlow()

    private val _rows = MutableStateFlow(24)
    val rows: StateFlow<Int> = _rows.asStateFlow()

    private val _cwd = MutableStateFlow(initialCwd)
    val cwd: StateFlow<String> = _cwd.asStateFlow()

    private val _isAlive = MutableStateFlow(true)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private val _pid = MutableStateFlow(1001)
    val pid: StateFlow<Int> = _pid.asStateFlow()

    /**
     * Emits binary output from PTY into the reactive flow to be consumed by xterm.js / WebView.
     */
    fun emitOutput(data: ByteArray) {
        _outputFlow.tryEmit(data)
    }

    /**
     * Sends user keystrokes / data from UI or virtual keyboard down to the PTY.
     */
    fun writeInput(bytes: ByteArray) {
        if (!_isAlive.value) return

        if (onWriteNative != null) {
            onWriteNative.invoke(bytes)
        } else {
            // Local fallback loopback echo for standalone testing / simulation
            handleLocalFallbackInput(bytes)
        }
    }

    /**
     * Convenience method to send string commands (e.g. from ProductivityBar).
     */
    fun writeCommand(command: String) {
        writeInput(command.toByteArray(Charsets.UTF_8))
    }

    /**
     * Updates terminal window dimensions and propagates SIGWINCH down to native PTY.
     */
    fun resize(newCols: Int, newRows: Int) {
        if (newCols > 0 && newRows > 0) {
            _cols.value = newCols
            _rows.value = newRows
            onResizeNative?.invoke(newCols, newRows)
        }
    }

    /**
     * Terminates the session and cleans up resources.
     */
    fun close() {
        _isAlive.value = false
        onCloseNative?.invoke()
    }

    private fun handleLocalFallbackInput(bytes: ByteArray) {
        scope.launch {
            val text = String(bytes, Charsets.UTF_8)
            when (text) {
                "\r", "\n" -> {
                    emitOutput("\r\n\u001b[1;36magy:workspace$ \u001b[0m".toByteArray(Charsets.UTF_8))
                }
                "\u0003" -> { // SIGINT (^C)
                    emitOutput("^C\r\n\u001b[1;36magy:workspace$ \u001b[0m".toByteArray(Charsets.UTF_8))
                }
                "agy\r", "agy run\r" -> {
                    emitOutput(
                        ("\r\n\u001b[38;2;139;92;246m[Antigravity Orchestrator]\u001b[0m Launching autonomous loop...\r\n" +
                         "\u001b[38;2;0;240;255m● Spec:\u001b[0m specs/01-app-blueprint.md\r\n" +
                         "\u001b[38;2;34;197;94m● Status:\u001b[0m All 8 acceptance criteria passing.\r\n" +
                         "\u001b[1;36magy:workspace$ \u001b[0m").toByteArray(Charsets.UTF_8)
                    )
                }
                "agy test\r" -> {
                    emitOutput(
                        ("\r\n\u001b[38;2;245;158;11m[QA Harness]\u001b[0m Running closed-loop test suites...\r\n" +
                         "\u001b[32m✔ SDD Spec Compliance Suite (3/3 PASS)\u001b[0m\r\n" +
                         "\u001b[32m✔ Static Analysis & Syntax Linter (PASS)\u001b[0m\r\n" +
                         "\u001b[32m✔ Automated Unit & Regression Tests (PASS)\u001b[0m\r\n" +
                         "\u001b[1;36magy:workspace$ \u001b[0m").toByteArray(Charsets.UTF_8)
                    )
                }
                else -> {
                    // Echo back character
                    emitOutput(bytes)
                }
            }
        }
    }
}
