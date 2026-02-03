package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.* // 모델들을 불러옵니다.
import retrofit2.http.*

interface HomeService {

    @GET("api/mobile/home/data")
    suspend fun getHomeData(): HomeDataResponse

    @GET("api/mobile/video/status")
    suspend fun getVideoStatus(): List<VideoStatus>

    @POST("api/mobile/session/create")
    suspend fun createSession(@Body request: SessionCreateRequest): SessionResponse

    @POST("api/mobile/session/join")
    suspend fun joinSession(@Body request: SessionJoinRequest): SessionResponse

    @GET("api/mobile/session/verify-code/{code}")
    suspend fun verifyTempCode(@Path("code") code: String): VerifyCodeResponse

    @GET("api/video/list/{sessionId}")
    suspend fun getSessionVideos(@Path("sessionId") sessionId: String): Map<String, Any>
}