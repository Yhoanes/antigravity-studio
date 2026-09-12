#include "native-pty.h"

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#include <string>
#include <vector>

#define LOG_TAG "NativePty"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {

jint create_subprocess_internal(
    JNIEnv* env,
    jstring j_executable,
    jobjectArray j_args,
    jobjectArray j_envp,
    jstring j_cwd,
    jint rows,
    jint cols,
    jintArray j_out_master_fd
) {
    if (j_executable == nullptr || j_out_master_fd == nullptr) {
        LOGE("Invalid arguments provided to nativeCreateSubprocess");
        return -EINVAL;
    }

    // 1. Allocate POSIX pseudo-terminal master descriptor
    int master_fd = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master_fd < 0) {
        // Fallback to direct /dev/ptmx open if posix_openpt is not available
        master_fd = open("/dev/ptmx", O_RDWR | O_NOCTTY | O_CLOEXEC);
        if (master_fd < 0) {
            LOGE("Failed to open PTY master: %s (errno: %d)", strerror(errno), errno);
            return -errno;
        }
    }

    // 2. Grant permissions and unlock the slave pseudoterminal
    if (grantpt(master_fd) != 0) {
        LOGE("grantpt failed: %s (errno: %d)", strerror(errno), errno);
        close(master_fd);
        return -errno;
    }

    if (unlockpt(master_fd) != 0) {
        LOGE("unlockpt failed: %s (errno: %d)", strerror(errno), errno);
        close(master_fd);
        return -errno;
    }

    // 3. Obtain slave device node path
    char slave_name[128];
    if (ptsname_r(master_fd, slave_name, sizeof(slave_name)) != 0) {
        LOGE("ptsname_r failed: %s (errno: %d)", strerror(errno), errno);
        close(master_fd);
        return -errno;
    }

    // 4. Configure initial window dimensions
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(master_fd, TIOCSWINSZ, &ws);

    // 5. Extract executable path
    const char* exe_chars = env->GetStringUTFChars(j_executable, nullptr);
    std::string executable_path = exe_chars ? exe_chars : "";
    if (exe_chars) env->ReleaseStringUTFChars(j_executable, exe_chars);

    if (executable_path.empty()) {
        LOGE("Executable path is empty");
        close(master_fd);
        return -EINVAL;
    }

    // 6. Extract working directory
    std::string cwd_path;
    if (j_cwd != nullptr) {
        const char* cwd_chars = env->GetStringUTFChars(j_cwd, nullptr);
        if (cwd_chars) {
            cwd_path = cwd_chars;
            env->ReleaseStringUTFChars(j_cwd, cwd_chars);
        }
    }

    // 7. Parse argv arguments
    std::vector<std::string> args_vec;
    args_vec.push_back(executable_path);

    if (j_args != nullptr) {
        jsize args_count = env->GetArrayLength(j_args);
        for (jsize i = 0; i < args_count; ++i) {
            jstring arg_item = static_cast<jstring>(env->GetObjectArrayElement(j_args, i));
            if (arg_item != nullptr) {
                const char* arg_chars = env->GetStringUTFChars(arg_item, nullptr);
                if (arg_chars != nullptr) {
                    args_vec.push_back(arg_chars);
                    env->ReleaseStringUTFChars(arg_item, arg_chars);
                }
                env->DeleteLocalRef(arg_item);
            }
        }
    }

    std::vector<char*> argv_ptrs;
    argv_ptrs.reserve(args_vec.size() + 1);
    for (auto& arg : args_vec) {
        argv_ptrs.push_back(const_cast<char*>(arg.c_str()));
    }
    argv_ptrs.push_back(nullptr);

    // 8. Parse and enhance environment variables (UTF-8 and xterm-256color support)
    std::vector<std::string> env_vec;
    bool has_term = false;
    bool has_colorterm = false;
    bool has_lang = false;

    if (j_envp != nullptr) {
        jsize env_count = env->GetArrayLength(j_envp);
        for (jsize i = 0; i < env_count; ++i) {
            jstring env_item = static_cast<jstring>(env->GetObjectArrayElement(j_envp, i));
            if (env_item != nullptr) {
                const char* env_chars = env->GetStringUTFChars(env_item, nullptr);
                if (env_chars != nullptr) {
                    std::string entry(env_chars);
                    if (entry.rfind("TERM=", 0) == 0) has_term = true;
                    if (entry.rfind("COLORTERM=", 0) == 0) has_colorterm = true;
                    if (entry.rfind("LANG=", 0) == 0) has_lang = true;
                    env_vec.push_back(std::move(entry));
                    env->ReleaseStringUTFChars(env_item, env_chars);
                }
                env->DeleteLocalRef(env_item);
            }
        }
    }

    if (!has_term) env_vec.push_back("TERM=xterm-256color");
    if (!has_colorterm) env_vec.push_back("COLORTERM=truecolor");
    if (!has_lang) env_vec.push_back("LANG=en_US.UTF-8");

    std::vector<char*> envp_ptrs;
    envp_ptrs.reserve(env_vec.size() + 1);
    for (auto& ev : env_vec) {
        envp_ptrs.push_back(const_cast<char*>(ev.c_str()));
    }
    envp_ptrs.push_back(nullptr);

    // 9. Fork child process
    pid_t child_pid = fork();
    if (child_pid < 0) {
        LOGE("fork failed: %s (errno: %d)", strerror(errno), errno);
        close(master_fd);
        return -errno;
    }

    if (child_pid == 0) {
        // --- Child Process Context ---

        // Detach from parent session and establish new session
        if (setsid() < 0) {
            LOGE("Child setsid failed: %s", strerror(errno));
            _exit(1);
        }

        // Open slave side of the pseudo-terminal
        int slave_fd = open(slave_name, O_RDWR);
        if (slave_fd < 0) {
            LOGE("Child open slave %s failed: %s", slave_name, strerror(errno));
            _exit(2);
        }

        // Set slave as controlling terminal for this session
        if (ioctl(slave_fd, TIOCSCTTY, 0) < 0) {
            LOGW("Child ioctl TIOCSCTTY warning: %s", strerror(errno));
        }

        // Apply raw mode and configure UTF-8
        struct termios tios;
        if (tcgetattr(slave_fd, &tios) == 0) {
            cfmakeraw(&tios);
            tios.c_cflag |= CS8;
#ifdef IUTF8
            tios.c_iflag |= IUTF8;
#endif
            tcsetattr(slave_fd, TCSANOW, &tios);
        }

        // Apply window size
        ioctl(slave_fd, TIOCSWINSZ, &ws);

        // Redirect standard descriptors (stdin, stdout, stderr) to slave_fd
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);

        // Close slave_fd and master_fd descriptors
        if (slave_fd > STDERR_FILENO) {
            close(slave_fd);
        }
        close(master_fd);

        // Change working directory if specified
        if (!cwd_path.empty()) {
            if (chdir(cwd_path.c_str()) != 0) {
                LOGW("Child chdir to %s failed: %s", cwd_path.c_str(), strerror(errno));
            }
        }

        // Reset signal handlers to defaults
        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGTSTP, SIG_DFL);
        signal(SIGTTIN, SIG_DFL);
        signal(SIGTTOU, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGHUP, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);

        // Execute binary
        execve(executable_path.c_str(), argv_ptrs.data(), envp_ptrs.data());

        // Reaching here indicates execve failure
        LOGE("Child execve %s failed: %s (errno: %d)", executable_path.c_str(), strerror(errno), errno);
        _exit(127);
    }

    // --- Parent Process Context ---

    // Set master PTY descriptor to non-blocking mode
    int flags = fcntl(master_fd, F_GETFL, 0);
    if (flags >= 0) {
        fcntl(master_fd, F_SETFL, flags | O_NONBLOCK);
    }

    // Write master_fd into caller's outMasterFd[0]
    jint master_val = master_fd;
    env->SetIntArrayRegion(j_out_master_fd, 0, 1, &master_val);

    LOGI("Subprocess successfully started: PID=%d, MasterFD=%d, Path=%s",
         child_pid, master_fd, executable_path.c_str());

    return child_pid;
}

