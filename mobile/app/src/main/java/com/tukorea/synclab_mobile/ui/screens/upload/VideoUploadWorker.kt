package com.tukorea.synclab_mobile.ui.screens.upload

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import com.tukorea.synclab_mobile.data.repository.UploadRepository
import com.tukorea.synclab_mobile.utils.NetworkMonitor
import kotlinx.coroutines.flow.first
import java.io.File

class VideoUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val uploadRepository = UploadRepository()
    private val settingsRepository = SettingsRepository(context)
    private val networkMonitor = NetworkMonitor(context)

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString("video_path") ?: return Result.failure()
        val jsonPath = inputData.getString("json_path") ?: return Result.failure()

        val videoFile = File(videoPath)
        val jsonFile = File(jsonPath)

        // 1. 파일 확인
        if (!videoFile.exists() || !jsonFile.exists()) return Result.failure()

        // 2. 설정 및 네트워크 체크 (Wi-Fi 전용 설정 시)
        val isWifiOnly = settingsRepository.isWifiOnlyFlow.first()
        val isWifiNow = networkMonitor.isWifiConnected.first()

        if (isWifiOnly && !isWifiNow) {
            // Wi-Fi가 아니면 나중에 다시 시도하도록 예약
            return Result.retry()
        }

        return try {
            // 3. JSON 메타데이터 파싱
            val metadata = Gson().fromJson(jsonFile.readText(), VideoMetadata::class.java)

            // 4. 리포지토리 호출하여 실제 업로드 수행
            val result = uploadRepository.uploadVideoToS3(videoFile, metadata) { progress ->
                // WorkManager 내부에 진행률 저장 (UI에서 관찰 가능)
                setProgressAsync(workDataOf("progress" to progress))
            }

            if (result.isSuccess) {
                // 성공 시 임시 파일 삭제 (필요 시)
                // videoFile.delete()
                // jsonFile.delete()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("VideoUploadWorker", "Upload failed", e)
            Result.retry()
        }
    }
}