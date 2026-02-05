package com.tukorea.synclab_mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // 테스트할 안드로이드 SDK 버전 명시
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        // Robolectric이 제공하는 가상의 Application Context
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepository(context)
    }

    @Test
    fun `와이파이_설정을_변경하면_DataStore에_반영되어야_함`() = runTest {
        // 1. 초기값 확인 (기본값이 true라고 가정)
        val initialValue = repository.isWifiOnlyFlow.first()
        assertEquals(true, initialValue)

        // 2. 값 변경 실행
        repository.updateWifiOnly(false)

        // 3. 변경된 값 확인
        val updatedValue = repository.isWifiOnlyFlow.first()
        assertEquals(false, updatedValue)
    }
}