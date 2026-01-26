package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.CreateSessionRequest
import com.tukorea.synclab_mobile.data.api.SessionActionRequest
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    // 현재 세션 정보
    var currentSession by mutableStateOf<SessionInfo?>(null)

    // 과거 세션 기록 리스트
    var sessionHistory by mutableStateOf<List<SessionInfo>>(emptyList())

    // 최근 영상 처리 리스트 (Status 갱신의 핵심)
    var recentVideos by mutableStateOf<List<VideoStatus>>(emptyList())

    init {
        // 앱 시작 시 초기 데이터 로드 (홈 데이터 통합 로드)
        loadHomeData()
    }

    /**
     * 서버에서 홈 화면에 필요한 초기 데이터(세션, 히스토리, 영상목록)를 가져옵니다.
     */
    fun loadHomeData() {
        viewModelScope.launch {
            try {
                val data = NetworkClient.homeService.getHomeData()
                currentSession = data.current_session
                sessionHistory = data.history
                recentVideos = data.videos
            } catch (e: Exception) {
                Log.e("HomeViewModel", "홈 데이터 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 🔴 HomeScreen의 LaunchedEffect에서 호출할 함수
     * 서버에서 최신 영상 처리 상태만 가져와 업데이트합니다.
     */
    fun refreshVideoStatus() {
        viewModelScope.launch {
            try {
                val updatedVideos = NetworkClient.homeService.getVideoStatus()
                recentVideos = updatedVideos // UI 자동 갱신
                Log.d("HomeViewModel", "영상 처리 상태 업데이트 완료")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "영상 상태 갱신 실패: ${e.message}")
            }
        }
    }

    /**
     * 새로운 세션을 생성합니다.
     */
    fun createSession(name: String) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.homeService.createSession(SessionActionRequest(name = name))
                if (response.status == "success") {
                    currentSession = response.session
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "세션 생성 실패: ${e.message}")
            }
        }
    }

    /**
     * 입력받은 코드로 기존 세션에 참가합니다.
     */
    fun joinSession(code: String) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.homeService.joinSession(SessionActionRequest(sessionId = code))
                if (response.status == "success") {
                    currentSession = response.session
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "세션 참가 실패: ${e.message}")
            }
        }
    }

    /**
     * 현재 참여 중인 세션을 종료(퇴장)합니다.
     */
    fun clearSession() {
        currentSession = null
        // 필요시 서버에 퇴장 알림 API 호출 로직 추가 가능
    }
}