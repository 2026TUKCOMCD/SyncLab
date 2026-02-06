package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.data.model.LoginResponse
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import com.tukorea.synclab_mobile.data.repository.HomeRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = HomeRepository()

    // 현재 참여 중인 세션 정보 (가장 중요: 여기에 ID가 있어야 업로드 가능)
    var currentSession by mutableStateOf<SessionInfo?>(null)
    var sessionHistory by mutableStateOf<List<SessionInfo>>(emptyList())
    var recentVideos by mutableStateOf<List<VideoStatus>>(emptyList())

    // 사용자 정보 상태
    var isGuest by mutableStateOf(true)
    var userName by mutableStateOf("게스트")
    var userEmail by mutableStateOf("로그인이 필요합니다")

    var currentInviteCode by mutableStateOf("")
    var expiresIn by mutableIntStateOf(0)

    init {
        // 앱 실행 시 기존 세션 정보를 불러옴
        loadHomeData()
    }

    /**
     * [추가됨] 로그인 직후 서버 응답 데이터를 뷰모델에 강제로 주입하는 함수
     */
    fun updateUserInfo(loginResponse: LoginResponse) {
        this.userName = loginResponse.userName
        this.userEmail = "${loginResponse.userId}@synclab.com"
        this.isGuest = false

        // 서버가 준 currentSessionId가 있다면 즉시 세팅
        if (!loginResponse.currentSessionId.isNullOrEmpty()) {
            this.currentSession = SessionInfo(
                sessionId = loginResponse.currentSessionId,
                sessionName = "진행 중인 세션"
            )
        }
        Log.d("HomeViewModel", "✅ 로그인 정보 업데이트 완료: $userName, 세션: ${loginResponse.currentSessionId}")
    }

    fun loadHomeData() {
        viewModelScope.launch {
            try {
                val response = com.tukorea.synclab_mobile.api.NetworkClient.homeService.getHomeData()

                // 사용자 정보 업데이트
                response.userName?.let {
                    this@HomeViewModel.userName = it
                    this@HomeViewModel.isGuest = false
                }
                response.userId?.let {
                    this@HomeViewModel.userEmail = "$it@synclab.com"
                }

                currentSession = response.currentSession
                sessionHistory = response.history ?: emptyList()

                currentSession?.sessionId?.let { sid ->
                    recentVideos = response.videos?.get(sid) ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "홈 데이터 로드 실패: ${e.message}")
            }
        }
    }

    fun createSession(sessionName: String = "새로운 세션") {
        viewModelScope.launch {
            repository.createNewSession(sessionName).onSuccess { response ->
                if (response.status == "success") {
                    currentSession = response.session
                    currentInviteCode = response.tempCode ?: ""
                    expiresIn = response.expiresIn ?: 300
                    recentVideos = emptyList()
                    loadHomeData()
                }
            }.onFailure { e ->
                Log.e("HomeViewModel", "세션 생성 실패: ${e.message}")
            }
        }
    }

    fun joinSession(input: String) {
        viewModelScope.launch {
            // 6자리 숫자면 단기 코드 검증, 아니면 초대 코드로 바로 가입
            if (input.length == 6 && input.all { it.isDigit() }) {
                repository.verifyConnectCode(input).onSuccess { verifyResponse ->
                    joinSessionByInviteCode(verifyResponse.sessionId)
                }.onFailure { e ->
                    Log.e("HomeViewModel", "코드 검증 실패: ${e.message}")
                }
            } else {
                joinSessionByInviteCode(input)
            }
        }
    }

    private fun joinSessionByInviteCode(inviteCode: String) {
        viewModelScope.launch {
            repository.joinSession(inviteCode).onSuccess { response ->
                currentSession = response.session
                refreshVideoStatus()
                loadHomeData()
            }.onFailure { e ->
                Log.e("HomeViewModel", "세션 참가 실패: ${e.message}")
            }
        }
    }

    fun refreshVideoStatus() {
        val sid = currentSession?.sessionId ?: return
        viewModelScope.launch {
            repository.fetchSessionVideos(sid).onSuccess { response ->
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
        expiresIn = 0
        Log.d("HomeViewModel", "세션 종료: 로그인 상태는 유지됩니다.")
    }

    fun performLogout() {
        isGuest = true
        userName = "게스트"
        userEmail = "로그인이 필요합니다"
        clearSession()
    }
}