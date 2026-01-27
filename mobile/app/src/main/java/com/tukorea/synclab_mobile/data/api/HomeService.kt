package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
    val videos: Map<String, List<VideoStatus>>
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
    @GET("api/home/data")
    suspend fun getHomeData(): HomeDataResponse

    @GET("api/video/status")
    suspend fun getVideoStatus(): List<VideoStatus>

    @POST("api/session/create")
    suspend fun createSession(@Body request: SessionActionRequest): SessionResponse

    @POST("api/session/join")
    suspend fun joinSession(@Body request: SessionActionRequest): SessionResponse

    // ✅ 특정 세션의 영상 목록을 가져오는 API 추가 (서버의 /api/video/list/{sessionId} 대응)
    @GET("api/video/list/{sessionId}")
    suspend fun getSessionVideos(@Path("sessionId") sessionId: String): Map<String, Any>
}