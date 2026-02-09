package com.tukorea.synclab_mobile.api

import android.content.Context
import com.tukorea.synclab_mobile.BuildConfig
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

    private val BASE_URL = BuildConfig.BASE_URL

    private var authManager: AuthManager? = null

    fun init(context: Context) {
        if (authManager == null) {
            authManager = AuthManager(context.applicationContext)
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        val token = authManager?.getToken()

        val newRequest = if (!token.isNullOrEmpty()) {
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
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.MINUTES)
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

    // 서비스 인터페이스
    val service: VideoUploadService by lazy { retrofit.create(VideoUploadService::class.java) }
    val homeService: HomeService by lazy { retrofit.create(HomeService::class.java) }
    val authService: AuthService by lazy { retrofit.create(AuthService::class.java) }
}