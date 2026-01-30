package com.tukorea.synclab_mobile.data.model

import com.google.gson.annotations.SerializedName

// --- 1. 공통 정보 모델 (여기 같이 있어야 에러가 안 납니다) ---

data class SessionInfo(
    val sessionId: String,
    val sessionName: String,
    val createdAt: String,
    val participantCount: Int,
    val connectCode: String? = null,
    val expiresAt: Long? = null
)

data class VideoStatus(
    val videoId: String,
    val fileName: String,
    val status: String, // "PENDING", "PROCESSING", "COMPLETED"
    val timestamp: Long
)

// --- 2. 응답/요청 DTO 모델 ---

data class SessionResponse(
    val status: String,
    val session: SessionInfo,
    @SerializedName("temp_code") val tempCode: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null
)

data class VerifyCodeResponse(
    val status: String,
    @SerializedName("session_id") val sessionId: String,
    val message: String? = null
)

data class HomeDataResponse(
    @SerializedName("current_session") val currentSession: SessionInfo?,
    val history: List<SessionInfo>,
    val videos: Map<String, List<VideoStatus>>,
    @SerializedName("temp_codes") val tempCodes: Map<String, Any>? = null
)

data class SessionCreateRequest(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("user_pk") val userPk: Int? = null
)
data class SessionJoinRequest(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("user_pk") val userPk: Int? = null
)

