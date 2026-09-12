#ifndef ANTIGRAVITY_STUDIO_NATIVE_PTY_H
#define ANTIGRAVITY_STUDIO_NATIVE_PTY_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * JNI bindings for com.antigravity.studio.pty.NativePty
 */

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeCreateSubprocess(
    JNIEnv* env,
    jobject thiz,
    jstring executable,
    jobjectArray args,
    jobjectArray envp,
    jstring cwd,
    jint rows,
    jint cols,
    jintArray out_master_fd
);

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeWrite(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
);

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeRead(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
);

JNIEXPORT jboolean JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeResize(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jint rows,
    jint cols
);

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_pty_NativePty_nativeClose(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jint child_pid
);

/*
 * Backward-compatible bindings for com.antigravity.studio.core.pty.PtyNativeBridge
 */

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeCreateSubprocess(
    JNIEnv* env,
    jobject thiz,
    jstring executable,
    jobjectArray args,
    jobjectArray envp,
    jstring cwd,
    jint rows,
    jint cols,
    jintArray out_master_fd
);

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeWrite(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
);

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeRead(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jbyteArray buffer,
    jint offset,
    jint length
);

JNIEXPORT jboolean JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeResize(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jint rows,
    jint cols
);

JNIEXPORT jint JNICALL
Java_com_antigravity_studio_core_pty_PtyNativeBridge_nativeClose(
    JNIEnv* env,
    jobject thiz,
    jint master_fd,
    jint child_pid
);

#ifdef __cplusplus
}
#endif

#endif // ANTIGRAVITY_STUDIO_NATIVE_PTY_H
