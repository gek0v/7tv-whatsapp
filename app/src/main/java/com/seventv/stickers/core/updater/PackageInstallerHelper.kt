package com.seventv.stickers.core.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface InstallLaunchResult {
    data object LaunchedInstaller : InstallLaunchResult
    data class RequiresUnknownSourcesPermission(val settingsIntent: Intent) : InstallLaunchResult
    data class Error(val message: String) : InstallLaunchResult
}

object PackageInstallerHelper {

    const val FILE_PROVIDER_AUTHORITY = "com.seventv.stickers.fileprovider"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getApkContentUri(
        context: Context,
        apkFile: File,
        uriResolver: (Context, String, File) -> Uri = { ctx, auth, file ->
            FileProvider.getUriForFile(ctx, auth, file)
        }
    ): Uri {
        return uriResolver(context, FILE_PROVIDER_AUTHORITY, apkFile)
    }

    fun createInstallIntent(
        context: Context,
        apkFile: File,
        uriResolver: (Context, String, File) -> Uri = { ctx, auth, file ->
            FileProvider.getUriForFile(ctx, auth, file)
        }
    ): Intent {
        val apkUri = getApkContentUri(context, apkFile, uriResolver)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchInstall(
        context: Context,
        apkFile: File,
        canInstallChecker: (Context) -> Boolean = ::canRequestPackageInstalls,
        uriResolver: (Context, String, File) -> Uri = { ctx, auth, file ->
            FileProvider.getUriForFile(ctx, auth, file)
        }
    ): InstallLaunchResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            return InstallLaunchResult.Error("El archivo APK descargado no existe.")
        }

        if (!canInstallChecker(context)) {
            return InstallLaunchResult.RequiresUnknownSourcesPermission(
                settingsIntent = createUnknownSourcesSettingsIntent(context)
            )
        }

        return try {
            val installIntent = createInstallIntent(context, apkFile, uriResolver)
            context.startActivity(installIntent)
            InstallLaunchResult.LaunchedInstaller
        } catch (e: Exception) {
            InstallLaunchResult.Error("No se pudo abrir el instalador de Android: ${e.message ?: "error desconocido"}")
        }
    }
}