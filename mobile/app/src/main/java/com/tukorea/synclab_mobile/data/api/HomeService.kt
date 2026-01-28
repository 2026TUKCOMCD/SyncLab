package com.tukorea.synclab_mobile.data.api

import com.google.gson.annotations.SerializedName
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import retrofit2.http.*

// --- [데이터 모델 정의] ---

/**
 * 1. 세션 생성/참가 응답
 */
data class SessionResponse(
    val status: String,
    val session: SessionInfo,
    @SerializedName("temp_code") val tempCode: String? = null, // 서버가 temp_code로 줄 경우 대응
    @SerializedName("expires_in") val expiresIn: Int? = null
)

/**
 * 2. 임시 코드 검증 응답
 */
data class VerifyCodeResponse(
    val status: String,
    @SerializedName("session_id") val sessionId: String,
    val message: String? = null
)

/**
 * 3. 홈 데이터 전체 응답 (핵심!)
 * 여기에 SessionInfo와 VideoStatus가 리스트와 맵 형태로 포함됩니다.
 */
data class HomeDataResponse(
    val current_session: SessionInfo?, // 서버의 snake_case와 일치시킴
    val history: List<SessionInfo>,
    val videos: Map<String, List<VideoStatus>>,
    val temp_codes: Map<String, Any>? = null
)
/**
 * 4. 서버 요청용 공통 모델
 */
data class SessionActionRequest(
    val name: String? = null,
    @SerializedName("session_id") val sessionId: String? = null
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

    @GET("api/session/verify-code/{code}")
    suspend fun verifyTempCode(@Path("code") code: String): VerifyCodeResponse

    @GET("api/video/list/{sessionId}")
    suspend fun getSessionVideos(@Path("sessionId") sessionId: String): Map<String, Any>
}