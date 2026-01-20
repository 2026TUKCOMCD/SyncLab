package com.tukorea.synclab_mobile.ui.screens.record

import android.util.Log
import androidx.navigation.NavController
import androidx.camera.video.Recording
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tukorea.synclab_mobile.Screen
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.utils.NtpSyncManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * 전략 B(선형 보간)가 적용된 녹화 화면
 * 촬영 시작/종료 시점의 Raw 데이터(SystemTime, Offset, RTT)를 모두 수집합니다.
 */
@Composable
fun RecordScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 상태 관리 변수들
    var isRecording by remember { mutableStateOf(false) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }

    // [전략 B 핵심] 촬영 타임스탬프 및 오프셋 데이터 저장용 상태
    var startSys by remember { mutableLongStateOf(0L) }
    var startOff by remember { mutableLongStateOf(0L) }
    var startRtt by remember { mutableLongStateOf(0L) }

    var endSys by remember { mutableLongStateOf(0L) }
    var endOff by remember { mutableLongStateOf(0L) }
    var endRtt by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. 하드웨어 카메라 뷰 배치
        CameraView(
            isRecording = isRecording,
            onRecordingStarted = { recording ->
                currentRecording = recording
            },
            onRecordingFinished = { file ->
                // [중요] 녹화 종료 후 모든 수집된 Raw 데이터를 기반으로 메타데이터 생성
                val metadata = VideoMetadata.create(
                    fileName = file.name,
                    startSys = startSys,
                    endSys = endSys,
                    startOff = startOff,
                    endOff = endOff,
                    startRtt = startRtt,
                    endRtt = endRtt,
                    lastSync = NtpSyncManager.getLastSyncTime(),
                    isSynced = NtpSyncManager.isSynced()
                )

                // JSON 파일로 물리적 저장 (나중에 영상과 함께 업로드)
                try {
                    val jsonFile = File(context.externalCacheDir, "${file.nameWithoutExtension}.json")
                    jsonFile.writeText(metadata.toJson())
                    Log.d("SyncLab_Metadata", "메타데이터 저장 성공: ${jsonFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("SyncLab_Error", "JSON 저장 실패", e)
                }

                // 업로드 대기 화면으로 이동
                navController.navigate(Screen.Upload.route)
            }
        )

        // 2. 상단 오버레이 (상태 표시용)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isRecording) "● REC 1080p" else "READY",
                color = if (isRecording) Color.Red else Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // 3. 하단 녹화 컨트롤러
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        if (isRecording) {
                            // --- 녹화 종료 프로세스 ---
                            // 1. 종료 시점 시스템 시간 즉시 기록
                            endSys = System.currentTimeMillis()

                            // 2. 종료 시점 NTP 오프셋 및 RTT 측정 (네트워크 통신)
                            val syncResult = NtpSyncManager.sync()
                            endOff = syncResult.offset
                            endRtt = syncResult.rtt

                            // 3. 카메라 중지
                            currentRecording?.stop()
                            isRecording = false
                        } else {
                            // --- 녹화 시작 프로세스 ---
                            // 1. 시작 전 NTP 오프셋 및 RTT 측정 (네트워크 통신)
                            val syncResult = NtpSyncManager.sync()
                            startOff = syncResult.offset
                            startRtt = syncResult.rtt

                            // 2. 시작 시점 시스템 시간 기록 (통신 직후)
                            startSys = System.currentTimeMillis()

                            // 3. 카메라 시작
                            isRecording = true
                        }
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.White else Color.Red
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop" else "Record",
                    tint = if (isRecording) Color.Red else Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}