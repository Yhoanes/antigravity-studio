package com.antigravity.studio.pty

/**
 * Low-level JNI bridge to the POSIX pseudo-terminal (PTY) subsystem in `libantigravity-pty.so`.
 *
 * Provides native bindings for terminal process allocation, non-blocking I/O,
 * window resize dispatching (TIOCSWINSZ / SIGWINCH), and process cleanup.
 */
object NativePty {

    init {
        System.loadLibrary("antigravity-pty")
    }

    /**
     * Allocates a POSIX pseudo-terminal pair (/dev/ptmx), executes fork() and setsid(),
     * attaches the slave PTY as the controlling terminal (TIOCSCTTY), applies raw termios
     * with UTF-8 flags, sets the initial window size, and invokes execve() with the target binary.
     *
     * @param executable Absolute path to the executable (e.g. "/system/bin/sh" or proot binary).
     * @param args Command-line arguments to pass to the executable.
     * @param envp Environment variables in KEY=VALUE format.
     * @param cwd Initial working directory for the child process.
     * @param rows Initial terminal rows.
     * @param cols Initial terminal columns.
     * @param outMasterFd Single-element IntArray where the master PTY file descriptor is returned.
     * @return Child process PID (> 0) on success, or a negative error code (-errno) on failure.
     */
    external fun nativeCreateSubprocess(
        executable: String,
        args: Array<String>,
        envp: Array<String>,
        cwd: String,
        rows: Int,
        cols: Int,
        outMasterFd: IntArray
    ): Int

    /**
     * Writes raw bytes synchronously to the master PTY descriptor.
     *
     * @param masterFd Master PTY file descriptor returned by [nativeCreateSubprocess].
     * @param buffer Byte array containing input data to send to the child process.
     * @param offset Start index in the buffer.
     * @param length Number of bytes to transmit.
     * @return Number of bytes actually written, or < 0 on POSIX write failure.
     */
    external fun nativeWrite(
        masterFd: Int,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int

    /**
     * Reads available output bytes from the master PTY descriptor in non-blocking mode.
     *
     * @param masterFd Master PTY file descriptor.
     * @param buffer Destination byte array to receive output from the child process.
     * @param offset Start index in the destination buffer.
     * @param length Maximum bytes to read.
     * @return Number of bytes read (0 if no data is currently available in non-blocking mode),
     *         or -1 if end-of-file (EOF / hang-up) occurred or an unrecoverable error happened.
     */
    external fun nativeRead(
        masterFd: Int,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int

    /**
     * Updates the terminal window size (TIOCSWINSZ) and triggers SIGWINCH in the child process.
     *
     * @param masterFd Master PTY file descriptor.
     * @param rows New number of character rows.
     * @param cols New number of character columns.
     * @return True if the ioctl succeeded, false otherwise.
     */
    external fun nativeResize(
        masterFd: Int,
        rows: Int,
        cols: Int
    ): Boolean

    /**
     * Closes the master PTY descriptor and terminates the child process if active,
     * escalating signals (SIGHUP -> SIGTERM -> SIGKILL) and reaping the zombie with waitpid.
     *
     * @param masterFd Master PTY file descriptor to close.
     * @param childPid Child process PID to signal and collect.
     * @return Child exit code (>= 0) or negative signal code if terminated by a signal.
     */
    external fun nativeClose(
        masterFd: Int,
        childPid: Int
    ): Int
}
