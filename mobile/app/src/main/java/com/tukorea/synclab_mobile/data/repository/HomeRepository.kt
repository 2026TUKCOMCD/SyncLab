package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.HomeService
import com.tukorea.synclab_mobile.data.model.SessionCreateRequest
import com.tukorea.synclab_mobile.data.model.SessionJoinRequest
import com.tukorea.synclab_mobile.data.model.SessionResponse
import com.tukorea.synclab_mobile.data.model.VerifyCodeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeRepository {
    // NetworkClient에서 정의한 이름과 일치하도록 수정 (소문자 h)
    private val api: HomeService = NetworkClient.homeService

    /**
     * [PC/관리자 측면] 세션 생성 + 6자리 임시 코드 수신
     */
    suspend fun createNewSession(sessionId: String, userPk: Int): Result<SessionResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = SessionCreateRequest(sessionId = sessionId, userPk = userPk)
                val response = api.createSession(request)
                Log.d("HomeRepository", "🚀 세션 생성 성공 ID: ${response.session.sessionId}, 코드: ${response.tempCode}")
                Result.success(response)
            } catch (e: Exception) {
                Log.e("HomeRepository", "❌ 세션 생성 실패: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * [모바일/참가자 측면] 6자리 숫자로 세션 정보 조회
     */
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

    /**
     * 세션 참가 (기존 세션 ID 직접 입력 방식)
     */
    suspend fun joinSession(sessionId: String): Result<SessionResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = SessionJoinRequest(sessionId = sessionId)
                val response = api.joinSession(request)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 세션별 영상 리스트 조회
     */
    suspend fun fetchSessionVideos(sessionId: String): Result<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getSessionVideos(sessionId)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}