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

    // [수정] 불필요한 Rtt 변수 제거 및 DB 필드 위주로 재편
    var startSys by remember { mutableLongStateOf(0L) }
    var startOff by remember { mutableLongStateOf(0L) }
    var endSys by remember { mutableLongStateOf(0L) }
    var endOff by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            isRecording = isRecording,
            onRecordingStarted = { recording ->
                currentRecording = recording
            },
            onRecordingFinished = { file ->
                // [수정] 슬림해진 VideoMetadata 모델에 맞춰 데이터 생성
                val metadata = VideoMetadata.create(
                    fileName = file.name,
                    videoName = "SyncLab_Video_${System.currentTimeMillis()}", // 기본 영상 이름 생성
                    startSys = startSys,
                    startOff = startOff,
                    endSys = endSys,
                    endOff = endOff
                )

                // JSON 파일로 저장
                try {
                    val jsonFile = File(context.externalCacheDir, "${file.nameWithoutExtension}.json")
                    jsonFile.writeText(metadata.toJson())
                    Log.d("SyncLab_Metadata", "메타데이터 저장 성공: ${jsonFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("SyncLab_Error", "JSON 저장 실패", e)
                }

                navController.navigate(Screen.Upload.route) {
                    // 현재 RecordScreen을 스택에서 비우고 Upload로 이동 (연속 촬영 시 스택 쌓임 방지)
                    popUpTo(Screen.Record.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )

        // 하단 녹화 컨트롤러 (Rtt 로직 제거)
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        if (isRecording) {
                            // --- 녹화 종료 프로세스 ---
                            endSys = System.currentTimeMillis()
                            val syncResult = NtpSyncManager.sync()
                            endOff = syncResult.offset // Rtt 할당 제거

                            currentRecording?.stop()
                            isRecording = false
                        } else {
                            // --- 녹화 시작 프로세스 ---
                            val syncResult = NtpSyncManager.sync()
                            startOff = syncResult.offset // Rtt 할당 제거
                            startSys = System.currentTimeMillis()

                            isRecording = true
                        }
                    }
                },
                // ... (이하 디자인 코드는 기존과 동일)
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.White else Color.Red
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isRecording) Color.Red else Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}