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
import androidx.work.*
import com.tukorea.synclab_mobile.Screen
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.utils.NtpSyncManager
import com.tukorea.synclab_mobile.ui.screens.upload.VideoUploadWorker // 경로 확인!
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject

@Composable
fun RecordScreen(navController: NavController, homeViewModel: HomeViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                val duration = (absoluteEndTime - absoluteStartTime) / 1000.0
                val metadataJson = JSONObject().apply {
                    put("absoluteStartTime", absoluteStartTime)
                    put("absoluteEndTime", absoluteEndTime)
                    put("duration", String.format("%.3f", duration).toDouble())
                    put("fileName", file.name)
                    put("videoName", file.name)
                    put("sessionId", sessionId)
                }

                var jsonFile: File? = null
                try {
                    val tempJson = File(context.externalCacheDir, "${file.nameWithoutExtension}.json")
                    tempJson.writeText(metadataJson.toString(4))
                    jsonFile = tempJson
                } catch (e: Exception) {
                    Log.e("SyncLab_Error", "JSON 저장 실패", e)
                }

                if (jsonFile != null) {
                    val uploadData = workDataOf(
                        "video_path" to file.absolutePath,
                        "json_path" to jsonFile.absolutePath
                    )

                    // [수정 포인트 1] 제약 조건 설정 (네트워크 연결)
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    // [수정 포인트 2] MIN_BACKOFF_MILLIS 참조 및 태그 추가
                    val uploadWorkRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
                        .addTag("VideoUpload") // 중요: UploadScreen에서 이 태그로 진행률을 찾음
                        .setConstraints(constraints)
                        .setInputData(uploadData)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            WorkRequest.MIN_BACKOFF_MILLIS, // 클래스 이름 명시 필요
                            TimeUnit.MILLISECONDS
                        )
                        .build()

                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "upload_${file.name}",
                        ExistingWorkPolicy.REPLACE,
                        uploadWorkRequest
                    )
                }

                navController.navigate(Screen.Upload.route) {
                    popUpTo(Screen.Record.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )

        // 세션 정보 및 녹화 컨트롤러 (기존과 동일)
        Text(
            text = "세션: $sessionId",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            style = MaterialTheme.typography.titleMedium
        )

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