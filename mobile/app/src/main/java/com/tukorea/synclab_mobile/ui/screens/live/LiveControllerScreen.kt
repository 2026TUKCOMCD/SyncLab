package com.tukorea.synclab_mobile.ui.screens.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tukorea.synclab_mobile.data.model.CameraParticipant
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel

// ─── 메인 화면 ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveControllerScreen(
    navController: NavController,
    homeViewModel: HomeViewModel,
    sessionId: String
) {
    val context = LocalContext.current
    val viewModel: LiveControllerViewModel = viewModel()

    LaunchedEffect(sessionId) {
        viewModel.connectAsController(sessionId, context)
        viewModel.loadSessionStatus(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("라이브 컨트롤러", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Session: $sessionId", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSessionStatus(sessionId) }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8F9FA))
        ) {
            // ── 카메라 그리드 영역 (고정 비율) ────────────────────────────────
            CameraControllerContent(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                sessionId = sessionId
            )

            // ── 오버레이 컨트롤 패널 (스크롤 가능) ───────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OverlaySectionTitle("오버레이 컨트롤")

                // ── 스코어보드 카드 ───────────────────────────────────────────
                OverlayCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("스코어보드", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = viewModel.homeTeam,
                                onValueChange = { viewModel.homeTeam = it },
                                label = { Text("홈 팀") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            OutlinedTextField(
                                value = viewModel.awayTeam,
                                onValueChange = { viewModel.awayTeam = it },
                                label = { Text("어웨이 팀") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ScoreEditor(
                                label = viewModel.homeTeam.ifBlank { "HOME" },
                                score = viewModel.homeScore,
                                onMinus = { viewModel.scoreAdd("home", -1, sessionId) },
                                onPlus = { viewModel.scoreAdd("home", 1, sessionId) },
                                modifier = Modifier.weight(1f)
                            )
                            Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            ScoreEditor(
                                label = viewModel.awayTeam.ifBlank { "AWAY" },
                                score = viewModel.awayScore,
                                onMinus = { viewModel.scoreAdd("away", -1, sessionId) },
                                onPlus = { viewModel.scoreAdd("away", 1, sessionId) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.applyTeamNames(sessionId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("팀명 적용", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { viewModel.toggleScoreboard(sessionId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (viewModel.showScoreboard) Color(0xFFDC2626) else Color(0xFF8B5CF6)
                                )
                            ) {
                                Text(
                                    if (viewModel.showScoreboard) "숨기기" else "표시",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // ── 자막 카드 ─────────────────────────────────────────────────
                OverlayCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("자막 (Lower Third)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))

                        OutlinedTextField(
                            value = viewModel.lowerThirdText,
                            onValueChange = { viewModel.lowerThirdText = it },
                            label = { Text("자막 텍스트") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        OutlinedTextField(
                            value = viewModel.lowerThirdSubText,
                            onValueChange = { viewModel.lowerThirdSubText = it },
                            label = { Text("부제목 (선택)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.applyLowerThird(sessionId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("적용", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { viewModel.toggleLowerThird(sessionId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (viewModel.showLowerThird) Color(0xFFDC2626) else Color(0xFF16A34A)
                                )
                            ) {
                                Text(
                                    if (viewModel.showLowerThird) "숨기기" else "표시",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── 하단 버튼 영역 ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleLive(sessionId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.isLive) Color(0xFF16A34A) else Color(0xFF3366FF)
                    )
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = if (viewModel.isLive) Color(0xFFDCFCE7) else Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (viewModel.isLive) "● LIVE 중 — 탭하면 방송 종료" else "방송 시작 (목록에 공개)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (viewModel.isLive) viewModel.toggleLive(sessionId)
                        navController.navigateUp()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Text("나가기", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── 카메라 콘텐츠 ────────────────────────────────────────────────────────────

@Composable
private fun CameraControllerContent(
    modifier: Modifier,
    viewModel: LiveControllerViewModel,
    sessionId: String
) {
    Column(modifier = modifier.fillMaxWidth()) {

        if (!viewModel.isConnected) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF3366FF)
            )
        }

        viewModel.errorMessage?.let { msg ->
            Surface(
                color = Color(0xFFFEE2E2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    msg,
                    color = Color(0xFFDC2626),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        viewModel.activeCamera?.let { active ->
            Surface(
                color = Color(0xFFFEF3C7),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "현재 메인 화면: $active",
                        color = Color(0xFF92400E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (viewModel.cameras.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!viewModel.isConnected) {
                        CircularProgressIndicator(color = Color(0xFF3366FF))
                        Text("카메라 연결 대기 중...", color = Color(0xFF64748B), fontSize = 14.sp)
                    } else {
                        Icon(
                            Icons.Default.Videocam,
                            null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(48.dp)
                        )
                        Text("연결된 카메라가 없습니다", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        Text(
                            "카메라 역할 기기에서 스트리밍을 시작해주세요",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.cameras) { camera ->
                    CameraCard(
                        camera = camera,
                        isActive = camera.id == viewModel.activeCamera,
                        onClick = { viewModel.switchCamera(sessionId, camera.id) }
                    )
                }
                if (viewModel.cameras.isNotEmpty() && viewModel.cameras.none { it.isStreaming }) {
                    item {
                        Surface(
                            color = Color(0xFF1E3A5F),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "카메라가 연결됨 — 카메라 기기에서 스트리밍을 시작해주세요",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 오버레이 공통 컴포넌트 ───────────────────────────────────────────────────

@Composable
private fun OverlaySectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color(0xFF1E293B)
    )
}

@Composable
private fun OverlayCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFFF8FAFF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
private fun ScoreEditor(
    label: String,
    score: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(label, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
            }
            Text(
                score.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── 카메라 카드 ──────────────────────────────────────────────────────────────

@Composable
private fun CameraCard(
    camera: CameraParticipant,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val clickable = camera.isStreaming
    Surface(
        modifier = Modifier
            .aspectRatio(4f / 3f)
            .clickable(enabled = clickable) { onClick() },
        color = if (clickable) Color(0xFF1E293B) else Color(0xFF0F1A2A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isActive) 3.dp else 1.dp,
            color = when {
                isActive -> Color.Red
                clickable -> Color(0xFF334155)
                else -> Color(0xFF1E293B)
            }
        ),
        shadowElevation = if (isActive) 6.dp else 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Videocam,
                    null,
                    tint = Color.White.copy(
                        alpha = when {
                            isActive -> 0.7f
                            clickable -> 0.4f
                            else -> 0.15f
                        }
                    ),
                    modifier = Modifier.size(36.dp)
                )
            }

            Surface(
                color = when {
                    isActive -> Color.Red
                    clickable -> Color(0xFF16A34A)
                    else -> Color(0xFF334155)
                },
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(Icons.Default.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(6.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = when {
                            isActive -> "LIVE"
                            clickable -> "ON AIR"
                            else -> "대기"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    camera.name,
                    color = Color.White.copy(alpha = if (clickable) 1f else 0.45f),
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
