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
    @SerializedName("current_session_id") val currentSessionId: String?,
    @SerializedName("last_joined_at") val lastJoinedAt: Long?
)