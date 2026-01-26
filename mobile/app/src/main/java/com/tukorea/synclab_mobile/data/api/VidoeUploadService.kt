package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.InitUploadResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

// 1. 오타 수정: Vidoe -> Video (리포지토리에서 참조 에러 방지)
interface VideoUploadService {

    /**
     * [단계 1] 분할 업로드 시작 요청
     * 서버로부터 S3 UploadId와 조각별 Presigned URL 리스트를 받아옵니다.
     */
    @GET("api/video/upload/init")
    suspend fun initMultipartUpload(
        @Query("filename") filename: String,
        @Query("partCount") partCount: Int
    ): InitUploadResponse

    /**
     * [단계 2 & 3 통합] 업로드 완료 보고 및 메타데이터 등록
     * 23일 수정 사항: @Body를 통해 CompleteUploadRequest(메타데이터 포함)를 JSON으로 전송합니다.
     */
    @POST("api/video/upload/complete")
    suspend fun completeAndRegister(
        @Body request: CompleteUploadRequest
    ): Response<ResponseBody>

    /**
     * (옵션) 기존 단일 업로드 방식 유지 시 사용
     */
    @GET("get-url")
    suspend fun getPresignedUrl(
        @Query("filename") filename: String
    ): Map<String, String>
}