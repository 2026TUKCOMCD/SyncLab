package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.CreateSessionRequest
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    var currentSession by mutableStateOf<SessionInfo?>(null)
    var sessionHistory = mutableStateListOf<SessionInfo>()
    var recentVideos = mutableStateListOf<VideoStatus>()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refreshVideoStatus()
                delay(5000) // 5초마다 서버에 확인
            }
        }
    }

    private fun refreshVideoStatus() {
        // TODO: 실제 서버 API 호출 (GET /api/video/status)
        // 현재는 리스트 중 PROCESSING인 것들의 상태를 업데이트하는 시뮬레이션
        println("서버에서 영상 처리 상태를 확인 중...")
    }
    // HomeViewModel.kt
    fun createSession(name: String) {
        viewModelScope.launch {
            try {
                // 1. 서버에 세션 생성 요청을 보냄
                val response = NetworkClient.homeService.createSession(CreateSessionRequest(name))

                // 2. 서버가 응답한 8자리 코드가 포함된 세션 정보를 화면에 반영
                if (response.status == "success") {
                    currentSession = response.session
                }
            } catch (e: Exception) {
                // 서버 연결 실패 시 에러 처리
                Log.e("HomeViewModel", "세션 생성 실패: ${e.message}")
            }
        }
    }

    fun joinSession(code: String) {
        viewModelScope.launch {
            // 서버에 code를 보내서 참가 요청하는 로직 작성
            currentSession = SessionInfo(code, "참가한 세션", "방금 전", 2)
        }
    }
    // HomeViewModel.kt 내부
    fun clearSession() {
        currentSession = null
        // 필요하다면 서버에도 '세션 나감' 요청을 보낼 수 있습니다.
    }
}