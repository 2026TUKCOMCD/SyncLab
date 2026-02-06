package com.tukorea.synclab_mobile.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.utils.userSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// UI 상태 데이터 모델
data class SettingsUiState(
    val isGuest: Boolean = true,
    val isWifiOnly: Boolean = true,
    val isAutoUpload: Boolean = false,
    val cacheSize: String = "1.2GB",
    val userName: String = "게스트",
    val userEmail: String = "로그인이 필요합니다",
    // [수정] 기본 이미지를 UI Avatars(2번 옵션) 경로로 변경
    val profileImageUrl: Any = "https://ui-avatars.com/api/?name=Guest&background=EBF4FF&color=7F9CF5&bold=true"
)

class SettingsViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
    private val AUTO_UPLOAD_KEY = booleanPreferencesKey("auto_upload")

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // [정상 작동] utils에서 가져온 userSettingsStore 사용
            context.userSettingsStore.data.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    isWifiOnly = prefs[WIFI_ONLY_KEY] ?: true,
                    isAutoUpload = prefs[AUTO_UPLOAD_KEY] ?: false
                )
            }
        }
    }

    // [수정] SettingsScreen의 호출부(name, email)와 파라미터 이름을 일치시켰습니다.
    fun syncUserInfo(
        isGuestUser: Boolean,
        userNameFromDb: String,
        userEmailFromDb: String,
        logoResourceId: Int
    ) {
        _uiState.value = _uiState.value.copy(
            isGuest = isGuestUser,
            userName = if (isGuestUser) "게스트" else userNameFromDb,
            userEmail = if (isGuestUser) "로그인이 필요합니다" else userEmailFromDb,
            profileImageUrl = if (isGuestUser) {
                "https://ui-avatars.com/api/?name=Guest&background=EBF4FF&color=7F9CF5&bold=true"
            } else {
                logoResourceId
            }
        )
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
                _uiState.value = _uiState.value.copy(cacheSize = "0.0MB")
            } catch (e: Exception) {
                Log.e("Settings", "캐시 삭제 실패")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState()
            onComplete()
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            context.userSettingsStore.edit { it[WIFI_ONLY_KEY] = enabled }
        }
    }

    fun toggleAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            context.userSettingsStore.edit { it[AUTO_UPLOAD_KEY] = enabled }
        }
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}