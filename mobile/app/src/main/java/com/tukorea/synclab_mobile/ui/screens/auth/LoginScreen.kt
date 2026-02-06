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
import com.tukorea.synclab_mobile.R
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.model.LoginRequest
import com.tukorea.synclab_mobile.data.model.LoginResponse
import com.tukorea.synclab_mobile.utils.AuthManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (LoginResponse) -> Unit, // 👈 LoginResponse 인자를 받도록 정의됨
    onGuestLogin: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { AuthManager(context) }

    var userId by remember { mutableStateOf("") }
    var userPw by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    var showGuestDialog by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }

    // 비회원 초대 코드 다이얼로그 (생략 없이 유지)
    if (showGuestDialog) {
        AlertDialog(
            onDismissRequest = { showGuestDialog = false },
            title = { Text("비회원 입장", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("6자리 숫자 초대 코드를 입력하세요.", fontSize = 14.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) inviteCodeInput = it },
                        label = { Text("6자리 숫자 코드") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = inviteCodeInput.length == 6,
                    onClick = {
                        onGuestLogin(inviteCodeInput)
                        showGuestDialog = false
                    }
                ) { Text("입장하기") }
            },
            dismissButton = { TextButton(onClick = { showGuestDialog = false }) { Text("취소") } }
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

            // ✅ 수정된 로그인 버튼 로직
            Button(
                enabled = !isProcessing,
                onClick = {
                    if (userId.isNotBlank() && userPw.isNotBlank()) {
                        isProcessing = true
                        coroutineScope.launch {
                            try {
                                val response = NetworkClient.authService.login(LoginRequest(userId, userPw))

                                if (response.isSuccessful) {
                                    val loginBody = response.body()
                                    if (loginBody?.status == "success" && loginBody != null) {
                                        // 1. 토큰 저장
                                        authManager.saveToken(loginBody.accessToken)

                                        // 2. [핵심 수정] 빈 괄호가 아니라 loginBody를 넣어줍니다!
                                        // 여기서 넘긴 loginBody가 NavGraph를 거쳐 HomeViewModel로 전달됩니다.
                                        onLoginSuccess(loginBody)

                                    } else {
                                        Toast.makeText(context, "로그인 정보가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "인증 실패 (${response.code()})", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "네트워크 에러: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isProcessing = false
                            }
                        }
                    } else {
                        Toast.makeText(context, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(text = "로그인", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showGuestDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(text = "비회원으로 시작하기", fontSize = 16.sp)
            }
        }
    }
}