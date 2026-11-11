package com.seventv.stickers.core.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.seventv.stickers.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(val info: AppUpdateInfo) : UpdateStatus
    data class Downloading(
        val info: AppUpdateInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkFile: File,
        val verifiedSha256: Boolean
    ) : UpdateStatus
    data class AwaitingInstallPermission(
        val info: AppUpdateInfo,
        val apkFile: File,
        val verifiedSha256: Boolean
    ) : UpdateStatus
    data class UpToDate(val checkedAtMs: Long = System.currentTimeMillis()) : UpdateStatus
    data class Error(val message: String, val lastAvailableInfo: AppUpdateInfo? = null) : UpdateStatus
}

data class AppUpdateState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val settings: UpdateSettings = UpdateSettings(),
    val isChangelogDialogVisible: Boolean = false,
    val isSettingsDialogVisible: Boolean = false,
    val isBannerDismissedInSession: Boolean = false,
    val currentVersion: String = BuildConfig.VERSION_NAME
)

class AppUpdateManager(
    private val context: Context,
    private val checker: AppUpdateChecker,
    private val preferences: UpdatePreferencesRepository,
    private val workManagerProvider: () -> WorkManager = { WorkManager.getInstance(context) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _state = MutableStateFlow(AppUpdateState(currentVersion = BuildConfig.VERSION_NAME))
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var activeUpdateInfo: AppUpdateInfo? = null

    init {
        preferences.settingsFlow
            .onEach { settings ->
                _state.update { it.copy(settings = settings) }
            }
            .launchIn(scope)

        observeActiveWorkIfAny()
    }

    private fun observeActiveWorkIfAny() {
        val wm = runCatching { workManagerProvider() }.getOrNull() ?: return
        wm.getWorkInfosForUniqueWorkFlow(ApkDownloadWorker.UNIQUE_WORK_NAME)
            .catch { /* ignore WorkManager DB errors */ }
            .onEach { workInfoList ->
                val workInfo = workInfoList.firstOrNull() ?: return@onEach
                handleWorkInfoUpdate(workInfo)
            }
            .launchIn(scope)
    }

    private fun handleWorkInfoUpdate(workInfo: WorkInfo) {
        val info = activeUpdateInfo ?: return
        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress
                val percent = progress.getInt(ApkDownloadWorker.KEY_PROGRESS_PERCENT, 0)
                val downloaded = progress.getLong(ApkDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                val total = progress.getLong(ApkDownloadWorker.KEY_TOTAL_BYTES, 0L)
                _state.update {
                    it.copy(
                        status = UpdateStatus.Downloading(
                            info = info,
                            progressPercent = percent,
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                val output = workInfo.outputData
                val apkPath = output.getString(ApkDownloadWorker.KEY_APK_PATH)
                val verified = output.getBoolean(ApkDownloadWorker.KEY_VERIFIED_SHA256, false)
                if (apkPath != null) {
                    val file = File(apkPath)
                    _state.update {
                        it.copy(
                            status = UpdateStatus.ReadyToInstall(
                                info = info,
                                apkFile = file,
                                verifiedSha256 = verified
                            )
                        )
                    }
                }
            }
            WorkInfo.State.FAILED -> {
                val errorMsg = workInfo.outputData.getString(ApkDownloadWorker.KEY_ERROR_MESSAGE)
                    ?: "Error durante la descarga de la actualización."
                _state.update {
                    it.copy(
                        status = UpdateStatus.Error(
                            message = errorMsg,
                            lastAvailableInfo = info
                    )
                )
            }
            }
            WorkInfo.State.CANCELLED -> {
                _state.update {
                    it.copy(status = UpdateStatus.Available(info))
                }
            }
            else -> Unit
        }
    }

    fun checkForUpdatesIfNeeded() {
        scope.launch(ioDispatcher) {
            val currentStatus = _state.value.status
            if (currentStatus is UpdateStatus.Downloading || currentStatus is UpdateStatus.ReadyToInstall) {
                return@launch
            }
            when (val result = checker.checkForUpdate(force = false)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    activeUpdateInfo = result.info
                    _state.update {
                        it.copy(
                            status = UpdateStatus.Available(result.info),
                            isBannerDismissedInSession = false
                        )
                    }
                }
                is UpdateCheckResult.UpToDate, UpdateCheckResult.Skipped -> {
                    // Silent
                }
                is UpdateCheckResult.Error -> {
                    // Silent on background check failure
                }
            }
        }
    }

    fun checkForUpdatesNow() {
        scope.launch {
            _state.update { it.copy(status = UpdateStatus.Checking) }
            val result = checker.checkForUpdate(force = true)
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    activeUpdateInfo = result.info
                    _state.update {
                        it.copy(
                            status = UpdateStatus.Available(result.info),
                            isBannerDismissedInSession = false,
                            isChangelogDialogVisible = true
                        )
                    }
                }
                is UpdateCheckResult.UpToDate, UpdateCheckResult.Skipped -> {
                    _state.update { it.copy(status = UpdateStatus.UpToDate()) }
                }
                is UpdateCheckResult.Error -> {
                    _state.update {
                        it.copy(
                            status = UpdateStatus.Error(
                                message = result.message,
                                lastAvailableInfo = activeUpdateInfo
                            )
                        )
                    }
                }
            }
        }
    }

    fun startDownload(info: AppUpdateInfo? = activeUpdateInfo) {
        val targetInfo = info ?: return
        activeUpdateInfo = targetInfo
        val wifiOnly = _state.value.settings.wifiOnlyDownload
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val inputData = workDataOf(
            ApkDownloadWorker.KEY_APK_URL to targetInfo.apkUrl,
            ApkDownloadWorker.KEY_APK_FILENAME to targetInfo.apkFileName,
            ApkDownloadWorker.KEY_VERSION_NAME to targetInfo.latestVersion,
            ApkDownloadWorker.KEY_EXPECTED_SHA256 to targetInfo.expectedSha256,
            ApkDownloadWorker.KEY_EXPECTED_SIZE to targetInfo.apkSizeBytes
        )

        val workRequest = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        _state.update {
            it.copy(
                status = UpdateStatus.Downloading(
                    info = targetInfo,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = targetInfo.apkSizeBytes
                )
            )
        }

        try {
            workManagerProvider().enqueueUniqueWork(
                ApkDownloadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = UpdateStatus.Error(
                        message = "No se pudo encolar la descarga: ${e.message}",
                        lastAvailableInfo = targetInfo
                    )
                )
            }
        }
    }

    fun cancelDownload() {
        try {
            workManagerProvider().cancelUniqueWork(ApkDownloadWorker.UNIQUE_WORK_NAME)
        } catch (_: Exception) {
            // Ignore
        }
        val info = activeUpdateInfo
        _state.update {
            it.copy(status = if (info != null) UpdateStatus.Available(info) else UpdateStatus.Idle)
        }
    }

    fun installDownloadedApk(context: Context) {
        val current = _state.value.status
        val (info, apkFile, verified) = when (current) {
            is UpdateStatus.ReadyToInstall -> Triple(current.info, current.apkFile, current.verifiedSha256)
            is UpdateStatus.AwaitingInstallPermission -> Triple(current.info, current.apkFile, current.verifiedSha256)
            else -> return
        }

        when (val result = PackageInstallerHelper.launchInstall(context, apkFile)) {
            is InstallLaunchResult.LaunchedInstaller -> {
                _state.update {
                    it.copy(isChangelogDialogVisible = false)
                }
            }
            is InstallLaunchResult.RequiresUnknownSourcesPermission -> {
                _state.update {
                    it.copy(
                        status = UpdateStatus.AwaitingInstallPermission(
                            info = info,
                            apkFile = apkFile,
                            verifiedSha256 = verified
                        ),
                        isChangelogDialogVisible = true
                    )
                }
                context.startActivity(result.settingsIntent)
            }
            is InstallLaunchResult.Error -> {
                _state.update {
                    it.copy(
                        status = UpdateStatus.Error(result.message, lastAvailableInfo = info
                    )
                )
            }
            }
        }
    }

    fun onResumeCheckInstallPermission(context: Context) {
        val current = _state.value.status
        if (current is UpdateStatus.AwaitingInstallPermission) {
            if (PackageInstallerHelper.canRequestPackageInstalls(context)) {
                _state.update {
                    it.copy(
                        status = UpdateStatus.ReadyToInstall(
                            info = current.info,
                            apkFile = current.apkFile,
                            verifiedSha256 = current.verifiedSha256
                        )
                    )
                }
                installDownloadedApk(context)
            }
        }
    }

    fun remindLater(hours: Long = 24L) {
        val info = activeUpdateInfo ?: return
        val snoozeDurationMs = hours * 60L * 60L * 1000L
        val snoozedUntil = System.currentTimeMillis() + snoozeDurationMs
        scope.launch(ioDispatcher) {
            preferences.snoozeVersion(info.latestVersion, snoozedUntil)
        }
        _state.update {
            it.copy(
                isChangelogDialogVisible = false,
                isBannerDismissedInSession = true,
                status = UpdateStatus.Idle
            )
        }
    }

    fun openChangelogDialog() {
        _state.update { it.copy(isChangelogDialogVisible = true) }
    }

    fun closeChangelogDialog() {
        _state.update { it.copy(isChangelogDialogVisible = false) }
    }

    fun openSettingsDialog() {
        _state.update { it.copy(isSettingsDialogVisible = true) }
    }

    fun closeSettingsDialog() {
        _state.update { it.copy(isSettingsDialogVisible = false) }
    }

    fun dismissBanner() {
        _state.update { it.copy(isBannerDismissedInSession = true) }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        scope.launch(ioDispatcher) {
            preferences.setAutoCheckEnabled(enabled)
        }
    }

    fun setWifiOnlyDownload(wifiOnly: Boolean) {
        scope.launch(ioDispatcher) {
            preferences.setWifiOnlyDownload(wifiOnly)
        }
    }
}
