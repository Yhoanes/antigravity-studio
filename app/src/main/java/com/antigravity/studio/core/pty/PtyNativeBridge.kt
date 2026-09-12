package com.antigravity.studio.core.pty

import com.antigravity.studio.pty.NativePty

/**
 * Bridge layer implementing the contract defined in SPEC-001 section 5.1.
 * Delegates directly to [NativePty].
 */
object PtyNativeBridge {

    fun nativeCreateSubprocess(
        executable: String,
        args: Array<String>,
        envp: Array<String>,
        cwd: String,
        rows: Int,
        cols: Int,
        outMasterFd: IntArray
    ): Int = NativePty.nativeCreateSubprocess(executable, args, envp, cwd, rows, cols, outMasterFd)

    fun nativeWrite(
        masterFd: Int,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int = NativePty.nativeWrite(masterFd, buffer, offset, length)

    fun nativeRead(
        masterFd: Int,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int = NativePty.nativeRead(masterFd, buffer, offset, length)

    fun nativeResize(
        masterFd: Int,
        rows: Int,
        cols: Int
    ): Boolean = NativePty.nativeResize(masterFd, rows, cols)

    fun nativeClose(
        masterFd: Int,
        childPid: Int
    ): Int = NativePty.nativeClose(masterFd, childPid)
}
