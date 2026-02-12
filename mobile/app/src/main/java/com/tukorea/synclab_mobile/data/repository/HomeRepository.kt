package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.HomeService
import com.tukorea.synclab_mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeRepository {
    private val api: HomeService = NetworkClient.homeService

    suspend fun createNewSession(name: String): Result<SessionResponse> {
        return withContext(Dispatchers.IO) {
            try {

                val request = SessionCreateRequest(sessionName = name)
                val response = api.createSession(request)

                Log.d("HomeRepository", "🚀 세션 생성 성공 ID: ${response.session.sessionId}")
                Result.success(response)
            } catch (e: Exception) {
                Log.e("HomeRepository", "❌ 세션 생성 실패: ${e.message}")
                Result.failure(e)
            }
        }
    }


    suspend fun verifyConnectCode(code: String): Result<VerifyCodeResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.verifyTempCode(code)
                Log.d("HomeRepository", "✅ 코드 검증 성공! 세션 연결: ${response.sessionId}")
                Result.success(response)
            } catch (e: Exception) {
                Log.e("HomeRepository", "❌ 코드 검증 실패: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun joinSession(inviteCode: String): Result<SessionJoinResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = SessionJoinRequest(inviteCode = inviteCode)
                val response = api.joinSession(request)
                Log.d("HomeRepository", "✅ 세션 참가 성공: ${response.session.sessionName}")
                Result.success(response)
            } catch (e: Exception) {
                Log.e("HomeRepository", "❌ 세션 참가 실패: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun fetchSessionVideos(sessionId: String): Result<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getSessionVideos(sessionId)
                Result.success(response)
            } catch (e: Exception) {
                Log.e("HomeRepository", "❌ 영상 조회 실패: ${e.message}")
                Result.failure(e)
            }
        }
    }
}