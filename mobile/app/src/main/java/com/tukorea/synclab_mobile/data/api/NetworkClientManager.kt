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

    //private const val BASE_URL = "https://overapprehensive-nonasbestine-rodney.ngrok-free.dev/" // 로컬 컴퓨터(FastAPI) 접속 시 에뮬레이터 주소
    private const val BASE_URL = "http://172.28.231.64:3000/"

    // AuthManager 인스턴스 (Context가 필요하므로 초기화 시 주의)
    private lateinit var authManager: AuthManager

    // 앱 시작 시 혹은 필요한 시점에 초기화 호출
    fun init(context: Context) {
        authManager = AuthManager(context)
    }

    // 1. 토큰을 헤더에 붙이는 Interceptor
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = if (::authManager.isInitialized) authManager.getToken() else null

        val newRequest = if (!token.isNullOrEmpty()) {
            // 서버의 Depends(get_current_user_id)가 기대하는 Bearer 포맷
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

    // 2. Client 설정에 authInterceptor 추가
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor) // 로그 먼저 찍고
        .addInterceptor(authInterceptor)    // 토큰 붙이기
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: VideoUploadService by lazy {
        retrofit.create(VideoUploadService::class.java)
    }

    val homeService: HomeService by lazy {
        retrofit.create(HomeService::class.java)
    }
    val authService: AuthService by lazy {
        retrofit.create(AuthService::class.java)
    }
}