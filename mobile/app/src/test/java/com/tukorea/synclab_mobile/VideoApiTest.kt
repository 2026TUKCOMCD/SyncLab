package com.tukorea.synclab_mobile

import com.tukorea.synclab_mobile.data.api.VideoUploadService
import com.tukorea.synclab_mobile.data.model.CompleteUploadRequest
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class VideoApiTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var testApiService: VideoUploadService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // 1. 네트워크 통신 자체에 타임아웃을 걸어 무한 대기를 방지합니다.
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS) // 연결 시도 3초 제한
            .readTimeout(3, TimeUnit.SECONDS)    // 데이터 읽기 3초 제한
            .writeTimeout(3, TimeUnit.SECONDS)   // 데이터 쓰기 3초 제한
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient) // 설정한 클라이언트 적용
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        testApiService = retrofit.create(VideoUploadService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * 시나리오 1: 데이터 규격 검증 (5초 내 종료 보장)
     */
    @Test(timeout = 5000)
    fun `서버_JSON_전송_데이터_규격_로컬_검증`() = runBlocking {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"status\":\"success\"}")
        )

        val dummyMetadata = VideoMetadata(
            videoName = "졸작_테스트_영상",
            fileName = "match_01.mp4",
            absoluteStartTime = 1705910000000L,
            absoluteEndTime = 1705915000000L,
            duration = 5.0
        )
        val dummyRequest = CompleteUploadRequest("test_123", "match_01.mp4", listOf("e1"), dummyMetadata)

        val response = testApiService.completeAndRegister(dummyRequest)
        val recordedRequest = mockWebServer.takeRequest(3, TimeUnit.SECONDS)

        assertTrue(response.isSuccessful)
        assertEquals("/api/video/upload/complete", recordedRequest?.path)
        println("테스트 완료: 데이터가 정상적으로 전송되었습니다.")
    }

    /**
     * 시나리오 2: 응답 지연 발생 시 강제 종료 확인 (5초 내 종료 보장)
     */
    @Test(timeout = 5000)
    fun `네트워크_지연_시_타임아웃_처리_검증`() = runBlocking {
        // 서버가 응답을 10초 동안 주지 않도록 설정
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(10, TimeUnit.SECONDS)
                .setResponseCode(200)
        )

        val isTimeoutOccurred = try {
            // 실제 API 호출 시 위에서 설정한 readTimeout(3초)에 의해 종료됨
            testApiService.initMultipartUpload("test.mp4", 1)
            false
        } catch (e: Exception) {
            // SocketTimeoutException 등이 발생하면 성공으로 간주
            println("정상적으로 타임아웃 감지됨: ${e.message}")
            true
        }

        assertTrue("타임아웃 예외가 발생해야 합니다.", isTimeoutOccurred)
    }

    /**
     * 시나리오 3: 물리적 연결 끊김 (즉시 종료)
     */
    @Test(timeout = 3000)
    fun `물리적_네트워크_단절_시_IOException_발생_확인`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = try {
            testApiService.initMultipartUpload("test.mp4", 3)
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(result is IOException)
    }
}