jint write_internal(
    JNIEnv* env,
    jint master_fd,
    jbyteArray j_buffer,
    jint offset,
    jint length
) {
    if (master_fd < 0 || j_buffer == nullptr || length <= 0 || offset < 0) {
        return -EINVAL;
    }

    jsize buf_len = env->GetArrayLength(j_buffer);
    if (offset + length > buf_len) {
        return -ERANGE;
    }

    std::vector<jbyte> native_buf(length);
    env->GetByteArrayRegion(j_buffer, offset, length, native_buf.data());

    const char* src = reinterpret_cast<const char*>(native_buf.data());
    int total_written = 0;

    while (total_written < length) {
        ssize_t written = write(master_fd, src + total_written, length - total_written);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Return amount written so far, or 0 if nothing written yet
                return total_written > 0 ? total_written : 0;
            }
            LOGE("write to master_fd %d failed: %s (errno: %d)", master_fd, strerror(errno), errno);
            return -errno;
        }
        if (written == 0) {
            break;
        }
        total_written += static_cast<int>(written);
    }

    return total_written;
}

jint read_internal(
    JNIEnv* env,
    jint master_fd,
    jbyteArray j_buffer,
    jint offset,
    jint length
) {
    if (master_fd < 0 || j_buffer == nullptr || length <= 0 || offset < 0) {
        return -EINVAL;
    }

    jsize buf_len = env->GetArrayLength(j_buffer);
    if (offset + length > buf_len) {
        return -ERANGE;
    }

    std::vector<char> temp_buf(length);
    ssize_t bytes_read = read(master_fd, temp_buf.data(), length);

    if (bytes_read < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
            // Non-blocking mode: no data ready right now
            return 0;
        }
        if (errno == EIO) {
            // On Linux PTY master, EIO is returned when the slave side is closed (EOF / Hangup)
            return -1;
        }
        LOGD("read from master_fd %d failed: %s (errno: %d)", master_fd, strerror(errno), errno);
        return -1;
    }

    if (bytes_read == 0) {
        // EOF encountered
        return -1;
    }

    env->SetByteArrayRegion(
        j_buffer,
        offset,
        static_cast<jsize>(bytes_read),
        reinterpret_cast<const jbyte*>(temp_buf.data())
    );

    return static_cast<jint>(bytes_read);
}

