package com.tukorea.synclab_mobile.ui.screens.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.LoginRequest
import com.tukorea.synclab_mobile.data.model.LoginResponse
import com.tukorea.synclab_mobile.utils.AuthManager
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)

    var isProcessing by mutableStateOf(false)
        private set

    fun login(
        userId: String,
        userPw: String,
        onSuccess: (LoginResponse) -> Unit, // 👈 빈 괄호()에서 LoginResponse로 변경!
        onError: (String) -> Unit
    ) {
        if (userId.isBlank() || userPw.isBlank()) {
            onError("아이디와 비밀번호를 모두 입력해주세요.")
            return
        }

        isProcessing = true

        viewModelScope.launch {
            try {
                val response = NetworkClient.authService.login(LoginRequest(userId, userPw))

                if (response.isSuccessful) {
                    val loginBody = response.body()
                    if (loginBody?.status == "success" && loginBody != null) {
                        authManager.saveToken(loginBody.accessToken)


                        onSuccess(loginBody)
                    } else {
                        onError("아이디 또는 비밀번호가 올바르지 않습니다.")
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "인증에 실패했습니다. 정보를 다시 확인해주세요."
                        else -> "서버 에러가 발생했습니다. (코드: ${response.code()})"
                    }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                onError("서버와의 연결이 원활하지 않습니다: ${e.localizedMessage}")
            } finally {
                isProcessing = false
            }
        }
    }
}