package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.VideoUploadService
import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.utils.S3Uploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class UploadRepository {
    private val api: VideoUploadService = NetworkClient.service
    private val CHUNK_SIZE = 5 * 1024 * 1024L // 5MB
    private val MAX_RETRIES = 3

    suspend fun uploadVideoToS3(
        videoFile: File,
        metadata: VideoMetadata,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) {
                    return@withContext Result.failure(Exception("파일 찾을 수 없음: ${videoFile.absolutePath}"))
                }

                val chunkCount = Math.ceil(videoFile.length().toDouble() / CHUNK_SIZE).toInt()
                Log.d("UploadRepository", "🚀 시작: ${videoFile.name}, 세션: ${metadata.sessionId}")

                // [1단계] 업로드 초기화 (S3 UploadId 발급)
                val initResponse = api.initMultipartUpload(
                    filename = videoFile.name,
                    partCount = chunkCount,
                    sessionId = metadata.sessionId
                )

                val uploadId = initResponse.uploadId
                val presignedUrls = initResponse.presignedUrls
                val s3Key = initResponse.s3Key

                val etags = mutableListOf<String>()

                // [2단계] S3로 조각 업로드
                for (i in 0 until chunkCount) {
                    val offset = i * CHUNK_SIZE
                    val currentPartSize = Math.min(CHUNK_SIZE, videoFile.length() - offset)

                    var retryCount = 0
                    var successEtag: String? = null

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

                    if (successEtag == null) return@withContext Result.failure(Exception("${i+1}번 조각 실패"))
                    etags.add(successEtag)

                    withContext(Dispatchers.Main) {
                        onProgress((i + 1).toFloat() / chunkCount)
                    }
                }

                // [3단계] 서버 완료 보고 (sessionId 누락 방지)
                val finalSessionId = metadata.sessionId ?: "default_session"

                val completeRequest = CompleteUploadRequest(
                    sessionId = finalSessionId, // ⭐️ 이 필드가 추가되어야 합니다!
                    uploadId = uploadId,
                    videoName = s3Key ?: videoFile.name,
                    etags = etags,
                    metadata = metadata.copy(sessionId = finalSessionId)
                )

                Log.d("UploadRepository", "🔗 완료 요청 전송: Session=$finalSessionId")

                // 타임아웃 발생 지점: 네트워크 상태가 안 좋으면 여기서 터집니다.
                val response = api.completeAndRegister(completeRequest)

                if (response.isSuccessful) {
                    Log.d("UploadRepository", "✅ 업로드 성공")
                    Result.success(Unit)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "알 수 없는 에러"
                    Result.failure(Exception("서버 오류: $errorMsg"))
                }

            } catch (e: Exception) {
                Log.e("UploadRepository", "❌ 에러 발생: ${e.message}")
                Result.failure(e)
            }
        }
    }
}