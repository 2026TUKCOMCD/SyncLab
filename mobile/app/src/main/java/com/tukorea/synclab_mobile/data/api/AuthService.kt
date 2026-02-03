package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.LoginRequest
import com.tukorea.synclab_mobile.data.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("api/mobile/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}