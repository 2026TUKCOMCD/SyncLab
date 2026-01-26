package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.SessionInfo
import retrofit2.http.Body
import retrofit2.http.POST

// 서버 응답 규격
data class SessionResponse(
    val status: String,
    val session: SessionInfo
)

// 서버에 보낼 요청 데이터
data class CreateSessionRequest(val name: String)
data class JoinSessionRequest(val sessionId: String)

interface HomeService {
    @POST("api/session/create")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionResponse

    @POST("api/session/join")
    suspend fun joinSession(@Body request: JoinSessionRequest): SessionResponse
}