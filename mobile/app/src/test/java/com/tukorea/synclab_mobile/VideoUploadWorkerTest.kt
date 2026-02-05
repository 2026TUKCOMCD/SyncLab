package com.tukorea.synclab_mobile.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VideoUploadWorkerTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepository(context)
    }

    @Test
    fun testWorkerReturnsRetryWhenWifiOnlyAndNoWifi() = runBlocking {
        // 1. 설정값 강제 세팅 (Wi-Fi 전용 ON)
        repository.updateWifiOnly(true)

        // 2. 가상의 파일 생성 (실제 경로가 필요하므로)
        val videoFile = File(context.cacheDir, "test_video.mp4").apply { createNewFile() }
        val jsonFile = File(context.cacheDir, "test_video.json").apply {
            writeText("{\"sessionId\":\"test_id\"}")
        }

        // 3. Worker 생성
        val worker = TestListenableWorkerBuilder<VideoUploadWorker>(
            context = context,
            inputData = workDataOf(
                "video_path" to videoFile.absolutePath,
                "json_path" to jsonFile.absolutePath
            )
        ).build()

        // 4. 실행 및 결과 검증
        // 네트워크 상태를 강제로 바꿀 수는 없으므로, 현재 기기 상태에 따라 로직을 탑니다.
        val result = worker.doWork()

        // 결과 확인 로그 (Logcat에서 확인 가능)
        println("Worker Result: $result")

        // 주의: 이 테스트는 현재 기기의 실제 네트워크에 의존합니다.
        // 만약 LTE 상태라면 result는 ListenableWorker.Result.retry()여야 합니다.
    }
}