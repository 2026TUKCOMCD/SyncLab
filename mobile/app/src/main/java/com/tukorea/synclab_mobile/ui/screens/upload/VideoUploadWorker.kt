package com.tukorea.synclab_mobile.ui.screens.upload

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.data.repository.UploadRepository
import java.io.File

class VideoUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private lateinit var uploadRepository: UploadRepository

    override suspend fun doWork(): Result {
        NetworkClient.init(applicationContext)
        uploadRepository = UploadRepository()

        val videoPath = inputData.getString("video_path") ?: return Result.failure()
        val jsonPath = inputData.getString("json_path") ?: return Result.failure()
        val sessionId = inputData.getString("session_id")

        if (sessionId.isNullOrBlank()) {
            Log.e("VideoUploadWorker", "❌ 세션 ID가 누락되었습니다.")
            return Result.failure()
        }

        val videoFile = File(videoPath)
        val jsonFile = File(jsonPath)

        if (!videoFile.exists() || !jsonFile.exists()) {
            Log.e("VideoUploadWorker", "파일 없음: $videoPath")
            return Result.failure()
        }

        return try {
            val metadataString = jsonFile.readText()
            val metadata = Gson().fromJson(metadataString, VideoMetadata::class.java)

            Log.d("VideoUploadWorker", "🚀 업로드 시작 - SID: $sessionId, File: ${videoFile.name}")

            val result = uploadRepository.uploadVideoToS3(
                videoFile = videoFile,
                metadata = metadata,
                sessionId = sessionId
            ) { progress ->
                if (!isStopped) {
                    setProgressAsync(workDataOf("progress" to progress))
                }
            }

            if (result.isSuccess) {
                Log.d("VideoUploadWorker", "✅ 업로드 성공")
                Result.success()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown Error"
                Log.e("VideoUploadWorker", "❌ 업로드 실패: $errorMsg")

                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("VideoUploadWorker", "🔥 예외 발생: ${e.message}", e)
            Result.retry()
        }
    }
}