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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGuestLogin: (String) -> Unit // 6자리 초대 코드를 전달함
) {
    val context = LocalContext.current

    var userId by remember { mutableStateOf("") }
    var userPw by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // --- 비회원용 상태 변수 ---
    var showGuestDialog by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }

    // 팝업창(AlertDialog): 비회원 6자리 숫자 코드 입력
    if (showGuestDialog) {
        AlertDialog(
            onDismissRequest = {
                showGuestDialog = false
                isProcessing = false
            },
            title = { Text("비회원 입장", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "전달받은 6자리 숫자 초대 코드를 입력하세요.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { input ->
                            // ✅ 6자리 제한 및 숫자만 허용 (HomeViewModel 규격과 일치)
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                inviteCodeInput = input
                            }
                        },
                        label = { Text("6자리 숫자 코드") },
                        placeholder = { Text("예: 123456") },
                        singleLine = true,
                        // ✅ 숫자 키패드 강제
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    Text(
                        text = "코드 발급 후 5분이 지나면 사용할 수 없습니다.",
                        fontSize = 11.sp,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    // ✅ 6자리가 정확히 입력되었을 때만 활성화
                    enabled = inviteCodeInput.length == 6,
                    onClick = {
                        isProcessing = true
                        onGuestLogin(inviteCodeInput)
                        showGuestDialog = false
                    }
                ) {
                    Text("입장하기")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGuestDialog = false
                    isProcessing = false
                    inviteCodeInput = "" // 입력값 초기화
                }) {
                    Text("취소")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. SyncLab 로고
            Image(
                painter = painterResource(id = R.drawable.synclab_logo),
                contentDescription = "SyncLab Logo",
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(80.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 아이디 입력 필드
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("아이디") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 비밀번호 입력 필드
            OutlinedTextField(
                value = userPw,
                onValueChange = { userPw = it },
                label = { Text("비밀번호") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 메인 로그인 버튼 (회원 전용)
            Button(
                enabled = !isProcessing,
                onClick = {
                    if (userId == "111" && userPw == "111") {
                        isProcessing = true
                        onLoginSuccess()
                    } else {
                        Toast.makeText(context, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isProcessing && !showGuestDialog) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "로그인", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 비회원 로그인 버튼 (클릭 시 팝업 노출)
            OutlinedButton(
                enabled = !isProcessing,
                onClick = {
                    showGuestDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "비회원으로 시작하기",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 회원가입 안내
            TextButton(onClick = {
                Toast.makeText(context, "회원가입 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "계정이 없으신가요? ", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        text = "회원가입",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}