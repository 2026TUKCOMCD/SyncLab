package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import com.tukorea.synclab_mobile.data.repository.HomeRepository
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
                val response = com.tukorea.synclab_mobile.api.NetworkClient.homeService.getHomeData()
                currentSession = response.currentSession
                sessionHistory = response.history ?: emptyList()

                // videos 맵에서 현재 세션 ID에 해당하는 리스트 추출
                currentSession?.sessionId?.let { sid ->
                    recentVideos = response.videos?.get(sid) ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "홈 데이터 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * [수정] userPk를 제거하고 세션 이름(name)을 받도록 변경
     */
    fun createSession(sessionName: String = "새로운 세션") {
        viewModelScope.launch {
            repository.createNewSession(sessionName).onSuccess { response ->
                if (response.status == "success") {
                    currentSession = response.session
                    currentInviteCode = response.tempCode ?: ""
                    expiresIn = response.expiresIn ?: 300
                    recentVideos = emptyList()

                    // 히스토리 갱신을 위해 데이터 다시 로드
                    loadHomeData()
                    Log.d("HomeViewModel", "세션 생성 성공: $currentInviteCode")
                }
            }.onFailure { e ->
                Log.e("HomeViewModel", "세션 생성 실패: ${e.message}")
            }
        }
    }

    /**
     * [수정] 6자리 코드는 verifyConnectCode로, 그 외엔 invite_code로 참가
     */
    fun joinSession(input: String) {
        viewModelScope.launch {
            if (input.length == 6 && input.all { it.isDigit() }) {
                // 1. 6자리 숫자 코드로 세션 ID 조회
                repository.verifyConnectCode(input).onSuccess { verifyResponse ->
                    // 2. 조회된 ID(invite_code)로 실제 참가
                    joinSessionByInviteCode(verifyResponse.sessionId)
                }.onFailure { e ->
                    Log.e("HomeViewModel", "코드 검증 실패: ${e.message}")
                }
            } else {
                // 바로 invite_code로 참가 시도
                joinSessionByInviteCode(input)
            }
        }
    }

    private fun joinSessionByInviteCode(inviteCode: String) {
        viewModelScope.launch {
            repository.joinSession(inviteCode).onSuccess { response ->
                currentSession = response.session
                refreshVideoStatus()
                loadHomeData() // 히스토리 업데이트
            }.onFailure { e ->
                Log.e("HomeViewModel", "세션 참가 실패: ${e.message}")
            }
        }
    }

    fun refreshVideoStatus() {
        val sid = currentSession?.sessionId ?: return
        viewModelScope.launch {
            repository.fetchSessionVideos(sid).onSuccess { response ->
                // Repository에서 반환 타입을 VideoListResponse 등으로 맞췄다면 아래와 같이 사용
                // recentVideos = response.videos ?: emptyList()

                // 만약 Map<String, Any> 형태라면 캐스팅 필요
                val videoList = response["videos"] as? List<*>
                recentVideos = videoList?.filterIsInstance<VideoStatus>() ?: emptyList()
            }.onFailure { e ->
                Log.e("HomeViewModel", "영상 갱신 실패: ${e.message}")
            }
        }
    }

    fun clearSession() {
        currentSession = null
        recentVideos = emptyList()
        currentInviteCode = ""
    }
}