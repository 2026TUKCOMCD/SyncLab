package com.tukorea.synclab_mobile.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import livekit.org.webrtc.SurfaceViewRenderer

@Composable
fun LiveCameraScreen(
    navController: NavController,
    homeViewModel: HomeViewModel,
    sessionId: String
) {
    val context = LocalContext.current
    val viewModel: LiveCameraViewModel = viewModel()
    // 화면 진입 시 룸 연결 (카메라 비활성, 컨트롤러에 참가자로 즉시 표시)
    LaunchedEffect(sessionId) {
        viewModel.joinRoom(sessionId, context)
    }

    // 화면 이탈 시 룸 연결 해제
    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── 카메라 미리보기 (STREAMING 상태에서만 표시) ───────────────────────
        val videoTrack = viewModel.localVideoTrack
        if (videoTrack != null) {
            val renderer = remember(videoTrack) {
                SurfaceViewRenderer(context).also { r ->
                    try {
                        r.init(viewModel.eglBase.eglBaseContext, null)
                        r.setMirror(false)          // 후방 카메라 → 거울 불필요
                        r.setZOrderMediaOverlay(true)
                        videoTrack.addRenderer(r)
                    } catch (e: Exception) {
                        android.util.Log.e("LiveCamera", "Renderer init: ${e.message}")
                    }
                }
            }
            DisposableEffect(videoTrack) {
                onDispose {
                    try {
                        videoTrack.removeRenderer(renderer)
                        renderer.release()
                    } catch (e: Exception) {
                        android.util.Log.e("LiveCamera", "Renderer release: ${e.message}")
                    }
                }
            }
            AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
        } else {
            // ─── 스트리밍 전: 상태 표시 ────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color(0xFF0F172A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    when (viewModel.connectionState) {
                        ConnectionState.CONNECTING -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text("서버 연결 중...", color = Color.White, fontSize = 16.sp)
                        }
                        ConnectionState.CONNECTED -> Text(
                            "연결 완료 — 스트리밍을 시작하세요",
                            color = Color(0xFF22C55E).copy(alpha = 0.9f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        ConnectionState.ERROR -> Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                viewModel.errorMessage ?: "연결 오류",
                                color = Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        ConnectionState.DISCONNECTED -> Text(
                            "연결이 종료되었습니다",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                        else -> Text(
                            "준비 중...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // ─── 상단: 세션 정보 + LIVE 배지 ──────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "Session: $sessionId",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            if (viewModel.connectionState == ConnectionState.STREAMING) {
                Surface(color = Color.Red, shape = RoundedCornerShape(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // ─── 하단 버튼 ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (viewModel.connectionState) {
                ConnectionState.CONNECTED -> Button(
                    onClick = { viewModel.startCamera(context) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3366FF))
                ) {
                    Icon(Icons.Default.FiberManualRecord, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("스트리밍 시작", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                ConnectionState.STREAMING -> Button(
                    onClick = { viewModel.stopCamera() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("스트리밍 종료", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                ConnectionState.ERROR -> Button(
                    onClick = { viewModel.joinRoom(sessionId, context) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B))
                ) {
                    Text("재연결", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                else -> {}
            }

            TextButton(onClick = { navController.navigateUp() }) {
                Text("뒤로가기", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}
