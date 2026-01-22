package com.tukorea.synclab_mobile.data.api // 이미지 경로 기준

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface VidoeUploadService {
    // [추가] 노트북(Python) 서버에서 S3 URL 가져오기
    @GET("get-url")
    suspend fun getPresignedUrl(
        @Query("filename") filename: String
    ): Map<String, String>

    // [기존] 백엔드 서버에 메타데이터 등록
    @Multipart
    @POST("api/video/metadata")
    suspend fun registerVideoMetadata(
        @Part metadata: MultipartBody.Part,
        @Part("videoId") videoId: RequestBody
    ): Response<ResponseBody>
}