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
                    return@withContext Result.failure(Exception("파일을 찾을 수 없습니다: ${videoFile.absolutePath}"))
                }

                // [준비] 세션 ID 및 파트 개수 계산
                val sessionId = metadata.sessionId ?: return@withContext Result.failure(Exception("세션 ID가 누락되었습니다."))
                val chunkCount = Math.ceil(videoFile.length().toDouble() / CHUNK_SIZE).toInt()

                Log.d("UploadRepository", "🚀 업로드 시작: ${videoFile.name} (세션: $sessionId, 파트: $chunkCount)")

                // [1단계] 업로드 초기화 (S3 UploadId 및 Presigned URLs 발급)
                // 인터페이스의 @Query 설정에 따라 URL 파라미터로 전달됩니다.
                val initResponse = api.initMultipartUpload(
                    sessionId = sessionId,
                    filename = videoFile.name,
                    partCount = chunkCount
                )

                val uploadId = initResponse.uploadId
                val presignedUrls = initResponse.presignedUrls
                // 서버가 준 s3Key가 없으면 "세션ID/파일명" 구조로 직접 생성
                val s3Key = initResponse.s3Key ?: "$sessionId/${videoFile.name}"

                val etags = mutableListOf<String>()

                // [2단계] S3로 조각(Part) 업로드
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
                        Log.w("UploadRepository", "⚠️ ${i + 1}번 조각 재시도 ($retryCount/$MAX_RETRIES)")
                        delay(2000)
                    }

                    if (successEtag == null) {
                        return@withContext Result.failure(Exception("${i + 1}번 조각 업로드 실패 (최대 재시도 초과)"))
                    }

                    etags.add(successEtag)

                    // 메인 스레드에서 프로그레스 업데이트
                    withContext(Dispatchers.Main) {
                        onProgress((i + 1).toFloat() / chunkCount)
                    }
                }

                // [3단계] 서버 완료 보고 및 DB 등록
                val completeRequest = CompleteUploadRequest(
                    sessionId = sessionId,
                    uploadId = uploadId,
                    videoName = s3Key, // S3 경로 (session_id/filename.mp4)
                    etags = etags,
                    metadata = metadata.copy(sessionId = sessionId)
                )

                Log.d("UploadRepository", "🔗 서버에 업로드 완료 보고 전송 중...")

                val response = api.completeAndRegister(completeRequest)

                if (response.isSuccessful) {
                    Log.d("UploadRepository", "✅ 모든 과정 성공적으로 완료")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("UploadRepository", "❌ 완료 보고 실패: $errorBody")
                    Result.failure(Exception("서버 등록 실패: $errorBody"))
                }

            } catch (e: Exception) {
                Log.e("UploadRepository", "❌ 예외 발생: ${e.message}")
                Result.failure(e)
            }
        }
    }
}