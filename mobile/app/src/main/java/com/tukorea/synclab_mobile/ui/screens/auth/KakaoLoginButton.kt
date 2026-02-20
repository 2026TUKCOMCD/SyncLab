package com.tukorea.synclab_mobile.ui.screens.auth

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

@Composable
fun KakaoLoginButton(
    enabled: Boolean,
    onAccessTokenReceived: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    Button(
        onClick = {
            loginWithKakao(context, onAccessTokenReceived, onError)
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500))
    ) {
        Text(
            text = "K",
            fontSize = 18.sp,
            color = Color(0xFF191919)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "카카오로 로그인",
            fontSize = 16.sp,
            color = Color(0xFF191919)
        )
    }
}

private fun loginWithKakao(
    context: Context,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        if (error != null) {
            onError("카카오 로그인 실패: ${error.localizedMessage}")
        } else if (token != null) {
            onSuccess(token.accessToken)
        }
    }

    if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
            if (error != null) {
                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                    onError("로그인이 취소되었습니다.")
                    return@loginWithKakaoTalk
                }
                UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
            } else if (token != null) {
                onSuccess(token.accessToken)
            }
        }
    } else {
        UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
    }
}
