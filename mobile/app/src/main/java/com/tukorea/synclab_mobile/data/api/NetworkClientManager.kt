package com.tukorea.synclab_mobile.api

import android.content.Context
import com.tukorea.synclab_mobile.data.api.AuthService
import com.tukorea.synclab_mobile.data.api.HomeService
import com.tukorea.synclab_mobile.data.api.VideoUploadService
import com.tukorea.synclab_mobile.utils.AuthManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val BASE_URL = "http://172.28.231.64:3000/"

    private var authManager: AuthManager? = null

    // 앱의 Application 클래스나 MainActivity에서 호출해주세요.
    fun init(context: Context) {
        if (authManager == null) {
            authManager = AuthManager(context.applicationContext)
        }
    }

    // [중요] 토큰 인셉터 복구
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        // authManager가 null이면 토큰 없이 보내고, 있으면 토큰을 가져옴
        val token = authManager?.getToken()

        val newRequest = if (!token.isNullOrEmpty()) {
            // Bearer 토큰 인증 헤더 복구
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Client 설정
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)    // 1. 토큰 먼저 붙이고
            .addInterceptor(loggingInterceptor) // 2. 로그 찍기 (최종 헤더 확인용)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.MINUTES) // 대용량 업로드를 위한 긴 시간 유지
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 서비스 인터페이스들
    val service: VideoUploadService by lazy { retrofit.create(VideoUploadService::class.java) }
    val homeService: HomeService by lazy { retrofit.create(HomeService::class.java) }
    val authService: AuthService by lazy { retrofit.create(AuthService::class.java) }
}