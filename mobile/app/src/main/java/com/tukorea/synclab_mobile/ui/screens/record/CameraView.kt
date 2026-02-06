package com.tukorea.synclab_mobile.ui.screens.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.work.*
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import com.tukorea.synclab_mobile.ui.screens.upload.VideoUploadWorker
import com.tukorea.synclab_mobile.utils.NetworkMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    sessionId: String, // 👈 세션 ID 추가
    onRecordingStarted: (Recording) -> Unit,
    onRecordingFinished: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    val scope = rememberCoroutineScope()

    // 설정 및 네트워크 모니터 인스턴스
    val settingsRepository = remember { SettingsRepository(context) }
    val networkMonitor = remember { NetworkMonitor(context) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val videoCaptureState = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    // 1. 카메라 바인딩 로직
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            videoCaptureState.value = videoCapture

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
            } catch (e: Exception) {
                Log.e("CameraView", "Binding failed", e)
            }
        }, mainExecutor)
    }

    // 2. 녹화 종료 및 업로드 로직 실행
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val videoCapture = videoCaptureState.value ?: return@LaunchedEffect

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
            val name = "SyncLab_$timeStamp"
            val file = File(context.externalCacheDir, "$name.mp4")
            val outputOptions = FileOutputOptions.Builder(file).build()

            val pending = videoCapture.output.prepareRecording(context, outputOptions)

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pending.withAudioEnabled()
            }

            val recording = pending.start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (!event.hasError()) {
                        Log.d("CameraView", "녹화 완료: ${file.absolutePath}")

                        // [핵심 로직] sessionId를 함께 전달하도록 수정
                        scope.launch {
                            handleUploadLogic(context, settingsRepository, networkMonitor, file, sessionId)
                        }

                        onRecordingFinished(file)
                    } else {
                        if (file.exists()) file.delete()
                    }
                }
            }
            onRecordingStarted(recording)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

/**
 * 사용자 설정 및 네트워크 상태에 따른 업로드 흐름 제어 (sessionId 추가)
 */
private suspend fun handleUploadLogic(
    context: Context,
    repository: SettingsRepository,
    networkMonitor: NetworkMonitor,
    videoFile: File,
    sessionId: String // 👈 추가
) {
    val isWifiOnly = repository.isWifiOnlyFlow.first()
    val isAutoUpload = repository.isAutoUploadFlow.first()
    val isWifiNow = networkMonitor.isWifiConnected.first()

    Log.d("UploadFlow", "흐름체크: Wi-Fi전용($isWifiOnly), 현재Wi-Fi($isWifiNow), 자동업로드($isAutoUpload)")

    if (isWifiOnly) {
        if (isWifiNow) {
            if (isAutoUpload) {
                enqueueWork(context, videoFile, requireWifi = true, sessionId = sessionId)
            } else {
                Log.d("UploadFlow", "Wi-Fi 환경이지만 자동 업로드가 꺼져 있어 수동 저장합니다.")
            }
        } else {
            Log.d("UploadFlow", "Wi-Fi 전용 모드이나 현재 LTE이므로 자동 업로드를 수행하지 않습니다.")
        }
    } else {
        if (isAutoUpload) {
            enqueueWork(context, videoFile, requireWifi = false, sessionId = sessionId)
        } else {
            Log.d("UploadFlow", "자동 업로드가 꺼져 있어 수동 저장합니다.")
        }
    }
}

/**
 * WorkManager에 업로드 작업 등록 (session_id 포함)
 */
private fun enqueueWork(context: Context, file: File, requireWifi: Boolean, sessionId: String) { // 👈 sessionId 추가
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(false)
        .setRequiresStorageNotLow(false)
        .build()

    val request = OneTimeWorkRequestBuilder<VideoUploadWorker>()
        .setConstraints(constraints)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(workDataOf(
            "video_path" to file.absolutePath,
            "json_path" to file.absolutePath.replace(".mp4", ".json"),
            "session_id" to sessionId // 👈 핵심: Worker에서 사용하는 키값 "session_id"로 전달
        ))
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "upload_${file.name}",
        ExistingWorkPolicy.REPLACE,
        request
    )
    Log.d("UploadFlow", "WorkManager 재등록 완료 (SID: $sessionId)")
}