package com.seventv.stickers.core.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PackageInstallerHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun createUnknownSourcesSettingsIntentTargetsPackage() {
        val intent = PackageInstallerHelper.createUnknownSourcesSettingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun createInstallIntentSetsApkMimeTypeAndGrantReadUriPermission() {
        val apkFile = tempFolder.newFile("update.apk")
        apkFile.writeText("dummy")
        val fakeUri = Uri.parse("content://${PackageInstallerHelper.FILE_PROVIDER_AUTHORITY}/updates/update.apk")

        val intent = PackageInstallerHelper.createInstallIntent(
            context = context,
            apkFile = apkFile,
            uriResolver = { _, _, _ -> fakeUri }
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(fakeUri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun launchInstallReturnsPermissionRequiredWhenUnknownSourcesDenied() {
        val apkFile = tempFolder.newFile("update.apk")
        apkFile.writeText("dummy")

        val result = PackageInstallerHelper.launchInstall(
            context = context,
            apkFile = apkFile,
            canInstallChecker = { false }
        )

        assertTrue(result is InstallLaunchResult.RequiresUnknownSourcesPermission)
        val settingsIntent = (result as InstallLaunchResult.RequiresUnknownSourcesPermission).settingsIntent
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settingsIntent.action)
    }

    @Test
    fun launchInstallStartsInstallActivityWhenPermissionGranted() {
        val apkFile = tempFolder.newFile("update.apk")
        apkFile.writeText("dummy")
        val fakeUri = Uri.parse("content://${PackageInstallerHelper.FILE_PROVIDER_AUTHORITY}/updates/update.apk")

        val result = PackageInstallerHelper.launchInstall(
            context = context,
            apkFile = apkFile,
            canInstallChecker = { true },
            uriResolver = { _, _, _ -> fakeUri }
        )

        assertTrue(result is InstallLaunchResult.LaunchedInstaller)
        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(fakeUri, started.data)
    }

    @Test
    fun launchInstallReturnsErrorWhenApkDoesNotExist() {
        val missing = File(tempFolder.root, "missing.apk")
        val result = PackageInstallerHelper.launchInstall(
            context = context,
            apkFile = missing,
            canInstallChecker = { true }
        )
        assertTrue(result is InstallLaunchResult.Error)
        assertFalse(missing.exists())
    }
}