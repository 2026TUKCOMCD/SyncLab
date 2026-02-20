package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthService {
    @POST("api/mobile/auth/login")
    @Headers("No-Authentication: true")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/mobile/auth/google")
    @Headers("No-Authentication: true")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<LoginResponse>

    @POST("api/mobile/auth/kakao")
    @Headers("No-Authentication: true")
    suspend fun kakaoLogin(@Body request: KakaoLoginRequest): Response<LoginResponse>

    @POST("api/mobile/auth/send-code")
    @Headers("No-Authentication: true")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<SimpleResponse>

    @POST("api/mobile/auth/verify-code")
    @Headers("No-Authentication: true")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<SimpleResponse>

    @POST("api/mobile/auth/signup")
    @Headers("No-Authentication: true")
    suspend fun signup(@Body request: SignupRequest): Response<LoginResponse>
}
