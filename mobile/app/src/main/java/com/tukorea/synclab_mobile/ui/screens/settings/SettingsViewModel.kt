package com.tukorea.synclab_mobile.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. UI 상태 데이터 클래스
data class SettingsUiState(
    val isGuest: Boolean = false,
    val isWifiOnly: Boolean = true,
    val isAutoUpload: Boolean = false,
    val uploadHistorySummary: String = "성공 0 / 실패 0",
    val cacheSize: String = "1.2GB",
    val userName: String = "",
    val userEmail: String = "",
    val profileImageUrl: String = ""
)

// 2. ViewModel 클래스 (Repository 주입 방식)
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()      // Repository의 데이터를 관찰 (실시간 반영)
        checkUserLoginStatus() // 로그인 상태 체크
    }

    // [로직 1] Repository의 Flow를 UI 상태에 바인딩 (실시간 데이터 동기화)
    private fun observeSettings() {
        viewModelScope.launch {
            repository.isWifiOnlyFlow.collect { isWifi ->
                _uiState.value = _uiState.value.copy(isWifiOnly = isWifi)
            }
        }
        viewModelScope.launch {
            repository.isAutoUploadFlow.collect { isAuto ->
                _uiState.value = _uiState.value.copy(isAutoUpload = isAuto)
            }
        }
    }

    // [로직 2] 로그인 상태 및 프로필 정보 로드
    private fun checkUserLoginStatus() {
        viewModelScope.launch {
            // TODO: 실제 앱에서는 AuthRepository 등을 통해 확인
            val isUserLoggedIn = false

            if (!isUserLoggedIn) {
                _uiState.value = _uiState.value.copy(
                    isGuest = true,
                    userName = "게스트",
                    userEmail = "로그인이 필요합니다",
                    profileImageUrl = "https://vinsign.app/resources/avatars/avatar-guest.png",
                    uploadHistorySummary = "로그인 후 확인 가능"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isGuest = false,
                    userName = "김철수",
                    userEmail = "@chulsoo_kim",
                    profileImageUrl = "https://vinsign.app/resources/avatars/avatar-2.png",
                    uploadHistorySummary = "성공 24 / 실패 1"
                )
            }
        }
    }

    // [로직 3] Wi-Fi 설정 업데이트 (Repository에 위임)
    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateWifiOnly(enabled)
        }
    }

    // [로직 4] 자동 업로드 설정 업데이트 (Repository에 위임)
    fun toggleAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoUpload(enabled)
        }
    }

    // [로직 5] 임시 파일 및 캐시 삭제
    fun clearCache() {
        viewModelScope.launch {
            // TODO: 실제 파일 삭제 로직 수행 (예: context.cacheDir.deleteRecursively())
            _uiState.value = _uiState.value.copy(cacheSize = "0.0MB")
        }
    }

    // [로직 6] 로그아웃
    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            // TODO: 세션 삭제 로직
            onSuccess()
        }
    }
}

// 3. Factory 클래스 (Repository 생성 및 주입 담당)
class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val repository = SettingsRepository(context.applicationContext)
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}