package com.tukorea.synclab_mobile.ui.screens.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.data.model.CameraParticipant
import com.tukorea.synclab_mobile.data.model.OverlayData
import com.tukorea.synclab_mobile.data.repository.LiveRepository
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveControllerViewModel : ViewModel() {

    private val repository = LiveRepository()

    // ─── 카메라 상태 ──────────────────────────────────────────────────────────

    var cameras by mutableStateOf<List<CameraParticipant>>(emptyList())
        private set

    var activeCamera by mutableStateOf<String?>(null)
        private set

    var isConnected by mutableStateOf(false)
        private set

    var isLive by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // ─── 오버레이 상태 ────────────────────────────────────────────────────────

    var showScoreboard by mutableStateOf(false)
        private set

    var homeTeam by mutableStateOf("HOME")

    var awayTeam by mutableStateOf("AWAY")

    var homeScore by mutableStateOf(0)
        private set

    var awayScore by mutableStateOf(0)
        private set

    var showLowerThird by mutableStateOf(false)
        private set

    var lowerThirdText by mutableStateOf("")

    var lowerThirdSubText by mutableStateOf("")

    // ─── 내부 ─────────────────────────────────────────────────────────────────

    private var pollingJob: Job? = null
    private var room: Room? = null

    // ─── 폴링: 500ms 간격으로 HTTP 기반 카메라 목록 + activeCamera 동기화 ──────

    fun loadSessionStatus(sessionId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var emptyCount = 0
            val emptyThreshold = 6  // 3초(6 * 500ms) 연속 비어있어야 목록 제거

            while (true) {
                try {
                    val result = repository.getLiveStatus(sessionId)
                    if (result.isSuccess) {
                        val status = result.getOrNull()!!
                        activeCamera = status.activeCamera

                        if (status.cameras.isNotEmpty()) {
                            cameras = status.cameras
                            emptyCount = 0
                        } else {
                            emptyCount++
                            if (emptyCount >= emptyThreshold) {
                                cameras = emptyList()
                            }
                            Log.d("LiveControllerVM", "빈 결과 $emptyCount/$emptyThreshold")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LiveControllerVM", "상태 조회 오류: ${e.message}")
                }
                delay(500)
            }
        }
    }

    fun toggleLive(sessionId: String) {
        viewModelScope.launch {
            val result = if (isLive) repository.endLive(sessionId)
                         else repository.goLive(sessionId)
            if (result.isSuccess) {
                isLive = !isLive
            } else {
                errorMessage = "라이브 상태 변경 실패: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun switchCamera(sessionId: String, cameraId: String) {
        viewModelScope.launch {
            val result = repository.switchCamera(sessionId, cameraId)
            if (result.isSuccess) {
                activeCamera = cameraId
                Log.d("LiveControllerVM", "카메라 전환 완료: $cameraId")
            } else {
                errorMessage = "카메라 전환 실패: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun connectAsController(sessionId: String, context: Context) {
        viewModelScope.launch {
            try {
                val sessionResult = repository.createLiveSession(sessionId)
                val livekitUrl = sessionResult.getOrNull()?.livekitUrl ?: "ws://localhost:7880"

                val tokenResult = repository.getToken(sessionId, "controller")
                if (tokenResult.isFailure) {
                    val msg = tokenResult.exceptionOrNull()?.message ?: ""
                    errorMessage = if (msg == "CONTROLLER_DUPLICATE") {
                        "이미 다른 기기에서 컨트롤러가 연결 중입니다."
                    } else {
                        "토큰 발급 실패: $msg"
                    }
                    return@launch
                }
                val token = tokenResult.getOrNull()!!

                val newRoom = LiveKit.create(appContext = context.applicationContext)
                newRoom.connect(url = livekitUrl, token = token)
                room = newRoom
                isConnected = true
                Log.d("LiveControllerVM", "컨트롤러 연결 완료: $sessionId")

            } catch (e: Exception) {
                Log.e("LiveControllerVM", "연결 실패: ${e.message}")
                errorMessage = "컨트롤러 연결 실패: ${e.message}"
            }
        }
    }

    // ─── 오버레이 제어 ────────────────────────────────────────────────────────

    fun scoreAdd(team: String, delta: Int, sessionId: String) {
        if (team == "home") {
            homeScore = maxOf(0, homeScore + delta)
        } else {
            awayScore = maxOf(0, awayScore + delta)
        }
        postOverlay(sessionId)
    }

    fun applyTeamNames(sessionId: String) {
        postOverlay(sessionId)
    }

    fun toggleScoreboard(sessionId: String) {
        showScoreboard = !showScoreboard
        postOverlay(sessionId)
    }

    fun applyLowerThird(sessionId: String) {
        showLowerThird = lowerThirdText.isNotBlank()
        postOverlay(sessionId)
    }

    fun toggleLowerThird(sessionId: String) {
        showLowerThird = !showLowerThird
        postOverlay(sessionId)
    }

    private fun postOverlay(sessionId: String) {
        viewModelScope.launch {
            val data = OverlayData(
                showScoreboard = showScoreboard,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                homeScore = homeScore,
                awayScore = awayScore,
                showLowerThird = showLowerThird,
                lowerThird = lowerThirdText,
                subTitle = lowerThirdSubText
            )
            repository.updateOverlay(sessionId, data)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        room?.disconnect()
        room = null
    }
}
