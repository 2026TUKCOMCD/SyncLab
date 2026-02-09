package com.tukorea.synclab_mobile.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.utils.userSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val isGuest: Boolean = true,
    val isWifiOnly: Boolean = true,
    val isAutoUpload: Boolean = false,
    val cacheSize: String = "0.0MB",
    val userName: String = "게스트",
    val userEmail: String = "로그인이 필요합니다",
    val profileImageUrl: Any = "https://ui-avatars.com/api/?name=Guest&background=EBF4FF&color=7F9CF5&bold=true"
)

class SettingsViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
    private val AUTO_UPLOAD_KEY = booleanPreferencesKey("auto_upload")

    init {
        loadSettings()
        updateCacheSize()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            context.userSettingsStore.data.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    isWifiOnly = prefs[WIFI_ONLY_KEY] ?: true,
                    isAutoUpload = prefs[AUTO_UPLOAD_KEY] ?: false
                )
            }
        }
    }

    private fun updateCacheSize() {
        viewModelScope.launch {
            val totalSize = withContext(Dispatchers.IO) {
                calculateFolderSize(context.cacheDir) +
                        (context.externalCacheDir?.let { calculateFolderSize(it) } ?: 0L)
            }
            _uiState.value = _uiState.value.copy(cacheSize = formatSize(totalSize))
        }
    }

    private fun calculateFolderSize(file: File): Long {
        var length: Long = 0
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                length += if (f.isDirectory) calculateFolderSize(f) else f.length()
            }
        }
        return length
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0.0MB"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f%s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

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
                withContext(Dispatchers.IO) {
                    context.cacheDir.deleteRecursively()
                    context.externalCacheDir?.deleteRecursively()
                    // 삭제 후 폴더 구조 재생성 (일부 API 에러 방지)
                    context.cacheDir.mkdirs()
                    context.externalCacheDir?.mkdirs()
                }
                updateCacheSize() // 삭제 후 UI 갱신 (0.0MB)
                Log.d("Settings", "캐시 삭제 완료")
            } catch (e: Exception) {
                Log.e("Settings", "캐시 삭제 실패", e)
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