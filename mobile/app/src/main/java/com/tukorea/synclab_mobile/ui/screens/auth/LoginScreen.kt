package com.tukorea.synclab_mobile.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    // MainActivity의 NavGraph에서 넘겨받는 성공 콜백
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current

    // 입력값을 저장할 상태 변수
    var userId by remember { mutableStateOf("") }
    var userPw by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
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
            // 로고 또는 앱 이름
            Text(
                text = "SyncLab",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 아이디 입력창
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("아이디") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(15.dp))

            // 비밀번호 입력창
            OutlinedTextField(
                value = userPw,
                onValueChange = { userPw = it },
                label = { Text("비밀번호") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(30.dp))

            // 로그인 버튼
            Button(
                enabled = !isProcessing, // 🔴 처리 중일 때는 버튼 비활성화
                onClick = {
                    if (userId == "111" && userPw == "111") {
                        isProcessing = true // 상태 변경
                        onLoginSuccess()
                    } else {
                        Toast.makeText(context, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(text = "로그인", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // 회원가입 버튼 (추후 구현용)
            TextButton(onClick = {
                Toast.makeText(context, "회원가입 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
            }) {
                Text(text = "계정이 없으신가요? 회원가입", color = Color.Gray)
            }

        }
    }
}