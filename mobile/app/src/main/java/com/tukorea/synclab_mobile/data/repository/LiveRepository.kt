package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.CameraParticipant
import com.tukorea.synclab_mobile.data.model.LiveSessionResponse
import com.tukorea.synclab_mobile.data.model.LiveStatusResponse
import com.tukorea.synclab_mobile.data.model.OverlayData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiveRepository {
    private val api = NetworkClient.liveService

    suspend fun createLiveSession(sessionId: String): Result<LiveSessionResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.createLiveSession(mapOf("session_id" to sessionId))
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("라이브 세션 생성 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("LiveRepository", "createLiveSession 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getToken(sessionId: String, role: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getLiveToken(mapOf("session_id" to sessionId, "role" to role))
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!.token)
                } else if (response.code() == 409) {
                    Result.failure(Exception("CONTROLLER_DUPLICATE"))
                } else {
                    Result.failure(Exception("토큰 발급 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("LiveRepository", "getToken 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun switchCamera(sessionId: String, cameraId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.switchCamera(
                    mapOf("session_id" to sessionId, "target_camera_id" to cameraId)
                )
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("카메라 전환 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("LiveRepository", "switchCamera 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun goLive(sessionId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.goLive(mapOf("session_id" to sessionId))
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("go-live 실패: ${response.code()}"))
            } catch (e: Exception) {
                Log.e("LiveRepository", "goLive 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun endLive(sessionId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.endLive(mapOf("session_id" to sessionId))
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("end-live 실패: ${response.code()}"))
            } catch (e: Exception) {
                Log.e("LiveRepository", "endLive 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getLiveStatus(sessionId: String): Result<LiveStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getLiveStatus(sessionId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("상태 조회 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("LiveRepository", "getLiveStatus 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getOverlay(sessionId: String): Result<OverlayData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getOverlay(sessionId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("오버레이 조회 실패: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e("LiveRepository", "getOverlay 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun updateOverlay(sessionId: String, data: OverlayData): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.updateOverlay(sessionId, data)
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("오버레이 업데이트 실패: ${response.code()}"))
            } catch (e: Exception) {
                Log.e("LiveRepository", "updateOverlay 오류: ${e.message}")
                Result.failure(e)
            }
        }
    }
}
