package com.tukorea.synclab_mobile.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tukorea.synclab_mobile.R
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.LoginRequest
import com.tukorea.synclab_mobile.data.model.LoginResponse
import com.tukorea.synclab_mobile.utils.AuthManager
import com.tukorea.synclab_mobile.ui.screens.auth.LoginViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (LoginResponse) -> Unit,
    onGuestLogin: (String) -> Unit,
    onSignupClick: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authManager = remember { AuthManager(context) }

    var userId by remember { mutableStateOf("") }
    var userPw by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    var showGuestDialog by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }


    if (showGuestDialog) {
        AlertDialog(
            onDismissRequest = { showGuestDialog = false },
            title = { Text("비회원 입장", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("8자리 초대 코드를 입력하세요.", fontSize = 14.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = {
                            if (it.length <= 8) inviteCodeInput = it
                        },
                        label = { Text("8자리 초대 코드") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        enabled = !viewModel.isProcessing
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = inviteCodeInput.length == 8 && !viewModel.isProcessing,
                    onClick = {
                        viewModel.guestLogin(
                            inviteCode = inviteCodeInput,
                            onSuccess = {
                                onGuestLogin(inviteCodeInput)
                                showGuestDialog=false
                            },
                            onError = {errorMsg ->
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                ) {
                    if(viewModel.isProcessing){
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }else{
                    Text("입장하기")
                }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestDialog = false }) { Text("취소") }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.synclab_logo),
                contentDescription = "SyncLab Logo",
                modifier = Modifier.fillMaxWidth(0.85f).height(80.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("아이디") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userPw,
                onValueChange = { userPw = it },
                label = { Text("비밀번호") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                enabled = !viewModel.isProcessing,
                onClick = {
                    if (userId.isNotBlank() && userPw.isNotBlank()) {
                        viewModel.login(
                            userId = userId,
                            userPw = userPw,
                            onSuccess = { loginBody ->
                                onLoginSuccess(loginBody)
                            },
                            onError = { errorMsg ->
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (viewModel.isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(text = "로그인", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onSignupClick) {
                Text("이메일로 회원가입", color = Color(0xFF3366FF), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray)
                Text("  또는  ", color = Color.Gray, fontSize = 14.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            GoogleSignInButton(
                enabled = !viewModel.isProcessing,
                onIdTokenReceived = { idToken ->
                    viewModel.googleLogin(
                        idToken = idToken,
                        onSuccess = { loginBody -> onLoginSuccess(loginBody) },
                        onError = { errorMsg ->
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            KakaoLoginButton(
                enabled = !viewModel.isProcessing,
                onAccessTokenReceived = { accessToken ->
                    viewModel.kakaoLogin(
                        accessToken = accessToken,
                        onSuccess = { loginBody -> onLoginSuccess(loginBody) },
                        onError = { errorMsg ->
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    inviteCodeInput = ""
                    showGuestDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(text = "비회원으로 시작하기", fontSize = 16.sp)
            }
        }
    }
}