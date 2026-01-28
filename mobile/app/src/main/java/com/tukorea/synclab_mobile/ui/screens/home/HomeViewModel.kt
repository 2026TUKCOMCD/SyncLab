package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import com.tukorea.synclab_mobile.data.repository.HomeRepository
import com.tukorea.synclab_mobile.data.api.SessionActionRequest
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = HomeRepository()

    var currentSession by mutableStateOf<SessionInfo?>(null)
    var sessionHistory by mutableStateOf<List<SessionInfo>>(emptyList())
    var recentVideos by mutableStateOf<List<VideoStatus>>(emptyList())

    var isGuest by mutableStateOf(false)
    var currentInviteCode by mutableStateOf("")
    var expiresIn by mutableIntStateOf(0)

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            try {
                // NetworkClient를 직접 호출할 때 발생할 수 있는 Exception 방어
                val response = com.tukorea.synclab_mobile.api.NetworkClient.homeService.getHomeData()
                currentSession = response.current_session
                sessionHistory = response.history ?: emptyList()

                currentSession?.sessionId?.let { sid ->
                    recentVideos = response.videos[sid] ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "홈 데이터 로드 실패: ${e.message}")
            }
        }
    }

    // [중요 수정] 세션 생성 시 에러 핸들링 강화
    fun createSession(name: String) {
        viewModelScope.launch {
            try {
                repository.createNewSession(name).onSuccess { response ->
                    if (response.status == "success") {
                        currentSession = response.session
                        currentInviteCode = response.tempCode ?: ""
                        expiresIn = response.expiresIn ?: 300
                        recentVideos = emptyList()
                        Log.d("HomeViewModel", "세션 생성 성공: $currentInviteCode")
                    }
                }.onFailure { e ->
                    Log.e("HomeViewModel", "세션 생성 실패 (API 에러): ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "세션 생성 도중 예외 발생: ${e.message}")
            }
        }
    }

    // [중요 수정] joinSession에서 "참여 중인 세션 없음" 방지 로직
    fun joinSession(input: String) {
        viewModelScope.launch {
            try {
                if (input.length == 6 && input.all { it.isDigit() }) {
                    // 6자리 코드로 sessionId 조회
                    repository.verifyConnectCode(input).onSuccess { verifyResponse ->
                        joinSessionById(verifyResponse.sessionId)
                    }.onFailure { e ->
                        Log.e("HomeViewModel", "코드 검증 실패 (만료 혹은 잘못된 코드): ${e.message}")
                    }
                } else {
                    joinSessionById(input)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "세션 참가 도중 예외: ${e.message}")
            }
        }
    }

    private fun joinSessionById(sessionId: String) {
        viewModelScope.launch {
            repository.joinSession(sessionId).onSuccess { response ->
                currentSession = response.session
                refreshVideoStatus()
            }.onFailure { e ->
                Log.e("HomeViewModel", "ID 참가 실패: ${e.message}")
            }
        }
    }

    fun refreshVideoStatus() {
        val sid = currentSession?.sessionId ?: return
        viewModelScope.launch {
            repository.fetchSessionVideos(sid).onSuccess { response ->
                val videoList = response["videos"] as? List<*>
                recentVideos = videoList?.filterIsInstance<VideoStatus>() ?: emptyList()
            }.onFailure { e -> Log.e("HomeViewModel", "영상 갱신 실패: ${e.message}") }
        }
    }

    fun clearSession() {
        currentSession = null
        recentVideos = emptyList()
        currentInviteCode = ""
    }
}