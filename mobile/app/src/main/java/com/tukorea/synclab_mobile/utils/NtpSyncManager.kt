package com.tukorea.synclab_mobile.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import java.net.InetAddress

/**
 * SyncLab 초정밀 동기화 매니저
 * 전략 B: 서버측 선형 보간을 위해 Offset과 RTT(지연시간)를 함께 측정합니다.
 */
object NtpSyncManager {
    private const val TAG = "NtpSyncManager"
    private const val NTP_SERVER = "time.google.com"
    private const val TIMEOUT_MS = 3000
    private const val SYNC_INTERVAL_MS = 10 * 60 * 1000 // 10분 간격

    // 마지막으로 성공한 동기화 정보 저장
    private var lastTimeOffset: Long = 0
    private var lastRoundTripTime: Long = 0
    private var lastSyncTimestamp: Long = 0
    private var isSyncedAtLeastOnce: Boolean = false

    /**
     * 동기화가 필요한지 확인하고 실행합니다.
     * @param isRecording 현재 녹화 중인지 여부 (녹화 중에는 강제 동기화 지양)
     */
    suspend fun checkAndSync(isRecording: Boolean): SyncResult {
        val currentTime = System.currentTimeMillis()

        // 마지막 동기화로부터 10분이 지났거나 한 번도 안 했을 경우 실행
        return if (!isSyncedAtLeastOnce || (currentTime - lastSyncTimestamp > SYNC_INTERVAL_MS)) {
            Log.d(TAG, "동기화 조건 충족: 동기화 시작")
            sync()
        } else {
            Log.d(TAG, "최근 동기화 기록이 유효함. 스킵합니다.")
            SyncResult(true, lastTimeOffset, lastRoundTripTime, lastSyncTimestamp)
        }
    }

    /**
     * NTP 서버와 통신하여 현재의 시간 오차와 네트워크 지연시간을 측정합니다.
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val client = NTPUDPClient()
        client.defaultTimeout = TIMEOUT_MS

        return@withContext try {
            val address = InetAddress.getByName(NTP_SERVER)
            val info = client.getTime(address)

            info.computeDetails()

            val offset = info.offset ?: 0L
            val rtt = info.delay ?: 0L

            lastTimeOffset = offset
            lastRoundTripTime = rtt
            lastSyncTimestamp = System.currentTimeMillis()
            isSyncedAtLeastOnce = true

            Log.d(TAG, "NTP 동기화 성공: Offset=$offset ms, RTT=$rtt ms")
            SyncResult(true, offset, rtt, lastSyncTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "NTP 동기화 실패: ${e.message}")
            SyncResult(false, lastTimeOffset, lastRoundTripTime, lastSyncTimestamp)
        } finally {
            client.close()
        }
    }

    /**
     * 시스템 시간에 현재 오프셋을 더한 보정 시간을 반환
     */
    fun getCurrentNtpTime(): Long {
        return System.currentTimeMillis() + lastTimeOffset
    }

    fun isSynced(): Boolean = isSyncedAtLeastOnce
    fun getSavedOffset(): Long = lastTimeOffset
    fun getSavedRtt(): Long = lastRoundTripTime
    fun getLastSyncTime(): Long = lastSyncTimestamp

    data class SyncResult(
        val isSuccess: Boolean,
        val offset: Long,
        val rtt: Long,
        val syncTimestamp: Long
    )
}