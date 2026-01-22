package com.tukorea.synclab_mobile

import org.junit.Test
import org.junit.Assert.assertEquals
import kotlin.math.ceil

//큰 영상을 분할할때 조각의 개수가 정확히 계산되는지 검증

class VideoSplitTest {
    @Test
    fun `영상_분할_개수_정확도_테스트`() {
        val fileSize = 12 * 1024 * 1024L // 12MB
        val chunkSize = 5 * 1024 * 1024L // 5MB
        val count = ceil(fileSize.toDouble() / chunkSize).toInt()
        assertEquals(3, count) // 5+5+2 이므로 3개
    }
}