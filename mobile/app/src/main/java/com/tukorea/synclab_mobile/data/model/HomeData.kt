package com.tukorea.synclab_mobile.data.model

// 세션 정보
data class SessionInfo(
    val sessionId: String,
    val sessionName: String,
    val createdAt: String,
    val participantCount: Int,
    val connectCode: String? = null, // inviteCode 대신 connectCode로 이름 통일
    val expiresAt: Long? = null
)

// 영상 처리 상태 정보
data class VideoStatus(
    val videoId: String,
    val fileName: String,
    val status: String, // "PENDING", "PROCESSING", "COMPLETED"
    val timestamp: Long
)