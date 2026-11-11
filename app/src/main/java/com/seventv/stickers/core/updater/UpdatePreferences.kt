package com.seventv.stickers.core.updater

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_update_preferences"
)

data class UpdateSettings(
    val autoCheckEnabled: Boolean = true,
    val wifiOnlyDownload: Boolean = false
)

data class UpdatePreferencesSnapshot(
    val etag: String? = null,
    val cachedReleaseJson: String? = null,
    val lastCheckTimestampMs: Long = 0L,
    val snoozedUntilMs: Long = 0L,
    val snoozedVersion: String? = null,
    val autoCheckEnabled: Boolean = true,
    val wifiOnlyDownload: Boolean = false
)

interface UpdatePreferencesRepository {
    val settingsFlow: Flow<UpdateSettings>
    suspend fun getSnapshot(): UpdatePreferencesSnapshot
    suspend fun saveCheckResult(etag: String?, cachedReleaseJson: String?, checkedAtMs: Long)
    suspend fun updateLastCheckTimestamp(checkedAtMs: Long)
    suspend fun snoozeVersion(version: String, snoozedUntilMs: Long)
    suspend fun clearSnooze()
    suspend fun setAutoCheckEnabled(enabled: Boolean)
    suspend fun setWifiOnlyDownload(wifiOnly: Boolean)
}

class DataStoreUpdatePreferencesRepository(
    private val context: Context
) : UpdatePreferencesRepository {

    private object Keys {
        val ETAG = stringPreferencesKey("etag")
        val CACHED_RELEASE_JSON = stringPreferencesKey("cached_release_json")
        val LAST_CHECK_TIMESTAMP_MS = longPreferencesKey("last_check_timestamp_ms")
        val SNOOZED_UNTIL_MS = longPreferencesKey("snoozed_until_ms")
        val SNOOZED_VERSION = stringPreferencesKey("snoozed_version")
        val AUTO_CHECK_ENABLED = booleanPreferencesKey("auto_check_enabled")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
    }

    override val settingsFlow: Flow<UpdateSettings> = context.updateDataStore.data.map { prefs ->
        UpdateSettings(
            autoCheckEnabled = prefs[Keys.AUTO_CHECK_ENABLED] ?: true,
            wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: false
        )
    }

    override suspend fun getSnapshot(): UpdatePreferencesSnapshot {
        val prefs = context.updateDataStore.data.first()
        return UpdatePreferencesSnapshot(
            etag = prefs[Keys.ETAG],
            cachedReleaseJson = prefs[Keys.CACHED_RELEASE_JSON],
            lastCheckTimestampMs = prefs[Keys.LAST_CHECK_TIMESTAMP_MS] ?: 0L,
            snoozedUntilMs = prefs[Keys.SNOOZED_UNTIL_MS] ?: 0L,
            snoozedVersion = prefs[Keys.SNOOZED_VERSION],
            autoCheckEnabled = prefs[Keys.AUTO_CHECK_ENABLED] ?: true,
            wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: false
        )
    }

    override suspend fun saveCheckResult(
        etag: String?,
        cachedReleaseJson: String?,
        checkedAtMs: Long
    ) {
        context.updateDataStore.edit { prefs ->
            if (etag != null) {
                prefs[Keys.ETAG] = etag
            } else {
                prefs.remove(Keys.ETAG)
            }
            if (cachedReleaseJson != null) {
                prefs[Keys.CACHED_RELEASE_JSON] = cachedReleaseJson
            } else {
                prefs.remove(Keys.CACHED_RELEASE_JSON)
            }
            prefs[Keys.LAST_CHECK_TIMESTAMP_MS] = checkedAtMs
        }
    }

    override suspend fun updateLastCheckTimestamp(checkedAtMs: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.LAST_CHECK_TIMESTAMP_MS] = checkedAtMs
        }
    }

    override suspend fun snoozeVersion(version: String, snoozedUntilMs: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.SNOOZED_VERSION] = version
            prefs[Keys.SNOOZED_UNTIL_MS] = snoozedUntilMs
        }
    }

    override suspend fun clearSnooze() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(Keys.SNOOZED_VERSION)
            prefs.remove(Keys.SNOOZED_UNTIL_MS)
        }
    }

    override suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.AUTO_CHECK_ENABLED] = enabled
        }
    }

    override suspend fun setWifiOnlyDownload(wifiOnly: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY_DOWNLOAD] = wifiOnly
        }
    }
}