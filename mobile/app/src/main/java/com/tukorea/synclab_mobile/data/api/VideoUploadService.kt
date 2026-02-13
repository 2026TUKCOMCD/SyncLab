package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.InitUploadResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VideoUploadService {


    @GET("api/mobile/video/upload/init")
    suspend fun initMultipartUpload(
        @Query("session_id") sessionId: String, // 폴더명이 될 ID
        @Query("filename") filename: String,   // 저장될 파일 이름
        @Query("part_count") partCount: Int
    ): InitUploadResponse


    @POST("api/mobile/video/upload/complete")
    suspend fun completeAndRegister(
        @Body request: CompleteUploadRequest
    ): Response<ResponseBody>

    
    @GET("api/mobile/video/proxy/check/{sessionId}/{filename}")
    suspend fun checkProxyStatus(
        @Path("sessionId") sessionId: String,
        @Path("filename") filename: String
    ): Map<String, Any>
}