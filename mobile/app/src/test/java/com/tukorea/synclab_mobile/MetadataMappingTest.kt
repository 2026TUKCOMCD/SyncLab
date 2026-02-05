package com.tukorea.synclab_mobile

import com.google.gson.Gson
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * 서버 JSON 데이터와 VideoMetadata 모델 간의 매핑을 검증하는 테스트
 */
class MetadataMappingTest {

    @Test
    fun `서버_JSON_파싱_및_세션ID_검증_테스트`() {
        // 1. 서버에서 내려줄 수 있는 형태의 JSON (sessionId 포함)
        val json = """
            {
                "videoName": "SyncLab_20240101",
                "fileName": "SyncLab_20240101.mp4",
                "absoluteStartTime": 1704067200000,
                "absoluteEndTime": 1704067210000,
                "duration": 10.0,
                "sessionId": "room_alpha_01"
            }
        """.trimIndent()

        // 2. GSON을 이용한 역직렬화
        val metadata = Gson().fromJson(json, VideoMetadata::class.java)

        // 3. 검증
        assertNotNull("파싱된 객체는 null이 아니어야 합니다", metadata)
        assertEquals("SyncLab_20240101.mp4", metadata.fileName)
        assertEquals("room_alpha_01", metadata.sessionId) // 세션 ID 매핑 확인
        assertEquals(10.0, metadata.duration, 0.001) // 소수점 오차 허용 범위 지정
    }

    @Test
    fun `모델을_JSON으로_변환_테스트`() {
        // 반대로 객체를 JSON으로 만들었을 때 형식이 올바른지 확인
        val metadata = VideoMetadata(
            videoName = "test",
            fileName = "test.mp4",
            absoluteStartTime = 1000L,
            absoluteEndTime = 2000L,
            duration = 1.0,
            sessionId = "session_123"
        )

        val jsonOutput = Gson().toJson(metadata)

        // JSON 문자열에 필수 키값이 포함되어 있는지 확인
        assertEquals(true, jsonOutput.contains("\"sessionId\":\"session_123\""))
    }
}