package com.tukorea.synclab_mobile.data.api

import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.InitUploadResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VideoUploadService {

    /**
     * [1단계] 분할 업로드 시작
     * 서버에 파일 정보와 세션 ID를 전달하여 S3 UploadId 및 Presigned URL들을 발급받습니다.
     */
    @GET("api/mobile/video/upload/init")
    suspend fun initMultipartUpload(
        @Query("filename") filename: String,
        @Query("partCount") partCount: Int,
        @Query("sessionId") sessionId: String
    ): InitUploadResponse

    /**
     * [2단계] 업로드 완료 보고 및 메타데이터 등록
     * S3 조각 업로드가 모두 완료된 후, ETag 리스트와 영상 메타데이터를 서버에 전달하여 병합을 요청합니다.
     */
    @POST("api/mobile/video/upload/complete")
    suspend fun completeAndRegister(
        @Body request: CompleteUploadRequest
    ): Response<ResponseBody>

    /**
     * [3단계] 프록시 영상 처리 상태 확인
     * 서버에서 FFmpeg 작업이 완료되어 480p 프록시 영상이 생성되었는지 확인합니다.
     */
    @GET("api/mobile/video/proxy/check/{sessionId}/{filename}")
    suspend fun checkProxyStatus(
        @Path("sessionId") sessionId: String,
        @Path("filename") filename: String
    ): Map<String, Any>
}