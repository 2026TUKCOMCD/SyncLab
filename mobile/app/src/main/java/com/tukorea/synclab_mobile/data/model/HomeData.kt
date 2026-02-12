package com.tukorea.synclab_mobile.data.model

import com.google.gson.annotations.SerializedName

data class SessionInfo(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("session_name") val sessionName: String,
    @SerializedName("created_at") val createdAt: String ="",
    @SerializedName("participant_count") val participantCount: Int =0,
    @SerializedName("connect_code") val connectCode: String? = null,
    @SerializedName("expires_at") val expiresAt: Long? = null
)

data class VideoStatus(
    @SerializedName("video_id") val videoId: String,
    @SerializedName("file_name") val fileName: String,
    val status: String, // "PENDING", "PROCESSING", "COMPLETED"
    val timestamp: Long
)


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
    val videos: Map<String, List<VideoStatus>>? = null,

    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("user_id") val userId: String? = null,

    @SerializedName("temp_codes") val tempCodes: Map<String, Any>? = null
)

data class SessionCreateRequest(
    @SerializedName("name") val sessionName: String? = null, // 서버 SessionActionRequest 필드명 반영

)

data class SessionJoinRequest(
    @SerializedName("invite_code") val inviteCode: String, // 세션 참가는 invite_code를 사용
)

data class SessionJoinResponse(
    val status: String,
    @SerializedName("access_token") val accessToken: String?, 
    @SerializedName("session_id") val sessionId: String?,
    val session: SessionInfo
)