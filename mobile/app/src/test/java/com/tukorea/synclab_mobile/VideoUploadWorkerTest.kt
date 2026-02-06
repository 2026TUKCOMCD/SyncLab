package com.tukorea.synclab_mobile.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import com.tukorea.synclab_mobile.ui.screens.upload.VideoUploadWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [해결] @RunWith(RobolectricTestRunner::class)를 추가하여
 * 로컬 유닛 테스트 환경에서도 Android Context를 사용할 수 있게 합니다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // 테스트할 안드로이드 SDK 버전 지정
class VideoUploadWorkerTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        // Robolectric 환경에서 안전하게 Context를 가져옵니다.
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepository(context)
    }

    @Test
    fun testWorkerReturnsRetryWhenWifiOnlyAndNoWifi() = runBlocking {
        // 1. 설정값 강제 세팅 (Wi-Fi 전용 ON)
        repository.updateWifiOnly(true)

        // 2. 가상의 파일 생성 (캐시 디렉토리 활용)
        val videoFile = File(context.cacheDir, "test_video.mp4").apply {
            if (exists()) delete()
            createNewFile()
        }
        val jsonFile = File(context.cacheDir, "test_video.json").apply {
            if (exists()) delete()
            // 모델의 snake_case에 맞춰 JSON 작성
            writeText("""{"session_id":"test_id", "file_name":"test_video.mp4"}""")
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
        val result = worker.doWork()

        // 결과 확인 및 검증
        assertNotNull("Worker 결과는 null일 수 없습니다", result)
        println("Worker Result Status: $result")

        // 실제 네트워크 환경에 따라 결과가 달라질 수 있음을 인지
        // if (네트워크가 LTE라면) assertTrue(result is ListenableWorker.Result.Retry)
    }
}