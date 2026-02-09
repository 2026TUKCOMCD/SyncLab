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
    sessionId: String,
    onRecordingStarted: (Recording) -> Unit,
    onRecordingFinished: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    val scope = rememberCoroutineScope()

    val settingsRepository = remember { SettingsRepository(context) }

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

    // 2. 녹화 제어 로직
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

                        scope.launch {
                            handleUploadLogic(context, settingsRepository, file, sessionId)
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


private suspend fun handleUploadLogic(
    context: Context,
    repository: SettingsRepository,
    videoFile: File,
    sessionId: String
) {
    val isWifiOnly = repository.isWifiOnlyFlow.first()
    val isAutoUpload = repository.isAutoUploadFlow.first()

    Log.d("UploadFlow", "설정 확인 -> Wi-Fi전용: $isWifiOnly, 자동업로드: $isAutoUpload")

    if (isAutoUpload) {
        enqueueWork(context, videoFile, requireWifi = isWifiOnly, sessionId = sessionId)
    } else {
        Log.d("UploadFlow", "자동 업로드가 비활성화되어 있습니다. 로컬 저장만 수행합니다.")
    }
}

// requireWifi가 true이면 UNMETERED(와이파이), false이면 CONNECTED(모든 네트워크)
private fun enqueueWork(context: Context, file: File, requireWifi: Boolean, sessionId: String) {
    val networkType = if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(networkType) // 👈 시스템이 와이파이 연결 시점을 자동으로 감지
        .setRequiresStorageNotLow(true)
        .build()

    val request = OneTimeWorkRequestBuilder<VideoUploadWorker>()
        .setConstraints(constraints)
        .setInputData(workDataOf(
            "video_path" to file.absolutePath,
            "json_path" to file.absolutePath.replace(".mp4", ".json"),
            "session_id" to sessionId
        ))
        .addTag("VideoUpload")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "upload_${file.name}",
        ExistingWorkPolicy.REPLACE,
        request
    )

    Log.d("UploadFlow", "WorkManager 예약 완료 - 모드: ${if(requireWifi) "Wi-Fi Only" else "Any Network"}")
}