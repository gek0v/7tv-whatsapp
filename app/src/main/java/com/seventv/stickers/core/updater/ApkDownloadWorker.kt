package com.seventv.stickers.core.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.seventv.stickers.R
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class ApkDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "URL de descarga inválida."))
        val apkFileName = inputData.getString(KEY_APK_FILENAME)?.takeIf { it.isNotBlank() }
            ?: "seventv-update.apk"
        val versionName = inputData.getString(KEY_VERSION_NAME) ?: ""
        val expectedSha256 = inputData.getString(KEY_EXPECTED_SHA256)
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, 0L)

        val updatesDir = File(applicationContext.cacheDir, UPDATES_DIR_NAME).apply { mkdirs() }
        val sanitizedFileName = apkFileName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
        val destinationFile = File(updatesDir, sanitizedFileName)

        cleanupOldUpdates(updatesDir, sanitizedFileName)

        ensureNotificationChannel()
        try {
            setForeground(createForegroundInfo(versionName = versionName, progressPercent = 0, indeterminate = true))
        } catch (_: Exception) {
            // Best-effort foreground promotion
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(30L, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        var lastProgressUpdateMs = 0L

        val outcome = ApkDownloadHelper.downloadWithResume(
            okHttpClient = okHttpClient,
            url = apkUrl,
            destinationFile = destinationFile,
            expectedSha256 = expectedSha256,
            expectedSizeBytes = expectedSize,
            onProgress = { downloadedBytes, totalBytes ->
                val now = System.currentTimeMillis()
                val progressPercent = if (totalBytes > 0L) {
                    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    -1
                }
                if (now - lastProgressUpdateMs >= 250L || progressPercent == 100) {
                    lastProgressUpdateMs = now
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS_PERCENT to progressPercent.coerceAtLeast(0),
                            KEY_DOWNLOADED_BYTES to downloadedBytes,
                            KEY_TOTAL_BYTES to totalBytes
                        )
                    )
                    try {
                        setForeground(
                            createForegroundInfo(
                                versionName = versionName,
                                progressPercent = progressPercent.coerceAtLeast(0),
                                indeterminate = progressPercent < 0
                            )
                        )
                    } catch (_: Exception) {
                        // Ignore foreground update failure
                    }
                }
            }
        )

        return when (outcome) {
            is ApkDownloadOutcome.Success -> {
                Result.success(
                    workDataOf(
                        KEY_APK_PATH to outcome.file.absolutePath,
                        KEY_VERIFIED_SHA256 to outcome.verifiedSha256,
                        KEY_VERSION_NAME to versionName
                    )
                )
            }
            is ApkDownloadOutcome.Failure -> {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to outcome.message)
                )
            }
        }
    }

    private fun cleanupOldUpdates(updatesDir: File, keepFileName: String) {
        updatesDir.listFiles()?.forEach { file ->
            val isTarget = file.name == keepFileName || file.name == "$keepFileName.part"
            if (!isTarget) {
                file.delete()
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de la app",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descarga de actualizaciones de 7TV Stickers"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(
        versionName: String,
        progressPercent: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        val title = if (versionName.isNotBlank()) {
            "Descargando 7TV Stickers v$versionName"
        } else {
            "Descargando actualización"
        }
        val text = if (indeterminate) "Preparando descarga..." else "$progressPercent%"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, indeterminate)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "seventv_apk_download"
        const val UPDATES_DIR_NAME = "updates"
        const val CHANNEL_ID = "seventv_app_updates"
        const val NOTIFICATION_ID = 9001

        const val KEY_APK_URL = "apk_url"
        const val KEY_APK_FILENAME = "apk_filename"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val KEY_EXPECTED_SIZE = "expected_size"

        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_APK_PATH = "apk_path"
        const val KEY_VERIFIED_SHA256 = "verified_sha256"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}