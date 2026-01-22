package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.InitUploadResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VidoeUploadService {

    /**
     * [단계 1] 분할 업로드 시작 요청
     * 서버로부터 S3 UploadId와 조각별 Presigned URL 리스트를 받아옵니다. [cite: 4]
     */
    @GET("api/video/upload/init")
    suspend fun initMultipartUpload(
        @Query("filename") filename: String,
        @Query("partCount") partCount: Int
    ): InitUploadResponse

    /**
     * [단계 2 & 3 통합] 업로드 완료 보고 및 메타데이터 등록
     * S3 조각 병합 명령과 NTP 기반 메타데이터 저장을 한 번의 트랜잭션으로 처리합니다.
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