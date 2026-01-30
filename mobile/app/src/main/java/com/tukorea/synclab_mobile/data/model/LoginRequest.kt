package com.tukorea.synclab_mobile.data.model

data class LoginRequest(
    val userId: String,
    val userPw: String
)

data class LoginResponse(
    val status: String,
    val userId: String,
    val userPk: Int,
    val userName: String,
    val currentSessionId: String?, // 현재 참여 중인 세션 ID
    val lastJoinedAt: Long?       // 마지막 참여 시간
)