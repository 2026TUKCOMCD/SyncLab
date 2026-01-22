package com.tukorea.synclab_mobile

import com.google.gson.Gson
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import org.junit.Test
import org.junit.Assert.assertEquals

//서버에서 온 JSON 데이터가 우리 데이터 모델에 오차없이 들어가는지 확인

class MetadataMappingTest {
    @Test
    fun `서버_JSON_파싱_테스트`() {
        val json = """{"videoName":"test","fileName":"test.mp4","absoluteStartTime":100,"absoluteEndTime":200,"duration":5.0}"""
        val metadata = Gson().fromJson(json, VideoMetadata::class.java)

        assertEquals("test.mp4", metadata.fileName)
        assertEquals(5.0, metadata.duration, 0.0)
    }
}