package com.tukorea.synclab_mobile.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("id") val userId: String,
    @SerializedName("password") val userPw: String
)

data class LoginResponse(
    val status: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("id") val userId: String,
    @SerializedName("user_pk") val userPk: Int,
    @SerializedName("user_name") val userName: String,
    @SerializedName("profile_image") val profileImage: String?,
    val email: String?,
    @SerializedName("current_session_id") val currentSessionId: String?,
    @SerializedName("last_joined_at") val lastJoinedAt: Long?
)

data class GoogleLoginRequest(
    @SerializedName("id_token") val idToken: String
)

data class KakaoLoginRequest(
    @SerializedName("access_token") val accessToken: String
)

// 이메일 인증 회원가입
data class SendCodeRequest(val email: String)

data class VerifyCodeRequest(val email: String, val code: String)

data class SignupRequest(
    val email: String,
    val password: String,
    @SerializedName("user_name") val userName: String
)

data class SimpleResponse(
    val status: String,
    val message: String
)