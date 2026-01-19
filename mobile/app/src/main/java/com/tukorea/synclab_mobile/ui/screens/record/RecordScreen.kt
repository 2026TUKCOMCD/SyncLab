package com.tukorea.synclab_mobile.ui.screens.record

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
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 녹화 화면의 전체 레이아웃 및 상태 관리를 담당하는 컴포저블
 */
@Composable
fun RecordScreen(navController: NavController) {
    var isRecording by remember { mutableStateOf(false) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 하드웨어 카메라 뷰 배치
        CameraView(
            isRecording = isRecording,
            onRecordingStarted = { recording ->
                currentRecording = recording
            },
            onRecordingFinished = { file ->
                lastSavedFile = file
                isRecording = false
            }
        )

        // 2. 상단 오버레이 (NTP 시간 또는 상태 표시용)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isRecording) "녹화 중..." else "촬영 준비 완료",
                color = if (isRecording) Color.Red else Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // 3. 하단 녹화 컨트롤러
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 50.dp)
        ) {
            LargeFloatingActionButton(
                onClick = {
                    if (isRecording) {
                        currentRecording?.stop()
                        currentRecording = null
                    } else {
                        isRecording = true
                    }
                },
                containerColor = if (isRecording) Color.White else Color.Red,
                contentColor = if (isRecording) Color.Red else Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // 4. 저장 완료 알림 (간단한 표시)
        lastSavedFile?.let {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            ) {
                Text(text = "영상이 저장되었습니다: ${it.name}")
            }
        }
    }
}