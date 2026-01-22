package com.tukorea.synclab_mobile

import org.junit.Test
import org.junit.Assert.assertEquals

//NTP 시간이 정확히 계산되는지 확인하는 테스트

class NtpSyncTest {
    @Test
    fun `NTP_오프셋_적용_시간계산_테스트`() {
        val phoneTime = 1000000L
        val ntpOffset = 500L
        val result = phoneTime + ntpOffset
        assertEquals(1000500L, result)
    }
}