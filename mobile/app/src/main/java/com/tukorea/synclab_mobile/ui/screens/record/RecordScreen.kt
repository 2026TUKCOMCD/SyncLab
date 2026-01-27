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
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.utils.NtpSyncManager
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONObject

@Composable
fun RecordScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 현재 세션 ID (ViewModel에서 가져옴)
    val sessionId = homeViewModel.currentSession?.sessionId ?: "default_session"

    var isRecording by remember { mutableStateOf(false) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var absoluteStartTime by remember { mutableLongStateOf(0L) }
    var absoluteEndTime by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            isRecording = isRecording,
            onRecordingStarted = { recording ->
                currentRecording = recording
            },
            onRecordingFinished = { file ->
                // 1. JSON 메타데이터 생성 (sessionId 포함)
                val duration = (absoluteEndTime - absoluteStartTime) / 1000.0
                val metadataJson = JSONObject().apply {
                    put("absoluteStartTime", absoluteStartTime)
                    put("absoluteEndTime", absoluteEndTime)
                    put("duration", String.format("%.3f", duration).toDouble())
                    put("fileName", file.name)
                    put("videoName", file.name)
                    put("sessionId", sessionId) // ✅ 업로드 시 S3 폴더명이 됨
                }

                // 2. JSON 파일 저장 (동일한 파일명.json)
                try {
                    val jsonFile = File(context.externalCacheDir, "${file.nameWithoutExtension}.json")
                    jsonFile.writeText(metadataJson.toString(4))
                    Log.d("SyncLab_Metadata", "메타데이터 저장 성공: ${jsonFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("SyncLab_Error", "JSON 저장 실패", e)
                }

                // 3. 업로드 화면으로 단순 이동
                // 💡 파일 경로는 인자로 보내지 않고, UploadScreen에서 최신 파일을 찾도록 함
                navController.navigate(Screen.Upload.route) {
                    popUpTo(Screen.Record.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )

        // 상단 현재 세션 표시
        Text(
            text = "세션: $sessionId",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            style = MaterialTheme.typography.titleMedium
        )

        // 녹화 컨트롤러
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        if (isRecording) {
                            absoluteEndTime = NtpSyncManager.getCurrentNtpTime()
                            currentRecording?.stop()
                            isRecording = false
                        } else {
                            NtpSyncManager.checkAndSync()
                            absoluteStartTime = NtpSyncManager.getCurrentNtpTime()
                            isRecording = true
                        }
                    }
                },
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