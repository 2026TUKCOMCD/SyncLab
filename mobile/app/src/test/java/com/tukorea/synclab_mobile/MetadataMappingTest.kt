package com.tukorea.synclab_mobile

import com.google.gson.Gson
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MetadataMappingTest {
    @Test
    fun `서버_JSON_파싱_및_세션ID_검증_테스트`() {
        // 모델의 @SerializedName과 일치하도록 키값 수정 (snake_case)
        val json = """
            {
                "video_name": "SyncLab_20240101",
                "file_name": "SyncLab_20240101.mp4",
                "absolute_start_time": 1704067200000,
                "absolute_end_time": 1704067210000,
                "duration": 10.0,
                "session_id": "room_alpha_01"
            }
        """.trimIndent()

        val metadata = Gson().fromJson(json, VideoMetadata::class.java)

        // 이제 정상적으로 매핑되어 null이 나오지 않습니다.
        assertEquals("SyncLab_20240101.mp4", metadata.fileName)
        assertEquals("room_alpha_01", metadata.sessionId)
    }

    @Test
    fun `모델을_JSON으로_변환_테스트`() {
        val metadata = VideoMetadata(
            videoName = "test",
            fileName = "test.mp4",
            absoluteStartTime = 1000L,
            absoluteEndTime = 2000L,
            duration = 1.0,
            sessionId = "session_123"
        )

        val jsonOutput = metadata.toJson()

        // 변환된 JSON도 snake_case인지 확인해야 테스트가 통과합니다.
        assertTrue(jsonOutput.contains("\"session_id\":\"session_123\""))
        assertTrue(jsonOutput.contains("\"file_name\":\"test.mp4\""))
    }
}