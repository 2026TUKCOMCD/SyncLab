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
    private val api: VideoUploadService by lazy { NetworkClient.service }

    private val CHUNK_SIZE = 5 * 1024 * 1024L // 5MB
    private val MAX_RETRIES = 3

    suspend fun uploadVideoToS3(
        videoFile: File,
        metadata: VideoMetadata,
        sessionId: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) {
                    Log.e("UploadRepository", "❌ 파일을 찾을 수 없음: ${videoFile.absolutePath}")
                    return@withContext Result.failure(Exception("파일 없음"))
                }

                if (sessionId.isNullOrEmpty()) {
                    Log.e("UploadRepository", "❌ 세션 ID가 누락되었습니다. (파라미터 확인 필요)")
                    return@withContext Result.failure(Exception("세션 ID 누락"))
                }

                val chunkCount = Math.ceil(videoFile.length().toDouble() / CHUNK_SIZE).toInt()
                Log.d("UploadRepository", "🚀 S3 업로드 시퀀스 시작: SID=$sessionId, File=${videoFile.name} (파트: $chunkCount)")


                val initResponse = try {
                    api.initMultipartUpload(
                        sessionId = sessionId,
                        filename = videoFile.name,
                        partCount = chunkCount
                    )
                } catch (e: Exception) {
                    Log.e("UploadRepository", "❌ initMultipartUpload API 호출 실패", e)
                    throw e
                }

                val uploadId = initResponse.uploadId
                val presignedUrls = initResponse.presignedUrls
                val s3Key = initResponse.s3Key ?: "$sessionId/${videoFile.name}"

                val etags = mutableListOf<String>()

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
                        Log.w("UploadRepository", "⚠️ Part ${i+1} 재시도 중... ($retryCount/$MAX_RETRIES)")
                        delay(2000)
                    }

                    if (successEtag == null) {
                        throw Exception("${i + 1}번 조각 업로드 최종 실패")
                    }

                    etags.add(successEtag)
                    onProgress((i + 1).toFloat() / chunkCount)
                }

                val completeRequest = CompleteUploadRequest(
                    sessionId = sessionId,
                    uploadId = uploadId,
                    videoName = s3Key,
                    etags = etags,
                    metadata = metadata.copy(sessionId = sessionId)
                )

                Log.d("UploadRepository", "🔗 S3 전송 완료, 서버에 최종 등록 중... (SID: $sessionId)")
                val response = api.completeAndRegister(completeRequest)

                if (response.isSuccessful) {
                    Log.d("UploadRepository", "✅ 모든 과정 성공적으로 종료")
                    Result.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("UploadRepository", "❌ 서버 등록 실패: $errorBody")
                    Result.failure(Exception("서버 등록 실패: $errorBody"))
                }

            } catch (e: Exception) {
                Log.e("UploadRepository", "🔥 예외 발생", e)
                Result.failure(e)
            }
        }
    }
}