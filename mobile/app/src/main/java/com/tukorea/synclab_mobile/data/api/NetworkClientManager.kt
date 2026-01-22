package com.tukorea.synclab_mobile.api

import com.tukorea.synclab_mobile.data.api.VidoeUploadService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val BASE_URL = "https://overapprehensive-nonasbestine-rodney.ngrok-free.dev/" // 로컬 컴퓨터(FastAPI) 접속 시 에뮬레이터 주소

    private val interceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 전송 데이터 전체를 로그로 출력
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.MINUTES) // 대용량 영상 업로드를 위해 길게 설정
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ... 생략
    val service: VidoeUploadService by lazy { // 타입을 VidoeUploadService로 변경
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(VidoeUploadService::class.java) // 여기도 변경
    }
}