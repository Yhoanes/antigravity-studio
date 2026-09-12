package com.antigravity.studio.pty

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encapsulates an active POSIX pseudo-terminal (PTY) session, providing reactive
 * Coroutines-based I/O streams ([output] SharedFlow and [write] Channel)
 * and managing the full lifecycle of the spawned Linux subprocess.
 */
class TerminalSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val executable: String = "/system/bin/sh",
    val args: Array<String> = emptyArray(),
    val envp: Array<String> = defaultEnvironment(),
    val cwd: String = "",
    val initialRows: Int = 24,
    val initialCols: Int = 80,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : Closeable {

    enum class State {
        INITIALIZING,
        RUNNING,
        STOPPING,
        TERMINATED,
        FAILED
    }

    private val _state = MutableStateFlow(State.INITIALIZING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    // Reactive byte stream for terminal output (PTY master read -> xterm.js / UI)
    private val _output = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()
    val outputStream: SharedFlow<ByteArray> get() = output

    // Input buffer channel (UI / keyboard -> PTY master write)
    private val inputChannel = Channel<ByteArray>(capacity = 128)

    private var masterFd: Int = -1
    private var childPid: Int = -1

    var currentRows: Int = initialRows
        private set
    var currentCols: Int = initialCols
        private set

    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private val isClosed = AtomicBoolean(false)

    val isRunning: Boolean
        get() = _state.value == State.RUNNING

    val pid: Int
        get() = childPid

    val fd: Int
        get() = masterFd

    /**
     * Initializes the PTY descriptor pair, forks the subprocess, and launches
     * asynchronous reader and writer coroutines.
     */
    fun start(): TerminalSession {
        if (_state.value != State.INITIALIZING) {
            return this
        }

        val outFd = IntArray(1)
        val pid = NativePty.nativeCreateSubprocess(
            executable = executable,
            args = args,
            envp = envp,
            cwd = cwd,
            rows = initialRows,
            cols = initialCols,
            outMasterFd = outFd
        )

        if (pid < 0 || outFd[0] < 0) {
            _state.value = State.FAILED
            _exitCode.value = pid
            return this
        }

        this.childPid = pid
        this.masterFd = outFd[0]
        _state.value = State.RUNNING

        launchReaderLoop()
        launchWriterLoop()

        return this
    }

    /**
     * Reader loop: reads bytes from non-blocking master PTY descriptor and emits to [_output].
     */
    private fun launchReaderLoop() {
        readerJob = scope.launch(ioDispatcher) {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (isActive && _state.value == State.RUNNING) {
                    val bytesRead = NativePty.nativeRead(masterFd, buffer, 0, buffer.size)

                    when {
                        bytesRead > 0 -> {
                            val chunk = buffer.copyOf(bytesRead)
                            _output.emit(chunk)
                        }
                        bytesRead == 0 -> {
                            // Non-blocking read: no data available yet. Yield briefly to prevent CPU spinning.
                            delay(POLL_INTERVAL_MS)
                        }
                        else -> {
                            // EOF or PTY hang-up: child process closed slave or terminated
                            break
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Normal coroutine cancellation
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in TerminalSession reader loop", e)
            } finally {
                if (!isClosed.get()) {
                    terminateSubprocess()
                }
            }
        }
    }

    /**
     * Writer loop: receives data from [inputChannel] and transmits to master PTY descriptor.
     */
    private fun launchWriterLoop() {
        writerJob = scope.launch(ioDispatcher) {
            try {
                for (chunk in inputChannel) {
                    if (!isActive || _state.value != State.RUNNING) break
                    var offset = 0
                    while (offset < chunk.size && isActive && _state.value == State.RUNNING) {
                        val written = NativePty.nativeWrite(
                            masterFd,
                            chunk,
                            offset,
                            chunk.size - offset
                        )
                        if (written > 0) {
                            offset += written
                        } else if (written == 0) {
                            delay(POLL_INTERVAL_MS)
                        } else {
                            // Error on write
                            break
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Normal coroutine cancellation
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in TerminalSession writer loop", e)
            }
        }
    }

    /**
     * Sends input bytes to the PTY asynchronously via the channel.
     */
    suspend fun write(data: ByteArray) {
        if (_state.value == State.RUNNING) {
            inputChannel.send(data)
        }
    }

    /**
     * Helper to write UTF-8 string data directly.
     */
    suspend fun write(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Non-suspending write attempt. Returns true if queued, false otherwise.
     */
    fun tryWrite(data: ByteArray): Boolean {
        return if (_state.value == State.RUNNING) {
            inputChannel.trySend(data).isSuccess
        } else {
            false
        }
    }

    /**
     * Helper to send SIGINT (^C / 0x03) character.
     */
    suspend fun sendSigInt() {
        write(byteArrayOf(0x03))
    }

    /**
     * Helper to send EOF (^D / 0x04) character.
     */
    suspend fun sendEof() {
        write(byteArrayOf(0x04))
    }

    /**
     * Resizes the terminal window dimensions and notifies the child process via SIGWINCH.
     */
    fun resize(rows: Int, cols: Int): Boolean {
        if (masterFd < 0 || rows <= 0 || cols <= 0) return false
        val success = NativePty.nativeResize(masterFd, rows, cols)
        if (success) {
            currentRows = rows
            currentCols = cols
        }
        return success
    }

    /**
     * Terminates the session, closing file descriptors, cancelling coroutines,
     * and reaping child process status.
     */
    private fun terminateSubprocess(): Int {
        if (!isClosed.compareAndSet(false, true)) {
            return _exitCode.value ?: 0
        }

        _state.value = State.STOPPING

        inputChannel.close()
        writerJob?.cancel()
        readerJob?.cancel()

        val code = NativePty.nativeClose(masterFd, childPid)
        _exitCode.value = code
        _state.value = State.TERMINATED
        masterFd = -1

        return code
    }

    override fun close() {
        terminateSubprocess()
    }

    /**
     * Suspends until the terminal subprocess terminates.
     */
    suspend fun waitForExit(): Int {
        while (_state.value != State.TERMINATED && _state.value != State.FAILED) {
            delay(50)
        }
        return _exitCode.value ?: 0
    }

    companion object {
        private const val TAG = "TerminalSession"
        private const val READ_BUFFER_SIZE = 4096
        private const val POLL_INTERVAL_MS = 8L // ~120-144 Hz refresh interval alignment

        fun defaultEnvironment(): Array<String> = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "LANG=en_US.UTF-8",
            "LC_ALL=en_US.UTF-8"
        )
    }
}
