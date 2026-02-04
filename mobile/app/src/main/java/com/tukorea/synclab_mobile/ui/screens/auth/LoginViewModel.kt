package com.tukorea.synclab_mobile.ui.screens.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.LoginRequest
import com.tukorea.synclab_mobile.utils.AuthManager
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    // 1. 토큰 관리를 위한 AuthManager (Context 필요로 인해 AndroidViewModel 사용)
    private val authManager = AuthManager(application)

    // 2. UI 상태 관리 (로딩 여부)
    var isProcessing by mutableStateOf(false)
        private set

    /**
     * 서버 로그인 수행
     */
    fun login(
        userId: String,
        userPw: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // 입력값 검증
        if (userId.isBlank() || userPw.isBlank()) {
            onError("아이디와 비밀번호를 모두 입력해주세요.")
            return
        }

        isProcessing = true

        viewModelScope.launch {
            try {
                // NetworkClient에 등록된 authService 호출
                val response = NetworkClient.authService.login(LoginRequest(userId, userPw))

                if (response.isSuccessful) {
                    val loginBody = response.body()
                    if (loginBody?.status == "success") {
                        // ✅ 핵심: 서버에서 받은 JWT 토큰을 AuthManager에 저장
                        authManager.saveToken(loginBody.accessToken)
                        onSuccess()
                    } else {
                        onError("아이디 또는 비밀번호가 올바르지 않습니다.")
                    }
                } else {
                    // 서버 에러 처리 (401, 500 등)
                    val errorMsg = when (response.code()) {
                        401 -> "인증에 실패했습니다. 정보를 다시 확인해주세요."
                        else -> "서버 에러가 발생했습니다. (코드: ${response.code()})"
                    }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                // 네트워크 연결 실패 등 예외 처리
                onError("서버와의 연결이 원활하지 않습니다: ${e.localizedMessage}")
            } finally {
                isProcessing = false
            }
        }
    }
}