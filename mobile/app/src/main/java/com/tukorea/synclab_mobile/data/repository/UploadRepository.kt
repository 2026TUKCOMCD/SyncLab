package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.VideoUploadService // 1. 오타 수정: Vidoe -> Video
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
    // 2. NetworkClient.service의 타입을 VideoUploadService로 정확히 매칭
    private val api: VideoUploadService = NetworkClient.service

    private val s3Client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val CHUNK_SIZE = 5 * 1024 * 1024L // 5MB
    private val MAX_RETRIES = 3 // P0: 최대 재시도 횟수

    /**
     * S3 분할 업로드를 수행하고, 완료 시 메타데이터를 함께 등록합니다.
     * 함수명은 기존대로 유지하였습니다.
     */
    suspend fun uploadVideoToS3(
        videoFile: File,
        metadata: VideoMetadata,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) return@withContext Result.failure(Exception("파일 없음"))

                // 1. [단계 1] 서버에 분할 업로드 시작 요청
                val chunkCount = Math.ceil(videoFile.length().toDouble() / CHUNK_SIZE).toInt()

                // API 호출 (initMultipartUpload)
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

                    // P0: 재시도 로직 (성공할 때까지 혹은 최대 3회까지)
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
                        Log.d("UploadRepository", "${i + 1}번째 조각 업로드 실패, ${retryCount}회 재시도 중...")
                        delay(2000) // 23일 협의사항: 실패 시 2초 대기
                    }

                    if (successEtag == null) {
                        return@withContext Result.failure(Exception("${i + 1}번째 조각 최종 실패"))
                    }

                    etags.add(successEtag)

                    // ✅ 실시간 진행률 보고 (UI 스레드)
                    val progress = (i + 1).toFloat() / chunkCount
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }

                // 3. [단계 3] 완료 보고 및 메타데이터 통합 등록
                val completeRequest = CompleteUploadRequest(
                    uploadId = uploadId,
                    videoName = videoFile.name,
                    etags = etags,
                    metadata = metadata
                )

                // 23일 핵심 수정: completeAndRegister 호출
                val response = api.completeAndRegister(completeRequest)

                if (response.isSuccessful) {
                    Log.i("UploadRepository", "최종 업로드 및 메타데이터 등록 성공")
                    Result.success(Unit)
                } else {
                    Log.e("UploadRepository", "서버 처리 실패: ${response.code()}")
                    Result.failure(Exception("서버 처리 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("UploadRepository", "업로드 중 예외 발생: ${e.message}")
                Result.failure(e)
            }
        }
    }
}