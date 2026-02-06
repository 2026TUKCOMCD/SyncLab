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

    // 현재 세션 ID 가져오기
    val sessionId = homeViewModel.currentSession?.sessionId ?: "default_session"

    var isRecording by remember { mutableStateOf(false) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var absoluteStartTime by remember { mutableLongStateOf(0L) }
    var absoluteEndTime by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(
            isRecording = isRecording,
            sessionId = sessionId, // CameraView에 세션 ID 전달
            onRecordingStarted = { recording ->
                currentRecording = recording
            },
            onRecordingFinished = { file ->
                // 1. 녹화 시간 계산
                val durationSeconds = (absoluteEndTime - absoluteStartTime) / 1000.0

                // 2. [방법 1 적용] VideoMetadata 데이터 클래스 사용
                // @SerializedName 어노테이션에 의해 자동으로 snake_case JSON이 생성됩니다.
                val metadata = VideoMetadata(
                    videoName = file.name,
                    fileName = file.name, // 서버 필수 필드 (file_name)
                    absoluteStartTime = absoluteStartTime,
                    absoluteEndTime = absoluteEndTime,
                    duration = String.format("%.3f", durationSeconds).toDouble(),
                    sessionId = sessionId
                )

                var jsonFile: File? = null
                try {
                    val tempJson = File(context.externalCacheDir, "${file.nameWithoutExtension}.json")
                    // 3. 클래스 내 정의된 toJson() 호출
                    tempJson.writeText(metadata.toJson())
                    jsonFile = tempJson
                    Log.d("SyncLab_Record", "JSON 메타데이터 생성 성공: ${tempJson.absolutePath}")
                } catch (e: Exception) {
                    Log.e("SyncLab_Error", "JSON 저장 실패", e)
                }

                if (jsonFile != null) {
                    // 4. WorkManager 데이터 구성 (session_id 포함 필수)
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
                }

                // 업로드 화면으로 이동
                navController.navigate(Screen.Upload.route) {
                    popUpTo(Screen.Record.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )

        // 상단 UI: 세션 정보
        Text(
            text = "세션: $sessionId",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
            style = MaterialTheme.typography.titleMedium
        )

        // 하단 UI: 녹화 컨트롤 버튼
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        if (isRecording) {
                            // 녹화 중단 시점의 NTP 시간 기록
                            absoluteEndTime = NtpSyncManager.getCurrentNtpTime()
                            currentRecording?.stop()
                            isRecording = false
                        } else {
                            // 녹화 시작 전 시간 동기화 확인
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