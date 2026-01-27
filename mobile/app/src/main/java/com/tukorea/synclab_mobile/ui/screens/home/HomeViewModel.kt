package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.SessionActionRequest
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    // 현재 활성화된 세션 정보를 관찰 가능한 상태로 유지
    var currentSession by mutableStateOf<SessionInfo?>(null)
    var sessionHistory by mutableStateOf<List<SessionInfo>>(emptyList())
    var recentVideos by mutableStateOf<List<VideoStatus>>(emptyList())

    init {
        loadHomeData()
    }

    /**
     * 홈 데이터 로드: 서버에서 현재 세션과 히스토리를 가져옴
     */
    fun loadHomeData() {
        viewModelScope.launch {
            try {
                val data = NetworkClient.homeService.getHomeData()
                currentSession = data.current_session
                sessionHistory = data.history

                // 현재 세션 ID가 있다면 해당 세션에 속한 비디오만 필터링
                val sid = currentSession?.sessionId
                if (sid != null) {
                    recentVideos = data.videos[sid] ?: emptyList()
                    Log.d("HomeViewModel", "현재 세션($sid) 영상 로드 성공: ${recentVideos.size}개")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "홈 데이터 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 영상 상태 갱신: 특정 세션의 영상 리스트를 서버에서 다시 가져옴
     */
    fun refreshVideoStatus() {
        viewModelScope.launch {
            val sid = currentSession?.sessionId
            try {
                if (sid != null) {
                    val response = NetworkClient.homeService.getSessionVideos(sid)
                    // 서버 응답에서 "videos" 리스트 추출 (안전한 캐스팅)
                    val videoList = response["videos"] as? List<*>
                    recentVideos = videoList?.filterIsInstance<VideoStatus>() ?: emptyList()
                    Log.d("HomeViewModel", "세션($sid) 상태 갱신 완료")
                } else {
                    // 세션이 없는 경우 일반 상태 조회
                    recentVideos = NetworkClient.homeService.getVideoStatus()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "영상 상태 갱신 실패: ${e.message}")
            }
        }
    }

    /**
     * 세션 생성: 새로운 세션을 만들고 ID를 저장
     */
    fun createSession(name: String) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.homeService.createSession(SessionActionRequest(name = name))
                if (response.status == "success") {
                    currentSession = response.session
                    recentVideos = emptyList()
                    Log.d("HomeViewModel", "새 세션 생성 성공: ${currentSession?.sessionId}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "세션 생성 실패: ${e.message}")
            }
        }
    }

    /**
     * 세션 참가: 코드를 입력해 기존 세션에 들어감
     */
    fun joinSession(code: String) {
        viewModelScope.launch {
            try {
                val response = NetworkClient.homeService.joinSession(SessionActionRequest(sessionId = code))
                if (response.status == "success") {
                    currentSession = response.session
                    Log.d("HomeViewModel", "세션 참가 성공: ${currentSession?.sessionId}")
                    refreshVideoStatus() // 참가한 세션의 영상 목록 불러오기
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "세션 참가 실패: ${e.message}")
            }
        }
    }

    fun clearSession() {
        currentSession = null
        recentVideos = emptyList()
        Log.d("HomeViewModel", "세션 종료 및 데이터 초기화")
    }
}