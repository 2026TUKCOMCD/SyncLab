package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.VidoeUploadService
import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.utils.S3Uploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class UploadRepository {
    private val api: VidoeUploadService = NetworkClient.service

    private val s3Client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val CHUNK_SIZE = 5 * 1024 * 1024L // 5MB
    private val MAX_RETRIES = 3 // P0: 최대 재시도 횟수

    /**
     * S3 분할 업로드를 수행하고, 완료 시 메타데이터를 함께 등록합니다.
     * @param onProgress: (Float) -> Unit 형태의 콜백 함수 추가 (0.0 ~ 1.0 전달)
     */
    suspend fun uploadVideoToS3(
        videoFile: File,
        metadata: VideoMetadata,
        onProgress: (Float) -> Unit // 실시간 진행률 보고를 위한 콜백 추가
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) return@withContext Result.failure(Exception("파일 없음"))

                // 1. [단계 1] 서버에 분할 업로드 시작 요청
                val chunkCount = Math.ceil(videoFile.length().toDouble() / CHUNK_SIZE).toInt()
                val initResponse = api.initMultipartUpload(videoFile.name, chunkCount)
                val uploadId = initResponse.uploadId
                val presignedUrls = initResponse.presignedUrls

                val etags = mutableListOf<String>()

                // 2. [단계 2] 각 조각(Part) 업로드 수행
                for (i in 0 until chunkCount) {
                    val offset = i * CHUNK_SIZE
                    val currentPartSize = Math.min(CHUNK_SIZE, videoFile.length() - offset)

                    var retryCount = 0
                    var successEtag: String? = null

                    // P0: 재시도 로직
                    while (retryCount < MAX_RETRIES) {
                        successEtag = S3Uploader.uploadPart(
                            partUrl = presignedUrls[i],
                            file = videoFile,
                            partNumber = i + 1,
                            offset = offset,
                            partSize = currentPartSize
                        )

                        if (successEtag != null) break

                        retryCount++
                        delay(2000)
                    }

                    if (successEtag == null) {
                        return@withContext Result.failure(Exception("${i + 1}번째 조각 최종 실패"))
                    }

                    etags.add(successEtag)

                    // ✅ [콜백 역할] 조각 하나 성공할 때마다 진행률 계산하여 UI에 전달
                    val progress = (i + 1).toFloat() / chunkCount
                    withContext(Dispatchers.Main) {
                        onProgress(progress) // UI 스레드에서 콜백 실행
                    }
                }

                // 3. [단계 3] 완료 보고 및 메타데이터 통합 등록 [cite: 6]
                val completeRequest = CompleteUploadRequest(
                    uploadId = uploadId,
                    videoName = videoFile.name,
                    etags = etags,
                    metadata = metadata
                )

                val response = api.completeAndRegister(completeRequest)

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("서버 처리 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}