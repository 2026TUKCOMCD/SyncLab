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

    var currentSession by mutableStateOf<SessionInfo?>(null)
    var sessionHistory by mutableStateOf<List<SessionInfo>>(emptyList())
    var recentVideos by mutableStateOf<List<VideoStatus>>(emptyList())

    var isGuest by mutableStateOf(true)
    var userName by mutableStateOf("게스트")
    var userEmail by mutableStateOf("로그인이 필요합니다")

    var currentInviteCode by mutableStateOf("")
    var expiresIn by mutableIntStateOf(0)

    var errorMessage by mutableStateOf<String?>(null)

    init {

    }

    fun updateUserInfo(loginResponse: LoginResponse) {
        this.userName = loginResponse.userName
        this.userEmail = "${loginResponse.userId}@synclab.com"
        this.isGuest = false

        if (!loginResponse.currentSessionId.isNullOrEmpty()) {
            this.currentSession = SessionInfo(
                sessionId = loginResponse.currentSessionId,
                sessionName = "진행 중인 세션"
            )
        }
        loadHomeData()
        Log.d("HomeViewModel", "✅ 로그인 정보 업데이트 완료: $userName, 세션: ${loginResponse.currentSessionId}")
    }

    fun loadHomeData() {
        viewModelScope.launch {
            try {
                val response = com.tukorea.synclab_mobile.api.NetworkClient.homeService.getHomeData()

                response.userName?.let {
                    this@HomeViewModel.userName = it
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
            if (input.length == 8 && input.all { it.isDigit() }) {
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

                if(response.status == "success" && response.session != null) {
                    currentSession = response.session

                    if (isGuest) {
                        refreshVideoStatus()
                    } else {
                        loadHomeData()
                    }
                }else{
                    errorMessage = "초대 코드가 올바르지 않습니다."
                    Log.e("HomeViewModel", "세션 참가 실패: ${response.status}")
                }
            }.onFailure { e ->
                errorMessage = "네트워크 오류가 발생했습니다."
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
        sessionHistory = emptyList()
        clearSession()
    }
    fun prepareGuestMode() {
        isGuest = true
        currentSession = null
        recentVideos = emptyList()
        sessionHistory = emptyList()
        userName = "게스트"
        userEmail = "로그인이 필요합니다"
    }
}