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
    private val currentVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _state = MutableStateFlow(
        AppUpdateState(currentVersion = currentVersionProvider())
    )
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var activeUpdateInfo: AppUpdateInfo? = null

    init {
        _state.update { it.copy(currentVersion = currentVersionProvider()) }
        checkAndCleanInstalledUpdate(context)

        preferences.settingsFlow
            .onEach { settings ->
                _state.update { it.copy(settings = settings) }
            }
            .launchIn(scope)

        observeActiveWorkIfAny()
    }

    private fun clearWorkManagerDownloads() {
        val wm = runCatching { workManagerProvider() }.getOrNull() ?: return
        try {
            wm.cancelUniqueWork(ApkDownloadWorker.UNIQUE_WORK_NAME)
            wm.pruneWork()
        } catch (_: Exception) {
            // Ignore WorkManager errors
        }
    }

    private fun clearCachedApks(targetContext: Context = context) {
        runCatching {
            val updatesDir = File(targetContext.cacheDir, ApkDownloadWorker.UPDATES_DIR_NAME)
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        }
    }

    private fun resetToIdleAndClean(targetContext: Context = context) {
        activeUpdateInfo = null
        clearWorkManagerDownloads()
        clearCachedApks(targetContext)
        scope.launch(ioDispatcher) {
            preferences.clearSnooze()
        }
        _state.update {
            it.copy(
                status = UpdateStatus.Idle,
                isChangelogDialogVisible = false,
                currentVersion = currentVersionProvider()
            )
        }
    }

    fun checkAndCleanInstalledUpdate(targetContext: Context = context) {
        val currentVersion = currentVersionProvider()
        _state.update { it.copy(currentVersion = currentVersion) }

        val currentStatus = _state.value.status
        val info = when (currentStatus) {
            is UpdateStatus.Available -> currentStatus.info
            is UpdateStatus.Downloading -> currentStatus.info
            is UpdateStatus.ReadyToInstall -> currentStatus.info
            is UpdateStatus.AwaitingInstallPermission -> currentStatus.info
            is UpdateStatus.Error -> currentStatus.lastAvailableInfo
            else -> null
        } ?: activeUpdateInfo

        if (info != null && !SemVer.isUpdateAvailable(currentVersion, info.latestVersion)) {
            resetToIdleAndClean(targetContext)
            return
        }

        cleanObsoleteCachedApks(targetContext, currentVersion)
    }

    private fun cleanObsoleteCachedApks(targetContext: Context, currentVersion: String) {
        runCatching {
            val updatesDir = File(targetContext.cacheDir, ApkDownloadWorker.UPDATES_DIR_NAME)
            if (!updatesDir.exists()) return@runCatching
            updatesDir.listFiles()?.forEach { file ->
                if (file.extension.equals("apk", ignoreCase = true)) {
                    val archiveInfo = targetContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    val archiveVersion = archiveInfo?.versionName
                    if (archiveVersion != null && !SemVer.isUpdateAvailable(currentVersion, archiveVersion)) {
                        file.delete()
                    }
                }
            }
        }
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
        val currentVersion = currentVersionProvider()
        val output = workInfo.outputData
        val workVersionName = output.getString(ApkDownloadWorker.KEY_VERSION_NAME)
        val info = activeUpdateInfo

        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
            val targetVersion = workVersionName ?: info?.latestVersion
            if (targetVersion != null && !SemVer.isUpdateAvailable(currentVersion, targetVersion)) {
                resetToIdleAndClean(context)
                return
            }
        }

        if (info == null) {
            if (workInfo.state == WorkInfo.State.SUCCEEDED &&
                (workVersionName.isNullOrBlank() || !SemVer.isUpdateAvailable(currentVersion, workVersionName))
            ) {
                clearWorkManagerDownloads()
                clearCachedApks(context)
            }
            return
        }

        if (!SemVer.isUpdateAvailable(currentVersion, info.latestVersion)) {
            resetToIdleAndClean(context)
            return
        }

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
                val apkPath = output.getString(ApkDownloadWorker.KEY_APK_PATH)
                val verified = output.getBoolean(ApkDownloadWorker.KEY_VERIFIED_SHA256, false)
                if (apkPath != null) {
                    val file = File(apkPath)
                    if (file.exists()) {
                        _state.update {
                            it.copy(
                                status = UpdateStatus.ReadyToInstall(
                                    info = info,
                                    apkFile = file,
                                    verifiedSha256 = verified
                                )
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(status = UpdateStatus.Available(info))
                        }
                    }
                }
            }
            WorkInfo.State.FAILED -> {
                val errorMsg = workInfo.outputData.getString(ApkDownloadWorker.KEY_ERROR_MESSAGE)
                    ?: "Error durante la descarga de la actualizaci?n."
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
            val currentVersion = currentVersionProvider()
            val currentStatus = _state.value.status
            val pendingInfo = when (currentStatus) {
                is UpdateStatus.Downloading -> currentStatus.info
                is UpdateStatus.ReadyToInstall -> currentStatus.info
                is UpdateStatus.AwaitingInstallPermission -> currentStatus.info
                is UpdateStatus.Available -> currentStatus.info
                else -> activeUpdateInfo
            }

            if (pendingInfo != null && !SemVer.isUpdateAvailable(currentVersion, pendingInfo.latestVersion)) {
                resetToIdleAndClean(context)
            } else if (currentStatus is UpdateStatus.Downloading || currentStatus is UpdateStatus.ReadyToInstall) {
                return@launch
            }

            when (val result = checker.checkForUpdate(force = false)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    if (SemVer.isUpdateAvailable(currentVersionProvider(), result.info.latestVersion)) {
                        activeUpdateInfo = result.info
                        _state.update {
                            it.copy(
                                status = UpdateStatus.Available(result.info),
                                isBannerDismissedInSession = false
                            )
                        }
                    } else {
                        resetToIdleAndClean(context)
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    val previous = _state.value.status
                    if (previous is UpdateStatus.Available ||
                        previous is UpdateStatus.ReadyToInstall ||
                        previous is UpdateStatus.AwaitingInstallPermission ||
                        previous is UpdateStatus.Downloading
                    ) {
                        resetToIdleAndClean(context)
                    }
                }
                is UpdateCheckResult.Skipped -> {
                    val latest = activeUpdateInfo?.latestVersion
                    if (latest != null && !SemVer.isUpdateAvailable(currentVersionProvider(), latest)) {
                        resetToIdleAndClean(context)
                    }
                }
                is UpdateCheckResult.Error -> {
                    // Silent on background check failure
                }
            }
        }
    }

    fun checkForUpdatesNow() {
        scope.launch {
            val currentVersion = currentVersionProvider()
            val currentStatus = _state.value.status
            val pendingInfo = when (currentStatus) {
                is UpdateStatus.Available -> currentStatus.info
                is UpdateStatus.ReadyToInstall -> currentStatus.info
                is UpdateStatus.AwaitingInstallPermission -> currentStatus.info
                else -> activeUpdateInfo
            }
            if (pendingInfo != null && !SemVer.isUpdateAvailable(currentVersion, pendingInfo.latestVersion)) {
                resetToIdleAndClean(context)
            }

            _state.update { it.copy(status = UpdateStatus.Checking) }
            val result = checker.checkForUpdate(force = true)
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    if (SemVer.isUpdateAvailable(currentVersionProvider(), result.info.latestVersion)) {
                        activeUpdateInfo = result.info
                        _state.update {
                            it.copy(
                                status = UpdateStatus.Available(result.info),
                                isBannerDismissedInSession = false,
                                isChangelogDialogVisible = true
                            )
                        }
                    } else {
                        resetToIdleAndClean(context)
                        _state.update { it.copy(status = UpdateStatus.UpToDate()) }
                    }
                }
                is UpdateCheckResult.UpToDate, UpdateCheckResult.Skipped -> {
                    resetToIdleAndClean(context)
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
        val currentVersion = currentVersionProvider()
        if (!SemVer.isUpdateAvailable(currentVersion, targetInfo.latestVersion)) {
            resetToIdleAndClean(context)
            return
        }
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
        val currentVersion = currentVersionProvider()
        val nextStatus = if (info != null && SemVer.isUpdateAvailable(currentVersion, info.latestVersion)) {
            UpdateStatus.Available(info)
        } else {
            UpdateStatus.Idle
        }
        _state.update {
            it.copy(status = nextStatus)
        }
    }

    fun installDownloadedApk(context: Context) {
        val currentVersion = currentVersionProvider()
        val current = _state.value.status
        val (info, apkFile, verified) = when (current) {
            is UpdateStatus.ReadyToInstall -> Triple(current.info, current.apkFile, current.verifiedSha256)
            is UpdateStatus.AwaitingInstallPermission -> Triple(current.info, current.apkFile, current.verifiedSha256)
            else -> return
        }

        if (!SemVer.isUpdateAvailable(currentVersion, info.latestVersion)) {
            resetToIdleAndClean(context)
            return
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
                        status = UpdateStatus.Error(
                            result.message,
                            lastAvailableInfo = info
                        )
                    )
                }
            }
        }
    }

    fun onAppResumed(context: Context) {
        checkAndCleanInstalledUpdate(context)
        onResumeCheckInstallPermission(context)
    }

    fun onResumeCheckInstallPermission(context: Context) {
        val current = _state.value.status
        if (current is UpdateStatus.AwaitingInstallPermission) {
            val currentVersion = currentVersionProvider()
            if (!SemVer.isUpdateAvailable(currentVersion, current.info.latestVersion)) {
                resetToIdleAndClean(context)
                return
            }
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
        val currentVersion = currentVersionProvider()
        val current = _state.value.status
        val info = when (current) {
            is UpdateStatus.Available -> current.info
            is UpdateStatus.Downloading -> current.info
            is UpdateStatus.ReadyToInstall -> current.info
            is UpdateStatus.AwaitingInstallPermission -> current.info
            is UpdateStatus.Error -> current.lastAvailableInfo
            else -> activeUpdateInfo
        }
        if (info != null && !SemVer.isUpdateAvailable(currentVersion, info.latestVersion)) {
            resetToIdleAndClean(context)
            return
        }
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
