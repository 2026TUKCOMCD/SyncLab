package com.tukorea.synclab_mobile.ui.screens.record

import android.app.Activity
import android.content.res.Configuration
import android.util.Log
import android.view.WindowManager
import androidx.camera.video.Recording
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.work.*
import com.tukorea.synclab_mobile.Screen
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.ui.screens.upload.VideoUploadWorker
import com.tukorea.synclab_mobile.utils.NtpSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun RecordScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val sessionId = homeViewModel.currentSession?.sessionId ?: "unknown_session"

    var isRecording by remember { mutableStateOf(false) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var recordOrientation by remember { mutableStateOf("portrait") }
    var absoluteStartTime by remember { mutableLongStateOf(0L) }
    var absoluteEndTime by remember { mutableLongStateOf(0L) }

    // 녹화 경과 시간 타이머
    var recordingSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (true) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    // 녹화 중 세션 배지 자동 숨김
    var showSessionBadge by remember { mutableStateOf(true) }
    LaunchedEffect(isRecording) {
        showSessionBadge = !isRecording
    }

    // 녹화 완료 후 선택 다이얼로그
    var showFinishedDialog by remember { mutableStateOf(false) }
    var finishedFile by remember { mutableStateOf<File?>(null) }
    var finishedRotation by remember { mutableIntStateOf(0) }

    // 녹화 중 화면 꺼짐 방지
    val activity = context as? Activity
    DisposableEffect(isRecording) {
        if (isRecording) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // REC 점 깜빡임 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val recDotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recDot"
    )

    // 녹화 완료 선택 다이얼로그
    if (showFinishedDialog && finishedFile != null) {
        val capturedFile = finishedFile!!
        val capturedEndTime = absoluteEndTime
        val capturedRotation = finishedRotation
        AlertDialog(
            onDismissRequest = { /* 의도치 않은 닫기 방지 */ },
            shape = RoundedCornerShape(20.dp),
            title = { Text("녹화 완료", fontWeight = FontWeight.Bold) },
            text = { Text("영상이 저장되었습니다.\n어떻게 하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishedDialog = false
                    handleRecordingFinished(
                        context = context,
                        file = capturedFile,
                        startTime = absoluteStartTime,
                        endTime = capturedEndTime,
                        sessionId = sessionId,
                        orientation = recordOrientation,
                        rotation = capturedRotation,
                        onComplete = {
                            navController.navigate(Screen.Upload.route) {
                                popUpTo(Screen.Record.route) { inclusive = true }
                            }
                        }
                    )
                }) {
                    Text("업로드 화면으로", fontWeight = FontWeight.Bold, color = Color(0xFF3366FF))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinishedDialog = false
                    handleRecordingFinished(
                        context = context,
                        file = capturedFile,
                        startTime = absoluteStartTime,
                        endTime = capturedEndTime,
                        sessionId = sessionId,
                        orientation = recordOrientation,
                        rotation = capturedRotation,
                        onComplete = {}
                    )
                }) {
                    Text("계속 녹화")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            isPortrait = isPortrait,
            isRecording = isRecording,
            sessionId = sessionId,
            onRecordingStarted = { recording -> currentRecording = recording },
            onRecordingFinished = { file, rotationDegrees ->
                finishedFile = file
                finishedRotation = rotationDegrees
                showFinishedDialog = true
            }
        )

        // 세션 ID 배지 (녹화 중 숨김, 탭하면 숨기기 가능)
        if (showSessionBadge) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .clickable { showSessionBadge = false }
            ) {
                Text(
                    text = "Session ID: $sessionId",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // REC 표시 + 경과 타이머 (녹화 중에만 표시)
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(recDotAlpha)
                        .background(Color.Red, CircleShape)
                )
                Text(
                    text = "REC  %02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    rotation: Int,
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
        orientation = orientation,
        rotation = rotation
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
        .addTag("name_${file.name}")
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
