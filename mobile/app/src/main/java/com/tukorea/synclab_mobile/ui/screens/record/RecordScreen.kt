package com.tukorea.synclab_mobile.ui.screens.record

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.tukorea.synclab_mobile.Screen
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.utils.NtpSyncManager
import com.tukorea.synclab_mobile.ui.screens.upload.VideoUploadWorker
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun RecordScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // 현재 방향 감지
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val sessionId = homeViewModel.currentSession?.sessionId ?: "unknown_session"

    var isRecording by remember { mutableStateOf(false) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    // 촬영 시작 시점의 방향을 캡처 (촬영 중 회전해도 시작 방향 기준으로 업로드)
    var recordOrientation by remember { mutableStateOf("portrait") }

    var absoluteStartTime by remember { mutableLongStateOf(0L) }
    var absoluteEndTime by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            isPortrait = isPortrait,
            isRecording = isRecording,
            sessionId = sessionId,
            onRecordingStarted = { recording ->
                currentRecording = recording
            },
            onRecordingFinished = { file ->
                handleRecordingFinished(
                    context = context,
                    file = file,
                    startTime = absoluteStartTime,
                    endTime = absoluteEndTime,
                    sessionId = sessionId,
                    orientation = recordOrientation,
                    onComplete = {
                        navController.navigate(Screen.Upload.route) {
                            popUpTo(Screen.Record.route) { inclusive = true }
                        }
                    }
                )
            }
        )

        // 세션 ID 레이블
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
        ) {
            Text(
                text = "Session ID: $sessionId",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }

        // 가로 모드 표시
        if (!isPortrait) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 16.dp)
            ) {
                Text(
                    text = "가로 모드",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // 방향에 따라 녹화 버튼 위치 변경
        // 세로: 하단 중앙 / 가로: 우측 중앙
        val buttonAlignment = if (isPortrait) Alignment.BottomCenter else Alignment.CenterEnd
        val buttonPadding = if (isPortrait) {
            Modifier.padding(bottom = 64.dp)
        } else {
            Modifier.padding(end = 48.dp)
        }

        Box(modifier = Modifier.align(buttonAlignment).then(buttonPadding)) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (isRecording) {
                            absoluteEndTime = NtpSyncManager.getCurrentNtpTime()
                            currentRecording?.stop()
                            isRecording = false
                        } else {
                            NtpSyncManager.checkAndSync()
                            absoluteStartTime = NtpSyncManager.getCurrentNtpTime()
                            // 촬영 시작 시점의 방향 저장
                            recordOrientation = if (isPortrait) "portrait" else "landscape"
                            isRecording = true
                        }
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(if (isRecording) Color.White else Color.Red, CircleShape)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "녹화 중지" else "녹화 시작",
                    tint = if (isRecording) Color.Red else Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

private fun handleRecordingFinished(
    context: android.content.Context,
    file: File,
    startTime: Long,
    endTime: Long,
    sessionId: String,
    orientation: String,
    onComplete: () -> Unit
) {
    val durationSeconds = (endTime - startTime) / 1000.0
    val metadata = VideoMetadata(
        videoName = file.name,
        fileName = file.name,
        absoluteStartTime = startTime,
        absoluteEndTime = endTime,
        duration = String.format("%.3f", durationSeconds).toDouble(),
        sessionId = sessionId,
        orientation = orientation
    )

    val jsonFile = File(context.externalCacheDir, "${file.nameWithoutExtension}.json")
    try {
        jsonFile.writeText(metadata.toJson())
        Log.d("SyncLab_Record", "JSON 저장 완료: ${jsonFile.absolutePath}")
    } catch (e: Exception) {
        Log.e("SyncLab_Error", "JSON 생성 실패", e)
        return
    }

    val uploadData = workDataOf(
        "video_path" to file.absolutePath,
        "json_path" to jsonFile.absolutePath,
        "session_id" to sessionId
    )

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val uploadWorkRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
        .addTag("VideoUpload")
        .setConstraints(constraints)
        .setInputData(uploadData)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "upload_${file.name}",
        ExistingWorkPolicy.REPLACE,
        uploadWorkRequest
    )

    onComplete()
}
