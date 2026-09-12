package com.antigravity.studio.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antigravity.studio.MainActivity
import com.antigravity.studio.project.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio de primer plano (ForegroundService) para blindaje y persistencia en segundo plano en Xiaomi HyperOS.
 *
 * Mantiene la ejecución ininterrumpida de Antigravity Agent y el árbol POSIX PTY:
 * - Adquiere PowerManager.PARTIAL_WAKE_LOCK ("AntigravityStudio::AgentExecutionLock")
 * - Adquiere WifiManager.WifiLock (WIFI_MODE_FULL_LOW_LATENCY)
 * - Mantiene notificación continua con telemetría y botón [⏹ Detener]
 * - Emite notificaciones Heads-Up con vibración háptica en "studio_alerts_channel"
 *
 * Conforme a SPEC-003.
 */
class StudioBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var telemetryJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando StudioBackgroundService en HyperOS...")
        createNotificationChannels()
        acquireLocks()
        startTelemetryLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Acción ACTION_STOP_SERVICE recibida, deteniendo persistencia...")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_EXECUTION -> {
                Log.i(TAG, "Acción ACTION_STOP_EXECUTION recibida desde notificación.")
                onStopExecutionRequested?.invoke()
                return START_STICKY
            }
        }

        val taskName = intent?.getStringExtra(EXTRA_TASK_NAME) ?: "Ejecución Agéntica"
        val activeProj = ProjectManager.getInstance().activeProject.value
        val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: activeProj?.name ?: "tateti"

        val notification = buildForegroundNotification(projectName, taskName)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando startForeground: ${e.message}", e)
        }

        updateTelemetry(this)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Destruyendo StudioBackgroundService, liberando locks...")
        telemetryJob?.cancel()
        releaseLocks()
        _telemetryState.value = _telemetryState.value.copy(
            isServiceRunning = false,
            isWakeLockHeld = false,
            isWifiLockHeld = false
        )
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i(TAG, "WakeLock adquirido exitosamente: $WAKE_LOCK_TAG")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir WakeLock: ${e.message}")
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(wifiMode, WIFI_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i(TAG, "WifiLock adquirido exitosamente: $WIFI_LOCK_TAG (modo=$wifiMode)")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir WifiLock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock liberado.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando WakeLock: ${e.message}")
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.i(TAG, "WifiLock liberado.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando WifiLock: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Canal persistente de fondo
            val bgChannel = NotificationChannel(
                CHANNEL_BACKGROUND_ID,
                "Antigravity Studio Background",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activa la sesión de Antigravity Agent en HyperOS"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(bgChannel)

            // 2. Canal de alertas Heads-Up con vibración
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Antigravity Studio Alertas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas cuando el agente concluye tareas o solicita aprobación"
                vibrationPattern = longArrayOf(0, 150, 80, 150)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    private fun buildForegroundNotification(projectName: String, taskName: String): Notification {
        // PendingIntent para reabrir MainActivity
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent para botón [⏹ Detener]
        val stopIntent = Intent(this, StudioBackgroundService::class.java).apply {
            action = ACTION_STOP_EXECUTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_BACKGROUND_ID)
            .setContentTitle("⚡ Antigravity Studio")
            .setContentText("Ejecutando en segundo plano ($projectName) - HyperOS WakeLock activo")
            .setSubText(taskName)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "⏹ Detener", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startTelemetryLoop() {
        telemetryJob = serviceScope.launch {
            while (isActive) {
                updateTelemetry(this@StudioBackgroundService)
                delay(3000L) // Muestreo cada 3 segundos
            }
        }
    }

    companion object {
        private const val TAG = "StudioBgService"

        const val CHANNEL_BACKGROUND_ID = "studio_background_channel"
        const val CHANNEL_ALERTS_ID = "studio_alerts_channel"

        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002

        const val ACTION_START_PERSISTENCE = "com.antigravity.studio.ACTION_START_PERSISTENCE"
        const val ACTION_STOP_PERSISTENCE = "com.antigravity.studio.ACTION_STOP_PERSISTENCE"
        const val ACTION_STOP_SERVICE = "com.antigravity.studio.ACTION_STOP_SERVICE"
        const val ACTION_STOP_EXECUTION = "com.antigravity.studio.ACTION_STOP_EXECUTION"

        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_TASK_NAME = "extra_task_name"

        const val WAKE_LOCK_TAG = "AntigravityStudio::AgentExecutionLock"
        const val WIFI_LOCK_TAG = "AntigravityStudio::AgentWifiLock"

        private val _telemetryState = MutableStateFlow(HyperOsTelemetryState())
        val telemetryState: StateFlow<HyperOsTelemetryState> = _telemetryState.asStateFlow()
        val telemetryFlow: StateFlow<HyperOsTelemetryState> get() = telemetryState

        var onStopExecutionRequested: (() -> Unit)? = null

        /**
         * Helper estático para iniciar el servicio persistente de fondo.
         */
        fun startService(context: Context, taskName: String = "Antigravity Agent") {
            val activeProj = ProjectManager.getInstance().activeProject.value
            val projName = activeProj?.name ?: "tateti"
            val intent = Intent(context, StudioBackgroundService::class.java).apply {
                action = ACTION_START_PERSISTENCE
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_PROJECT_NAME, projName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startPersistence(context: Context, activeProjectName: String) {
            val intent = Intent(context, StudioBackgroundService::class.java).apply {
                action = ACTION_START_PERSISTENCE
                putExtra(EXTRA_PROJECT_NAME, activeProjectName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Helper estático para detener el servicio de fondo.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, StudioBackgroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun stopPersistence(context: Context) = stopService(context)

        /**
         * Helper estático para emitir una notificación Heads-Up cuando una tarea finaliza.
         */
        fun notifyTaskCompleted(context: Context, title: String, message: String) {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    2,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setContentTitle("Task Finished [✓]: $title")
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 150, 80, 150))
                    .build()

                val manager = NotificationManagerCompat.from(context)
                manager.notify(ALERT_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo emitir notifyTaskCompleted: ${e.message}")
            }
        }

        /**
         * Helper estático para emitir una notificación Heads-Up cuando se requiere aprobación táctil.
         */
        fun notifyApprovalNeeded(context: Context, toolName: String, summary: String) {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    3,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setContentTitle("Aprobación Requerida [Ctrl+K]")
                    .setContentText("$toolName: $summary")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 150, 80, 150))
                    .build()

                val manager = NotificationManagerCompat.from(context)
                manager.notify(ALERT_NOTIFICATION_ID + 1, notification)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo emitir notifyApprovalNeeded: ${e.message}")
            }
        }

        /**
         * Calcula y actualiza el estado de telemetría de HyperOS (RAM usada/total y estado de locks).
         */
        fun updateTelemetry(context: Context) {
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)

            var totalRamMb = 6144L // 6 GB Xiaomi Pad 6 por defecto
            try {
                val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actMgr?.getMemoryInfo(memInfo)
                if (memInfo.totalMem > 0L) {
                    totalRamMb = memInfo.totalMem / (1024 * 1024)
                }
            } catch (_: Exception) {}

            val current = _telemetryState.value
            _telemetryState.value = current.copy(
                isServiceRunning = true,
                processRamUsageMb = usedMb,
                totalDeviceRamMb = totalRamMb
            )
        }

        fun updateTelemetryStateDirectly(state: HyperOsTelemetryState) {
            _telemetryState.value = state
        }
    }
}