jboolean resize_internal(
    jint master_fd,
    jint rows,
    jint cols
) {
    if (master_fd < 0 || rows <= 0 || cols <= 0) {
        return JNI_FALSE;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = static_cast<unsigned short>(rows);
    ws.ws_col = static_cast<unsigned short>(cols);
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    int res = ioctl(master_fd, TIOCSWINSZ, &ws);
    if (res != 0) {
        LOGW("ioctl TIOCSWINSZ failed on master_fd %d: %s (errno: %d)",
             master_fd, strerror(errno), errno);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

jint close_internal(
    jint master_fd,
    jint child_pid
) {
    LOGI("Closing PTY session: masterFd=%d, childPid=%d", master_fd, child_pid);

    if (master_fd >= 0) {
        close(master_fd);
    }

    int exit_code = 0;

    if (child_pid > 0) {
        int status = 0;
        pid_t res = waitpid(child_pid, &status, WNOHANG);

        if (res == 0) {
            // Step 1: Send SIGHUP to allow clean shell/process shutdown
            kill(child_pid, SIGHUP);

            // Wait up to 100ms with polling
            for (int i = 0; i < 10; ++i) {
                res = waitpid(child_pid, &status, WNOHANG);
                if (res != 0) break;
                usleep(10000); // 10ms
            }

            // Step 2: Escalate to SIGTERM if still running
            if (res == 0) {
                kill(child_pid, SIGTERM);
                for (int i = 0; i < 5; ++i) {
                    res = waitpid(child_pid, &status, WNOHANG);
                    if (res != 0) break;
                    usleep(10000); // 10ms
                }
            }

            // Step 3: Escalate to SIGKILL to guarantee no zombie processes
            if (res == 0) {
                kill(child_pid, SIGKILL);
                waitpid(child_pid, &status, 0);
            }
        }

        if (WIFEXITED(status)) {
            exit_code = WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            exit_code = -WTERMSIG(status);
        }
    }

    LOGI("Subprocess PID %d terminated, exit code: %d", child_pid, exit_code);
    return exit_code;
}

} // namespace

/*
 * ============================================================================
 * JNI Exported Functions: com.antigravity.studio.pty.NativePty
 * ============================================================================
 */

extern "C" {

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeCreateSubprocess(
    JNIEnv* env,
    jobject /* thiz */,
    jstring executable,
    jobjectArray args,
    jobjectArray envp,
    jstring cwd,
    jint rows,
    jint cols,
    jintArray out_master_fd
) {
    return create_subprocess_internal(env, executable, args, envp, cwd, rows, cols, out_master_fd);
}

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeWrite(
    JNIEnv* env,
    jobject /* thiz */,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    return write_internal(env, master_fd, buffer, offset, length);
}

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeRead(
    JNIEnv* env,
    jobject /* thiz */,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    return read_internal(env, master_fd, buffer, offset, length);
}

JNIEXPORT jboolean JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeResize(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint master_fd,
    jint rows,
    jint cols
) {
    return resize_internal(master_fd, rows, cols);
}

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeClose(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint master_fd,
    jint child_pid
) {
    return close_internal(master_fd, child_pid);
}

/*
 * ============================================================================
 * JNI Exported Functions: com.antigravity.studio.core.pty.PtyNativeBridge
 * (Compatibility layer with SPEC-001 blueprint package)
 * ============================================================================
 */

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeCreateSubprocess(
    JNIEnv* env,
    jobject /* thiz */,
    jstring executable,
    jobjectArray args,
    jobjectArray envp,
    jstring cwd,
    jint rows,
    jint cols,
    jintArray out_master_fd
) {
    return create_subprocess_internal(env, executable, args, envp, cwd, rows, cols, out_master_fd);
}

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeWrite(
    JNIEnv* env,
    jobject /* thiz */,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    return write_internal(env, master_fd, buffer, offset, length);
}

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeRead(
    JNIEnv* env,
    jobject /* thiz */,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    return read_internal(env, master_fd, buffer, offset, length);
}

JNIEXPORT jboolean JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeResize(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint master_fd,
    jint rows,
    jint cols
) {
    return resize_internal(master_fd, rows, cols);
}

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeClose(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint master_fd,
    jint child_pid
) {
    return close_internal(master_fd, child_pid);
}

} // extern "C"
