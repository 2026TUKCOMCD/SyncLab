package com.tukorea.synclab_mobile.ui.screens.home

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.navigation.NavController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tukorea.synclab_mobile.data.model.SessionInfo
import com.tukorea.synclab_mobile.data.model.VideoStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navController: NavController? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit){
        viewModel.loadHomeData()
    }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            (context as? ComponentActivity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel.recentVideos) {
        if (viewModel.recentVideos.any { it.status == "PROCESSING" || it.status == "PENDING" }) {
            while (true) {
                delay(5000)
                try {
                    viewModel.refreshVideoStatus()
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Polling Error: ${e.message}")
                }
                if (viewModel.recentVideos.all { it.status == "COMPLETED" }) break
            }
        }
    }

    // 1. 세션 생성 다이얼로그
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("새로운 세션 생성") },
            text = { Text("새로운 세션을 생성하시겠습니까?\n생성 시 게스트를 위한 임시 코드가 함께 발급됩니다.") },
            confirmButton = {
                Button(onClick = {
                    showCreateDialog = false
                    viewModel.createSession("모바일 세션 ${System.currentTimeMillis() % 1000}")
                }) { Text("생성") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("취소") }
            }
        )
    }

    // 2. 세션 참가 다이얼로그
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("세션 참가") },
            text = {
                Column {
                    Text("공유받은 8자리 초대 코드를 입력하세요.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = {
                            if (it.length <= 8) joinCodeInput = it.uppercase().filter { it.isLetterOrDigit() }
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
                    enabled = joinCodeInput.length >= 6,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SyncLab", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.loadHomeData() }) {
                        Icon(Icons.Default.Refresh, "새로고침", tint = Color(0xFF475569))
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (viewModel.profileImageUrl != null) {
                            AsyncImage(
                                model = viewModel.profileImageUrl,
                                contentDescription = "프로필 사진",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "프로필",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 게스트 표시부
            if (viewModel.isGuest) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                text = "게스트 모드로 접속 중입니다",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!viewModel.isGuest && viewModel.currentSession == null) {
                        ActionCard(
                            title = "세션 생성",
                            icon = Icons.Default.Add,
                            containerColor = Color(0xFF3366FF),
                            contentColor = Color.White,
                            modifier = Modifier.weight(1f),
                            onClick = { showCreateDialog = true }
                        )
                    }
                    if(viewModel.currentSession == null){
                    ActionCard(
                        title = "코드 참가",
                        icon = Icons.Default.Keyboard,
                        containerColor = Color.White,
                        contentColor = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f),
                        onClick = { showJoinDialog = true }
                    )
                    }
                }
            }

            item {
                CurrentSessionCard(viewModel = viewModel, navController = navController)
            }

            // 최근 영상 처리 상태
            item { SectionHeader(title = "최근 영상 처리 상태", showViewAll = true) }

            if (viewModel.recentVideos.isEmpty()) {
                item {
                    Text("표시할 영상 데이터가 없습니다.", color = Color(0xFF94A3B8), fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(viewModel.recentVideos) { video -> VideoStatusItem(video) }
            }

            if (!viewModel.isGuest) {
                item { SectionHeader(title = "과거 세션 기록", showViewAll = false) }

                if (viewModel.sessionHistory.isEmpty()) {
                    item { Text("이전 기록이 없습니다.", color = Color(0xFF94A3B8), fontSize = 13.sp) }
                } else {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(24.dp),
                            shadowElevation = 1.dp
                        ) {
                            Column {
                                val limitedHistory = viewModel.sessionHistory.take(4)

                                limitedHistory.forEachIndexed { index, session ->
                                    SessionHistoryItem(session)
                                    if (index < limitedHistory.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                            color = Color(0xFFF1F5F9)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun ActionCard(title: String, icon: ImageVector, containerColor: Color, contentColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(100.dp).clickable { onClick() },
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(36.dp).background(contentColor.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun CurrentSessionCard(viewModel: HomeViewModel, navController: NavController? = null) {
    val session = viewModel.currentSession
    val clipboardManager = LocalClipboardManager.current
    var selectedMode by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (session != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("현재 참여 중인 세션", fontSize = 12.sp, color = Color(0xFF64748B))
                   if(!viewModel.isGuest){
                    IconButton(onClick = { viewModel.clearSession() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ExitToApp, "나가기", tint = Color(0xFFEF4444))
                    }
                   }
                }
                Text("📌 ${session.sessionName}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.sessionId, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3366FF))
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(session.sessionId)) }) {
                        Icon(Icons.Default.ContentCopy, "복사", modifier = Modifier.size(16.dp), tint = Color(0xFF94A3B8))
                    }
                }

                if (viewModel.currentInviteCode.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFFFFBEC),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VpnKey, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "게스트 초대 코드: ${viewModel.currentInviteCode}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                                // 초 단위를 분으로 환산
                                val remainingMin = viewModel.expiresIn / 60
                                Text(
                                    text = "보안을 위해 ${remainingMin}분 후 코드가 만료됩니다.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD97706).copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            // 모드 선택 탭 (세션 있고 로그인 유저일 때)
            if (session != null && !viewModel.isGuest && navController != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SessionModeTab(
                        title = "📝 편집",
                        isSelected = selectedMode == "edit",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedMode = if (selectedMode == "edit") null else "edit" }
                    )
                    SessionModeTab(
                        title = "📡 라이브",
                        isSelected = selectedMode == "live",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedMode = if (selectedMode == "live") null else "live" }
                    )
                }
                AnimatedVisibility(visible = selectedMode == "live") {
                    val sessionId = session.sessionId
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { navController.navigate("live_camera/$sessionId") },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("카메라", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { navController.navigate("live_controller/$sessionId") },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Text("컨트롤러", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("참여 중인 세션이 없습니다.", fontSize = 14.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
private fun SessionModeTab(title: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(40.dp).clickable { onClick() },
        color = if (isSelected) Color(0xFF3366FF) else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                title,
                color = if (isSelected) Color.White else Color(0xFF64748B),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun VideoStatusItem(video: VideoStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                if (video.status == "PROCESSING" || video.status == "PENDING") {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF3366FF), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.VideoFile, null, tint = Color(0xFF3366FF))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                val statusMsg = when(video.status) {
                    "PROCESSING" -> "프록시 생성 중..."
                    "PENDING" -> "대기 중..."
                    else -> "클라우드 동기화 완료"
                }
                Text(statusMsg, color = Color(0xFF64748B), fontSize = 12.sp)
            }
            if (video.status == "COMPLETED") {
                Surface(color = Color(0xFFDCFCE7), shape = CircleShape) {
                    Text("완료", color = Color(0xFF16A34A), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, showViewAll: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        if (showViewAll) Text("새로고침", fontSize = 12.sp, color = Color(0xFF3366FF), modifier = Modifier.clickable { /* Refresh logic */ })
    }
}

@Composable
fun SessionHistoryItem(session: SessionInfo) {
    Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(Color(0xFFF8F9FA), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Folder, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(session.sessionName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("${session.createdAt} · 참여자 ${session.participantCount}명", fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
    }
}