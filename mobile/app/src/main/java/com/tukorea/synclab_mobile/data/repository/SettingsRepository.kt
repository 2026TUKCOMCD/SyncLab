package com.tukorea.synclab_mobile.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.tukorea.synclab_mobile.utils.userSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class SettingsRepository(private val context: Context) {


    private object PreferencesKeys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_UPLOAD = booleanPreferencesKey("auto_upload")
    }


    val isWifiOnlyFlow: Flow<Boolean> = context.userSettingsStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] ?: true
        }

    val isAutoUploadFlow: Flow<Boolean> = context.userSettingsStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_UPLOAD] ?: false
        }

    suspend fun updateWifiOnly(enabled: Boolean) {
        context.userSettingsStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] = enabled
        }
    }

    suspend fun updateAutoUpload(enabled: Boolean) {
        context.userSettingsStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPLOAD] = enabled
        }
    }
}