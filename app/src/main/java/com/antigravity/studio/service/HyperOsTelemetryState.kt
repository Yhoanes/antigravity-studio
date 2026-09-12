package com.antigravity.studio.service

/**
 * Estado de telemetría y salud del servicio de fondo en HyperOS.
 * Diseñado para Xiaomi Pad 6 (Snapdragon 870, 6 GB RAM, HyperOS).
 * Conforme a SPEC-003.
 */
data class HyperOsTelemetryState(
    val isServiceRunning: Boolean = false,
    val isWakeLockHeld: Boolean = false,
    val isWifiLockHeld: Boolean = false,
    val processRamUsageMb: Long = 0L,
    val totalDeviceRamMb: Long = 6144L, // 6 GB en Xiaomi Pad 6
    val cpuTemperatureCelsius: Float = 0.0f
)
