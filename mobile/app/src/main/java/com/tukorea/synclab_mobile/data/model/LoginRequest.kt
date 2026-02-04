package com.tukorea.synclab_mobile.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("id") val userId: String,        // 서버 UserLogin 모델의 'id'와 매핑
    @SerializedName("password") val userPw: String   // 서버 UserLogin 모델의 'password'와 매핑
)

data class LoginResponse(
    val status: String,
    @SerializedName("access_token") val accessToken: String, // ✅ 서버의 access_token과 매핑
    @SerializedName("id") val userId: String,               // 서버의 id와 매핑
    @SerializedName("user_pk") val userPk: Int,             // 서버의 user_pk와 매핑
    @SerializedName("user_name") val userName: String,       // 서버의 user_name과 매핑
    @SerializedName("current_session_id") val currentSessionId: String?,
    @SerializedName("last_joined_at") val lastJoinedAt: Long?
)