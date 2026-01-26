package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// --- [데이터 모델 정의] ---

// 1. 서버 응답 규격 (기존 유지)
data class SessionResponse(
    val status: String,
    val session: SessionInfo
)

// 2. 홈 전체 데이터를 담는 응답 모델 (새로 추가)
data class HomeDataResponse(
    val current_session: SessionInfo?,
    val history: List<SessionInfo>,
    val videos: List<VideoStatus>
)

// 3. 서버에 보낼 요청 데이터 (기존 유지)
data class CreateSessionRequest(val name: String)
data class JoinSessionRequest(val sessionId: String)

// 4. ViewModel에서 공통으로 사용 중인 요청 모델 (새로 추가)
data class SessionActionRequest(
    val name: String? = null,
    val sessionId: String? = null
)

// --- [서비스 인터페이스] ---

interface HomeService {
    // 최근 영상 목록 및 히스토리 조회를 위해 추가
    @GET("api/home/data")
    suspend fun getHomeData(): HomeDataResponse

    // 영상 처리 상태 폴링을 위해 추가
    @GET("api/video/status")
    suspend fun getVideoStatus(): List<VideoStatus>

    // 기존 함수 유지 (매개변수 타입을 SessionActionRequest로 변경하여 ViewModel과 호환성 확보)
    @POST("api/session/create")
    suspend fun createSession(@Body request: SessionActionRequest): SessionResponse

    @POST("api/session/join")
    suspend fun joinSession(@Body request: SessionActionRequest): SessionResponse
}