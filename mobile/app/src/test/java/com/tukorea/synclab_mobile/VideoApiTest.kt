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

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        testApiService = retrofit.create(VideoUploadService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * 시나리오 1: 데이터 규격 검증
     */
    @Test(timeout = 5000)
    fun `서버_JSON_전송_데이터_규격_로컬_검증`() = runBlocking {
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"status\":\"success\"}")
        )

        val testSessionId = "SESS-DEBUG-001"

        val dummyMetadata = VideoMetadata(
            videoName = "졸작_테스트_영상",
            fileName = "match_01.mp4",
            absoluteStartTime = 1705910000000L,
            absoluteEndTime = 1705915000000L,
            duration = 5.0,
            sessionId = testSessionId
        )

        val dummyRequest = CompleteUploadRequest(
            sessionId = testSessionId,
            uploadId = "test_123",
            videoName = "match_01.mp4",
            etags = listOf("e1"),
            metadata = dummyMetadata
        )

        val response = testApiService.completeAndRegister(dummyRequest)
        val recordedRequest = mockWebServer.takeRequest(3, TimeUnit.SECONDS)

        assertTrue(response.isSuccessful)
        assertEquals("/api/video/upload/complete", recordedRequest?.path)
    }

    /**
     * 시나리오 2: 응답 지연 발생 시 타임아웃 처리 확인 (partCount 에러 수정)
     */
    @Test(timeout = 5000)
    fun `네트워크_지연_시_타임아웃_처리_검증`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(10, TimeUnit.SECONDS)
                .setResponseCode(200)
        )

        val isTimeoutOccurred = try {
            // ⭐️ 에러 해결: 파라미터 이름을 명시하거나 인터페이스 정의 순서 확인
            // 만약 인터페이스에 sessionId가 필수라면 세 번째 인자도 넣어줘야 합니다.
            testApiService.initMultipartUpload(
                filename = "test.mp4",
                partCount = 1,
                sessionId = "default_session"
            )
            false
        } catch (e: Exception) {
            println("정상적으로 타임아웃 감지됨: ${e.message}")
            true
        }

        assertTrue("3초 경과 시 타임아웃 예외가 발생해야 합니다.", isTimeoutOccurred)
    }

    /**
     * 시나리오 3: 물리적 연결 단절 상황 (partCount 에러 수정)
     */
    @Test(timeout = 3000)
    fun `물리적_네트워크_단절_시_IOException_발생_확인`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = try {
            // ⭐️ 여기도 동일하게 파라미터 규격을 맞춥니다.
            testApiService.initMultipartUpload(
                filename = "test.mp4",
                partCount = 3,
                sessionId = "default_session"
            )
            null
        } catch (e: Exception) {
            e
        }

        assertTrue("IOException 계열의 에러가 발생해야 합니다.", result is IOException)
    }
}