package com.tukorea.synclab_mobile.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
// [삭제] import androidx.datastore.preferences.preferencesDataStore
// [추가] utils에 만든 싱글톤을 가져옵니다.
import com.tukorea.synclab_mobile.utils.userSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// ❌ [삭제] 이 부분은 utils/DataStoreModule.kt에 이미 있으므로 여기서 지워야 합니다.
// private val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {

    // DataStore 키 정의
    private object PreferencesKeys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_UPLOAD = booleanPreferencesKey("auto_upload")
    }

    // ✅ [수정] context.dataStore 대신 context.userSettingsStore를 사용합니다.
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