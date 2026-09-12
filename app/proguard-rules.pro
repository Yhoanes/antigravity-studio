# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.antigravity.studio.pty.** { *; }
-keep class com.antigravity.studio.core.pty.** { *; }
