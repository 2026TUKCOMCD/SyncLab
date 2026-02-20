package com.tukorea.synclab_mobile.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tukorea.synclab_mobile.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInButton(
    enabled: Boolean,
    onIdTokenReceived: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = {
            coroutineScope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)

                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(
                        request = request,
                        context = context as Activity
                    )

                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                    onIdTokenReceived(googleIdTokenCredential.idToken)

                } catch (e: GetCredentialCancellationException) {
                    onError("로그인이 취소되었습니다.")
                } catch (e: NoCredentialException) {
                    onError("기기에 Google 계정이 없습니다.")
                } catch (e: GetCredentialException) {
                    onError("Google 로그인 실패: ${e.localizedMessage}")
                } catch (e: Exception) {
                    onError("Google 로그인 중 오류 발생: ${e.localizedMessage}")
                }
            }
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            text = "G",
            fontSize = 18.sp,
            color = Color(0xFF4285F4)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Google로 로그인",
            fontSize = 16.sp,
            color = Color.DarkGray
        )
    }
}
