package com.tukorea.synclab_mobile.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import java.net.InetAddress

/**
 * SyncLab 초정밀 동기화 매니저
 * 이제 서버측 보간 없이, 보정된 절대 시간(NTP Time)만을 제공합니다.
 */
object NtpSyncManager {
    private const val TAG = "NtpSyncManager"
    private const val NTP_SERVER = "time.google.com"
    private const val TIMEOUT_MS = 3000
    private const val SYNC_INTERVAL_MS = 10 * 60 * 1000 // 10분 간격

    private var lastTimeOffset: Long = 0
    private var lastSyncTimestamp: Long = 0
    private var isSyncedAtLeastOnce: Boolean = false

    /**
     * 동기화가 필요한지 확인하고 실행합니다.
     */
    suspend fun checkAndSync(isRecording: Boolean = false): Boolean {
        if (isRecording) {
            Log.d(TAG, "녹화 중이므로 NTP 동기화를 스킵합니다.")
            return isSyncedAtLeastOnce
        }

        val currentTime = System.currentTimeMillis()
        return if (!isSyncedAtLeastOnce || (currentTime - lastSyncTimestamp > SYNC_INTERVAL_MS)) {
            sync()
        } else {
            true
        }
    }

    /**
     * NTP 서버와 통신하여 현재의 시간 오차(Offset)만 측정합니다.
     */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val client = NTPUDPClient()
        client.defaultTimeout = TIMEOUT_MS

        return@withContext try {
            val address = InetAddress.getByName(NTP_SERVER)
            val info = client.getTime(address)

            // 상세 계산 실행
            info.computeDetails()

            // 오프셋(서버 시간 - 시스템 시간) 추출
            lastTimeOffset = info.offset ?: 0L
            lastSyncTimestamp = System.currentTimeMillis()
            isSyncedAtLeastOnce = true

            Log.d(TAG, "NTP 동기화 성공: Offset=$lastTimeOffset ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "NTP 동기화 실패: ${e.message}")
            false
        } finally {
            client.close()
        }
    }

    /**
     * JSON의 absoluteStartTime, absoluteEndTime에 사용할
     * 보정된 절대 시간(NTP 기준)을 반환합니다.
     */
    fun getCurrentNtpTime(): Long {
        return System.currentTimeMillis() + lastTimeOffset
    }

    fun isSynced(): Boolean = isSyncedAtLeastOnce
    fun getSavedOffset(): Long = lastTimeOffset
    fun getLastSyncTime(): Long = lastSyncTimestamp
}