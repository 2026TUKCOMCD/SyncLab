package com.tukorea.synclab_mobile.data.api // 이미지 경로 기준

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface VidoeUploadService { // 파일명과 일치시킴
    @Multipart
    @POST("api/video/metadata")
    suspend fun registerVideoMetadata(
        @Part metadata: MultipartBody.Part,
        @Part("videoId") videoId: RequestBody
    ): Response<ResponseBody>
}