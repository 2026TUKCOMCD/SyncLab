package com.tukorea.synclab_mobile.api

import com.tukorea.synclab_mobile.data.api.HomeService
import com.tukorea.synclab_mobile.data.api.VideoUploadService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    //private const val BASE_URL = "https://overapprehensive-nonasbestine-rodney.ngrok-free.dev/" // 로컬 컴퓨터(FastAPI) 접속 시 에뮬레이터 주소
    private const val BASE_URL = "https://webhook.site/3404ec0a-088c-4581-9026-26a73a5b4dd3/"

    private val interceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.MINUTES) // 업로드 대기 시간
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client) // 위에서 만든 client 적용
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 기존 영상 업로드 서비스 (이름을 service에서 videoService로 변경하거나 유지)
    val service: VideoUploadService by lazy {
        retrofit.create(VideoUploadService::class.java)
    }

    // 새로 추가한 세션 서비스
    val homeService: HomeService by lazy {
        retrofit.create(HomeService::class.java)
    }
}