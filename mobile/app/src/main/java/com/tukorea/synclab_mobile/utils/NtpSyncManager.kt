package com.tukorea.synclab_mobile.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ntp.NTPUDPClient
import java.net.InetAddress

//NTP 오차 측정
object NtpSyncManager {
    private const val TAG = "NtpSyncManager"
    private const val NTP_SERVER = "time.google.com"
    private const val TIMEOUT_MS = 3000
    private const val SYNC_INTERVAL_MS = 10 * 60 * 1000 // 10분 간격

    private var lastTimeOffset: Long = 0
    private var lastSyncTimestamp: Long = 0
    private var isSyncedAtLeastOnce: Boolean = false

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
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        val client = NTPUDPClient()
        client.defaultTimeout = TIMEOUT_MS

        return@withContext try {
            val address = InetAddress.getByName(NTP_SERVER)
            val info = client.getTime(address)

            info.computeDetails()

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

    fun getCurrentNtpTime(): Long {
        return System.currentTimeMillis() + lastTimeOffset
    }

    fun isSynced(): Boolean = isSyncedAtLeastOnce
    fun getSavedOffset(): Long = lastTimeOffset
    fun getLastSyncTime(): Long = lastSyncTimestamp
}