package com.antigravity.studio.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "UpdateManager"
private const val GITHUB_LATEST_RELEASE_URL =
    "https://api.github.com/repos/Yhoanes/antigravity-studio/releases/latest"

/**
 * Status representation for the in-app OTA update lifecycle.
 */
sealed interface UpdateStatus {
    data object IDLE : UpdateStatus
    data object CHECKING : UpdateStatus
    data class AVAILABLE(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String
    ) : UpdateStatus
    data class DOWNLOADING(val progress: Float) : UpdateStatus
    data object READY_TO_INSTALL : UpdateStatus
    data class UP_TO_DATE(val currentVersion: String) : UpdateStatus
    data class ERROR(val message: String) : UpdateStatus
}

/**
 * In-App OTA UpdateManager for Antigravity Studio.
 *
 * Checks GitHub Releases API for new versions, downloads the APK with DownloadManager,
 * and launches the package installer via FileProvider.
 */
object UpdateManager {

    const val CURRENT_VERSION = "v1.0.0"

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.IDLE)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private var activeDownloadId: Long? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressPollingJob: Job? = null

    /**
     * Checks for new releases asynchronously from GitHub API.
     */
    fun checkForUpdates(scope: CoroutineScope) {
        if (_updateStatus.value is UpdateStatus.CHECKING || _updateStatus.value is UpdateStatus.DOWNLOADING) {
            return
        }

        _updateStatus.value = UpdateStatus.CHECKING

        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_LATEST_RELEASE_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Antigravity-Studio-Updater")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    val json = JSONObject(response)

                    val tagName = json.optString("tag_name", "")
                    val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")
                    val assets = json.optJSONArray("assets")

                    var apkDownloadUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                val url = asset.optString("browser_download_url", "")
                                if (url.isNotEmpty()) {
                                    apkDownloadUrl = url
                                    break
                                }
                            }
                        }
                    }

                    if (apkDownloadUrl != null && isNewerVersion(tagName, CURRENT_VERSION)) {
                        Log.i(TAG, "Update available: $tagName (Current: $CURRENT_VERSION)")
                        _updateStatus.value = UpdateStatus.AVAILABLE(
                            version = tagName,
                            downloadUrl = apkDownloadUrl,
                            releaseNotes = releaseNotes
                        )
                    } else {
                        Log.i(TAG, "App is up to date: $CURRENT_VERSION (Latest: $tagName)")
                        _updateStatus.value = UpdateStatus.UP_TO_DATE(CURRENT_VERSION)
                    }
                } else {
                    val errorMsg = "GitHub API returned HTTP $responseCode"
                    Log.w(TAG, errorMsg)
                    _updateStatus.value = UpdateStatus.ERROR(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
                _updateStatus.value = UpdateStatus.ERROR(e.message ?: "Failed to check updates")
            }
        }
    }

    /**
     * Downloads the APK file using Android DownloadManager and initiates installation.
     */
    fun downloadAndInstall(context: Context, downloadUrl: String, scope: CoroutineScope) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        try {
            // Delete previously downloaded update file if any
            val destinationDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val apkFile = File(destinationDir, "antigravity-studio-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Antigravity Studio Update")
                setDescription("Descargando actualización...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "antigravity-studio-update.apk")
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            activeDownloadId = downloadId
            _updateStatus.value = UpdateStatus.DOWNLOADING(0f)

            // Register completion receiver
            unregisterReceiver(appContext)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        progressPollingJob?.cancel()
                        unregisterReceiver(appContext)
                        triggerApkInstall(appContext, apkFile)
                    }
                }
            }
            downloadReceiver = receiver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            // Poll progress
            progressPollingJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            cursor.close()
                            break
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            cursor.close()
                            _updateStatus.value = UpdateStatus.ERROR("La descarga del APK falló.")
                            break
                        } else if (bytesTotal > 0) {
                            val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            _updateStatus.value = UpdateStatus.DOWNLOADING(progress)
                        }
                        cursor.close()
                    }
                    delay(500)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download: ${e.message}", e)
            _updateStatus.value = UpdateStatus.ERROR("Error al iniciar descarga: ${e.message}")
        }
    }

    /**
     * Launches the Android Package Installer with FileProvider URI.
     */
    private fun triggerApkInstall(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _updateStatus.value = UpdateStatus.ERROR("Archivo APK no encontrado tras la descarga.")
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            _updateStatus.value = UpdateStatus.READY_TO_INSTALL
            Log.i(TAG, "Launched package installer for: $apkUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            _updateStatus.value = UpdateStatus.ERROR("Fallo al abrir instalador: ${e.message}")
        }
    }

    private fun unregisterReceiver(context: Context) {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            downloadReceiver = null
        }
    }

    /**
     * Compares two semantic version strings (e.g., "v1.0.1" vs "v1.0.0").
     * Returns true if remote is strictly newer than current.
     */
    fun isNewerVersion(remoteTag: String, currentTag: String): Boolean {
        try {
            val remoteParts = remoteTag.trim().removePrefix("v").split(".")
            val currentParts = currentTag.trim().removePrefix("v").split(".")

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrNull(i)?.toIntOrNull() ?: 0
                val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error comparing versions '$remoteTag' and '$currentTag': ${e.message}")
        }
        return false
    }

    fun dismiss() {
        _updateStatus.value = UpdateStatus.IDLE
    }
}
