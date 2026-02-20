package com.tukorea.synclab_mobile.ui.screens.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.*
import com.tukorea.synclab_mobile.utils.AuthManager
import kotlinx.coroutines.launch

class SignupViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)

    var isProcessing by mutableStateOf(false)
        private set

    // 현재 단계 (1: 이메일 입력, 2: 코드 입력, 3: 비밀번호/이름 입력)
    var currentStep by mutableStateOf(1)
        private set

    var verifiedEmail by mutableStateOf("")
        private set

    fun sendCode(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || !email.contains("@")) {
            onError("올바른 이메일을 입력해주세요.")
            return
        }

        isProcessing = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.authService.sendCode(SendCodeRequest(email))
                if (response.isSuccessful && response.body()?.status == "success") {
                    verifiedEmail = email
                    currentStep = 2
                    onSuccess()
                } else {
                    val errorMsg = when (response.code()) {
                        409 -> "이미 가입된 이메일입니다."
                        else -> response.body()?.message ?: "인증 코드 발송에 실패했습니다."
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

    fun verifyCode(
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (code.length != 6) {
            onError("6자리 인증 코드를 입력해주세요.")
            return
        }

        isProcessing = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.authService.verifyCode(
                    VerifyCodeRequest(verifiedEmail, code)
                )
                if (response.isSuccessful && response.body()?.status == "success") {
                    currentStep = 3
                    onSuccess()
                } else {
                    val errorMsg = when (response.code()) {
                        400 -> "인증 코드가 일치하지 않습니다."
                        410 -> "인증 코드가 만료되었습니다. 다시 발송해주세요."
                        else -> response.body()?.message ?: "인증에 실패했습니다."
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

    fun signup(
        password: String,
        userName: String,
        onSuccess: (LoginResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        if (password.length < 4) {
            onError("비밀번호는 4자 이상이어야 합니다.")
            return
        }
        if (userName.isBlank()) {
            onError("이름을 입력해주세요.")
            return
        }

        isProcessing = true
        viewModelScope.launch {
            try {
                val response = NetworkClient.authService.signup(
                    SignupRequest(verifiedEmail, password, userName)
                )
                if (response.isSuccessful) {
                    val loginBody = response.body()
                    if (loginBody?.status == "success") {
                        authManager.clearAuthData()
                        authManager.saveToken(loginBody.accessToken)
                        NetworkClient.resetClient()
                        onSuccess(loginBody)
                    } else {
                        onError("회원가입에 실패했습니다.")
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        403 -> "이메일 인증이 완료되지 않았습니다."
                        409 -> "이미 가입된 이메일입니다."
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

    fun goBackToStep1() {
        currentStep = 1
        verifiedEmail = ""
    }

    fun resendCode(onSuccess: () -> Unit, onError: (String) -> Unit) {
        sendCode(verifiedEmail, onSuccess, onError)
    }
}
