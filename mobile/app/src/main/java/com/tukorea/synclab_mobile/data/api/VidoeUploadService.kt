package com.tukorea.synclab_mobile.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * SyncLab 서버 통신 인터페이스
 */
interface VideoUploadService {
    @Multipart
    @POST("/upload/video")
    suspend fun uploadVideoData(
        @Part video: MultipartBody.Part,      // .mp4 파일
        @Part metadata: MultipartBody.Part,   // .json 파일
        @Part("videoId") videoId: RequestBody // 분석용 식별자
    ): Response<UploadResponse>
}

/**
 * 서버 응답 데이터 모델
 */
data class UploadResponse(
    val success: Boolean,
    val message: String,
    val videoUrl: String?,
    val metadataUrl: String?
)