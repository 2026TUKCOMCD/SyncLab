package com.tukorea.synclab_mobile.ui.screens.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.data.repository.LiveRepository
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoPreset169
import livekit.org.webrtc.EglBase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, STREAMING, DISCONNECTED, ERROR }

class LiveCameraViewModel : ViewModel() {

    private val repository = LiveRepository()

    var connectionState by mutableStateOf(ConnectionState.IDLE)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var localVideoTrack by mutableStateOf<LocalVideoTrack?>(null)
        private set

    // LiveKit + SurfaceViewRenderer가 공유하는 EglBase (OES 텍스처 공유 필수)
    val eglBase: EglBase = EglBase.create()

    // connectionState에서 파생
    val isStreaming get() = connectionState == ConnectionState.STREAMING

    private var room: Room? = null
    private var roomMonitorJob: Job? = null

    // 항상 1280×720으로 캡처 — 방향 회전은 LiveKit SDK가 rotation 메타데이터로 자동 처리
    private val captureParams = VideoPreset169.H720.capture

    // ─── 화면 진입 시 호출: 카메라 비활성 상태로 룸에만 참가 ──────────────────
    fun joinRoom(sessionId: String, context: Context) {
        if (connectionState == ConnectionState.CONNECTING ||
            connectionState == ConnectionState.CONNECTED ||
            connectionState == ConnectionState.STREAMING) return

        viewModelScope.launch {
            connectionState = ConnectionState.CONNECTING
            errorMessage = null

            val sessionResult = repository.createLiveSession(sessionId)
            if (sessionResult.isFailure) {
                errorMessage = "세션 생성 실패: ${sessionResult.exceptionOrNull()?.message}"
                connectionState = ConnectionState.ERROR
                return@launch
            }
            val livekitUrl = sessionResult.getOrNull()!!.livekitUrl

            val tokenResult = repository.getToken(sessionId, "camera")
            if (tokenResult.isFailure) {
                errorMessage = "토큰 발급 실패: ${tokenResult.exceptionOrNull()?.message}"
                connectionState = ConnectionState.ERROR
                return@launch
            }
            val token = tokenResult.getOrNull()!!

            try {
                @Suppress("OPT_IN_USAGE")
                val newRoom = LiveKit.create(
                    appContext = context.applicationContext,
                    overrides = LiveKitOverrides(eglBase = eglBase)
                )
                
                // RoomOptions 대신 개별 프로퍼티 설정하여 'Unresolved reference RoomOptions' 해결
                newRoom.videoTrackCaptureDefaults = LocalVideoTrackOptions(
                    position = CameraPosition.BACK,
                    captureParams = captureParams
                )

                newRoom.connect(url = livekitUrl, token = token)
                room = newRoom
                connectionState = ConnectionState.CONNECTED
                Log.d("LiveCameraVM", "룸 연결 완료 (카메라 비활성): $sessionId")
                startRoomMonitor()
            } catch (e: Exception) {
                Log.e("LiveCameraVM", "룸 연결 실패: ${e.message}")
                errorMessage = "연결 실패: ${e.message}"
                connectionState = ConnectionState.ERROR
            }
        }
    }

    // ─── 룸 연결 상태 모니터: 서버 재시작/네트워크 끊김 감지 ────────────────
    private fun startRoomMonitor() {
        roomMonitorJob?.cancel()
        roomMonitorJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                val r = room ?: break
                val roomState = r.state
                // Room.State.DISCONNECTED = 연결 끊김
                if (roomState == Room.State.DISCONNECTED) {
                    Log.w("LiveCameraVM", "LiveKit 룸 연결 끊김 감지 → ERROR 상태로 전환")
                    localVideoTrack = null
                    connectionState = ConnectionState.ERROR
                    errorMessage = "서버 연결이 끊겼습니다. 재연결 버튼을 눌러주세요."
                    room = null
                    break
                }
            }
        }
    }

    // ─── "스트리밍 시작" 버튼: 후방 카메라 활성화 ────────────────────────────
    fun startCamera(context: Context) {
        val currentRoom = room ?: return
        viewModelScope.launch {
            try {
                currentRoom.localParticipant.setCameraEnabled(true)
                val cameraTrack = currentRoom.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                currentRoom.localParticipant.setMicrophoneEnabled(false)
                localVideoTrack = cameraTrack
                connectionState = ConnectionState.STREAMING
                Log.d("LiveCameraVM", "카메라 시작 (후방) track=$localVideoTrack")
            } catch (e: Exception) {
                Log.e("LiveCameraVM", "카메라 시작 실패: ${e.message}")
                errorMessage = "카메라 시작 실패: ${e.message}"
            }
        }
    }

    // ─── "스트리밍 종료" 버튼: 카메라만 비활성화, 룸은 유지 ──────────────────
    fun stopCamera() {
        val currentRoom = room ?: return
        viewModelScope.launch {
            try {
                currentRoom.localParticipant.setCameraEnabled(false)
                localVideoTrack = null
                connectionState = ConnectionState.CONNECTED
                Log.d("LiveCameraVM", "카메라 중지 (룸 유지)")
            } catch (e: Exception) {
                Log.e("LiveCameraVM", "카메라 중지 실패: ${e.message}")
            }
        }
    }

    // ─── 화면 이탈 시 호출: 룸 연결 해제 ─────────────────────────────────────
    fun disconnect() {
        roomMonitorJob?.cancel()
        viewModelScope.launch {
            try {
                room?.localParticipant?.setCameraEnabled(false)
                room?.disconnect()
            } catch (e: Exception) {
                Log.e("LiveCameraVM", "연결 해제 오류: ${e.message}")
            } finally {
                room = null
                localVideoTrack = null
                connectionState = ConnectionState.DISCONNECTED
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomMonitorJob?.cancel()
        room?.disconnect()
        room = null
        eglBase.release()
    }
}
