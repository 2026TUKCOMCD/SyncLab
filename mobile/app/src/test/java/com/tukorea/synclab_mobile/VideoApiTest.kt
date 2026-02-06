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

        // 테스트를 위해 타임아웃을 2초로 짧게 설정 (시나리오 2 검증용)
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
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
     * 시나리오 1: 데이터 규격 및 경로 검증
     * 클라이언트가 서버로 보내는 JSON body 내용이 정확한지 확인합니다.
     */
    @Test(timeout = 5000)
    fun `서버_전송_데이터_규격_및_엔드포인트_검증`() = runBlocking {
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
            etags = listOf("etag1", "etag2"),
            metadata = dummyMetadata
        )

        testApiService.completeAndRegister(dummyRequest)

        // 서버에 도달한 실제 요청 가로채기
        val recordedRequest = mockWebServer.takeRequest()

        assertEquals("POST", recordedRequest?.method)
        // 실제 API 경로가 /api/video/upload/complete 인지 확인
        assertTrue(recordedRequest?.path?.contains("complete") == true)
        // 전송된 JSON에 sessionId가 포함되어 있는지 확인
        assertTrue(recordedRequest?.body?.readUtf8()?.contains(testSessionId) == true)
    }

    /**
     * 시나리오 2: 응답 지연 발생 시 타임아웃 처리 확인
     */
    @Test(timeout = 5000)
    fun `네트워크_지연_시_정해진_시간후_타임아웃_발생_확인`() = runBlocking {
        // 서버 응답을 5초 지연시킴 (클라이언트 타임아웃은 2초)
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(5, TimeUnit.SECONDS)
                .setResponseCode(200)
                .setBody("{\"uploadId\":\"late_id\"}")
        )

        val isTimeoutOccurred = try {
            testApiService.initMultipartUpload(
                sessionId = "test_sess",
                filename = "test.mp4",
                partCount = 5
            )
            false
        } catch (e: Exception) {
            println("타임아웃 로그: ${e.message}")
            true
        }

        assertTrue("설정된 타임아웃(2초)보다 지연이 길면 예외가 발생해야 합니다.", isTimeoutOccurred)
    }

    /**
     * 시나리오 3: 물리적 연결 단절 상황
     */
    @Test(timeout = 5000)
    fun `서버_연결_즉시_단절_시_IOException_발생_확인`() = runBlocking {
        // 연결되자마자 끊어버리는 정책 설정
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = try {
            testApiService.initMultipartUpload(
                sessionId = "test_sess",
                filename = "test.mp4",
                partCount = 1
            )
            null
        } catch (e: Exception) {
            e
        }

        // Retrofit/OkHttp는 연결 실패 시 IOException을 던짐
        assertTrue("연결 단절 시 IOException이 발생해야 합니다.", result is IOException)
    }
}