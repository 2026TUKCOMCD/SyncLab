package com.tukorea.synclab_mobile.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// DataStore 인스턴스 생성
private val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {

    // DataStore 키 정의
    private object PreferencesKeys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_UPLOAD = booleanPreferencesKey("auto_upload")
    }

    // Wi-Fi 전용 여부 흐름 (Flow)
    val isWifiOnlyFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] ?: true // 기본값 true
        }

    // 자동 업로드 여부 흐름 (Flow)
    val isAutoUploadFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_UPLOAD] ?: false // 기본값 false
        }

    // Wi-Fi 설정 업데이트
    suspend fun updateWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY] = enabled
        }
    }

    // 자동 업로드 설정 업데이트
    suspend fun updateAutoUpload(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPLOAD] = enabled
        }
    }
}