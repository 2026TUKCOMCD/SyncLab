package com.tukorea.synclab_mobile.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tukorea.synclab_mobile.data.model.LoginResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: (LoginResponse) -> Unit,
    onBack: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("회원가입") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.currentStep > 1) {
                            viewModel.goBackToStep1()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 단계 표시
            StepIndicator(currentStep = viewModel.currentStep)

            Spacer(modifier = Modifier.height(32.dp))

            when (viewModel.currentStep) {
                1 -> EmailStep(viewModel)
                2 -> CodeStep(viewModel)
                3 -> InfoStep(viewModel, onSignupSuccess)
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("이메일", "인증", "정보 입력").forEachIndexed { index, label ->
            val step = index + 1
            val isActive = step <= currentStep
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isActive) Color(0xFF3366FF) else Color(0xFFE0E0E0),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "$step. $label",
                    color = if (isActive) Color.White else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EmailStep(viewModel: SignupViewModel) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }

    Text(
        text = "이메일 주소를 입력해주세요",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "인증 코드가 해당 이메일로 발송됩니다.",
        fontSize = 14.sp,
        color = Color.Gray,
        modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("이메일") },
        placeholder = { Text("example@naver.com") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        enabled = !viewModel.isProcessing
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = {
            viewModel.sendCode(
                email = email,
                onSuccess = {
                    Toast.makeText(context, "인증 코드가 발송되었습니다.", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )
        },
        enabled = email.isNotBlank() && !viewModel.isProcessing,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        if (viewModel.isProcessing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text("인증 코드 발송", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CodeStep(viewModel: SignupViewModel) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }

    Text(
        text = "인증 코드를 입력해주세요",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "${viewModel.verifiedEmail}로 발송된 6자리 코드를 입력해주세요.",
        fontSize = 14.sp,
        color = Color.Gray,
        modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = code,
        onValueChange = { if (it.length <= 6) code = it },
        label = { Text("인증 코드") },
        placeholder = { Text("000000") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !viewModel.isProcessing
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = {
            viewModel.verifyCode(
                code = code,
                onSuccess = {
                    Toast.makeText(context, "이메일 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )
        },
        enabled = code.length == 6 && !viewModel.isProcessing,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        if (viewModel.isProcessing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text("인증 확인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(
        onClick = {
            viewModel.resendCode(
                onSuccess = {
                    Toast.makeText(context, "인증 코드가 재발송되었습니다.", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )
        },
        enabled = !viewModel.isProcessing
    ) {
        Text("코드 재발송", color = Color(0xFF3366FF))
    }
}

@Composable
private fun InfoStep(viewModel: SignupViewModel, onSignupSuccess: (LoginResponse) -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }

    Text(
        text = "회원 정보를 입력해주세요",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "${viewModel.verifiedEmail} (인증 완료)",
        fontSize = 14.sp,
        color = Color(0xFF3366FF),
        modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = userName,
        onValueChange = { userName = it },
        label = { Text("이름") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !viewModel.isProcessing
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("비밀번호") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        enabled = !viewModel.isProcessing
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = passwordConfirm,
        onValueChange = { passwordConfirm = it },
        label = { Text("비밀번호 확인") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        isError = passwordConfirm.isNotEmpty() && password != passwordConfirm,
        enabled = !viewModel.isProcessing
    )

    if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
        Text(
            text = "비밀번호가 일치하지 않습니다.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = {
            if (password != passwordConfirm) {
                Toast.makeText(context, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@Button
            }
            viewModel.signup(
                password = password,
                userName = userName,
                onSuccess = { loginBody ->
                    Toast.makeText(context, "회원가입이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                    onSignupSuccess(loginBody)
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )
        },
        enabled = userName.isNotBlank() && password.length >= 4
                && password == passwordConfirm && !viewModel.isProcessing,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        if (viewModel.isProcessing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text("가입하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
