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
    private var retrofitInstance: Retrofit? = null

    fun init(context: Context) {
        if (authManager == null) {
            authManager = AuthManager(context.applicationContext)
        }
    }

    fun resetClient(){
        retrofitInstance = null
    }

    private fun createAuthInterceptor() = Interceptor { chain ->
        val originalRequest = chain.request()

        if (originalRequest.header("No-Authentication") != null) {
            return@Interceptor chain.proceed(originalRequest)
        }

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

    private fun getRetrofit(): Retrofit {
        return retrofitInstance ?: synchronized(this) {
            val client = OkHttpClient.Builder()
                .addInterceptor(createAuthInterceptor())
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.MINUTES)  // 40분 영상 업로드를 위한 충분한 시간
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val newRetrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            retrofitInstance = newRetrofit
            newRetrofit
        }
    }

    val service: VideoUploadService get() = getRetrofit().create(VideoUploadService::class.java)
    val homeService: HomeService get() = getRetrofit().create(HomeService::class.java)
    val authService: AuthService get() = getRetrofit().create(AuthService::class.java)
}