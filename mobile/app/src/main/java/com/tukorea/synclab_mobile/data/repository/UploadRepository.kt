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
                // 0. 파일 체크
                if (!videoFile.exists()) return@withContext Result.failure(Exception("파일을 찾을 수 없습니다: ${videoFile.absolutePath}"))

                val chunkCount = Math.ceil(videoFile.length().toDouble() / CHUNK_SIZE).toInt()
                Log.d("UploadRepository", "업로드 시작: ${videoFile.name}, 조각 수: $chunkCount, 세션: ${metadata.sessionId}")

                // [1단계] 업로드 초기화 (서버로부터 경로 및 ID 수신)
                val initResponse = api.initMultipartUpload(
                    filename = videoFile.name,
                    partCount = chunkCount,
                    sessionId = metadata.sessionId
                )

                val uploadId = initResponse.uploadId
                val presignedUrls = initResponse.presignedUrls
                val s3Key = initResponse.s3Key // 서버가 생성한 "세션ID/파일명.mp4"

                val etags = mutableListOf<String>()

                // [2단계] S3로 직접 조각 업로드
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
                        Log.w("UploadRepository", "${i+1}번 조각 업로드 실패, 재시도 중... ($retryCount/$MAX_RETRIES)")
                        delay(2000)
                    }

                    if (successEtag == null) return@withContext Result.failure(Exception("${i+1}번 조각 업로드 최종 실패"))

                    etags.add(successEtag)

                    // 진행률 업데이트
                    withContext(Dispatchers.Main) {
                        onProgress((i + 1).toFloat() / chunkCount)
                    }
                }

                // [3단계] 완료 요청
                // sessionId가 null이거나 빈 문자열일 경우를 대비해 3단계 방어막 구축
                val safeSessionId = if (!metadata.sessionId.isNullOrBlank()) {
                    metadata.sessionId
                } else if (!s3Key.isNullOrBlank() && s3Key.contains("/")) {
                    s3Key.split("/")[0] // s3Key가 "SESS_ID/file.mp4" 형식이므로 여기서 추출
                } else {
                    "unknown_session" // 최악의 경우 기본값
                }

                val finalMetadata = VideoMetadata(
                    videoName = s3Key ?: metadata.videoName,
                    fileName = metadata.fileName,
                    absoluteStartTime = metadata.absoluteStartTime,
                    absoluteEndTime = metadata.absoluteEndTime,
                    duration = metadata.duration,
                    sessionId = safeSessionId // 👈 여기서 절대 null이 들어갈 수 없음
                )

                val completeRequest = CompleteUploadRequest(
                    uploadId = uploadId,
                    videoName = s3Key ?: finalMetadata.videoName,
                    etags = etags,
                    metadata = finalMetadata
                )

                Log.d("UploadComplete_Debug", "최종 확인 - SessionId: ${finalMetadata.sessionId}")
                val response = api.completeAndRegister(completeRequest)

                if (response.isSuccessful) {
                    Log.d("UploadRepository", "🎉 모든 업로드 및 서버 등록 완료!")
                    Result.success(Unit)
                } else {
                    // 서버가 422 또는 500 에러를 던진 경우 상세 사유 추출
                    val errorDetail = response.errorBody()?.string() ?: "알 수 없는 에러"
                    Log.e("UploadRepository", "서버 응답 에러 (${response.code()}): $errorDetail")
                    Result.failure(Exception("서버 처리 실패: $errorDetail"))
                }

            } catch (e: Exception) {
                Log.e("UploadRepository", "Fatal Error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}