package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // 팝업 상태 관리
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            // 2초 안에 다시 누르면 앱 종료
            (context as? ComponentActivity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 🔴 영상 처리 상태 자동 갱신 로직 ---
    // recentVideos 리스트에 "PROCESSING" 상태인 영상이 하나라도 있으면 5초마다 새로고침합니다.
    LaunchedEffect(viewModel.recentVideos) {
        val hasProcessingVideo = viewModel.recentVideos.any { it.status == "PROCESSING" }

        if (hasProcessingVideo) {
            while (true) {
                delay(5000) // 5초 대기
                try {
                    viewModel.refreshVideoStatus() // ViewModel에서 서버 데이터 호출
                } catch (e: Exception) {
                    Log.e("HomeScreen", "자동 갱신 중 에러: ${e.message}")
                }

                // 모든 영상이 완료(COMPLETED)되면 루프 종료
                if (viewModel.recentVideos.all { it.status == "COMPLETED" }) {
                    break
                }
            }
        }
    }

    // --- 1. 세션 생성 확인 팝업 ---
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("세션 생성") },
            text = { Text("새로운 세션을 생성하시겠습니까?\n생성 시 팀원들에게 공유할 8자리 코드가 발급됩니다.") },
            confirmButton = {
                Button(onClick = {
                    showCreateDialog = false
                    viewModel.createSession("새로운 프로젝트 세션")
                }) { Text("생성") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("취소") }
            }
        )
    }

    // --- 2. 세션 참가 코드 입력 팝업 ---
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("세션 참가") },
            text = {
                Column {
                    Text("공유받은 8자리 코드를 입력하세요.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = {
                            if (it.length <= 8) joinCodeInput = it.uppercase().filter { char ->
                                char.isLetterOrDigit()
                            }
                        },
                        label = { Text("참가 코드") },
                        placeholder = { Text("예: A1B2C3D4") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = joinCodeInput.length == 8,
                    onClick = {
                        viewModel.joinSession(joinCodeInput)
                        joinCodeInput = ""
                        showJoinDialog = false
                    }
                ) { Text("참가") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("취소") }
            }
        )
    }

    // 메인 UI 레이아웃
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(text = "SyncLab 대시보드", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        item {
            CurrentSessionCard(
                session = viewModel.currentSession,
                onCreate = { showCreateDialog = true },
                onJoin = { showJoinDialog = true },
                onExit = { viewModel.clearSession() }
            )
        }

        item {
            Text(text = "최근 영상 처리 상태", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (viewModel.recentVideos.isEmpty()) {
            item { Text("최근 업로드된 영상이 없습니다.", color = Color.Gray) }
        } else {
            items(viewModel.recentVideos) { video ->
                VideoStatusItem(video)
            }
        }

        item {
            Text(text = "과거 세션 기록", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        items(viewModel.sessionHistory) { session ->
            SessionHistoryItem(session)
        }
    }
}

@Composable
fun CurrentSessionCard(
    session: SessionInfo?,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onExit: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (session != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "현재 세션 정보", fontWeight = FontWeight.Bold)
                if (session != null) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "나가기", tint = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (session != null) {
                Text(text = "📌 ${session.sessionName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "코드: ${session.sessionId}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(session.sessionId))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "복사", modifier = Modifier.size(18.dp))
                    }
                }
                Text(text = "참가 인원: ${session.participantCount}명", fontSize = 14.sp)
            } else {
                Text(text = "참여 중인 세션이 없습니다.")
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCreate) { Text("세션 생성") }
                    OutlinedButton(onClick = onJoin) { Text("코드 참가") }
                }
            }
        }
    }
}

@Composable
fun VideoStatusItem(video: VideoStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = video.fileName, modifier = Modifier.weight(1f))

        val (color, text) = when(video.status) {
            "COMPLETED" -> Color(0xFF4CAF50) to "완료"
            "PROCESSING" -> Color(0xFF2196F3) to "처리중..."
            else -> Color.Gray to "대기"
        }

        Surface(color = color.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
            Text(
                text = text,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SessionHistoryItem(session: SessionInfo) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = session.sessionName, fontWeight = FontWeight.Medium)
                Text(text = session.createdAt, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}