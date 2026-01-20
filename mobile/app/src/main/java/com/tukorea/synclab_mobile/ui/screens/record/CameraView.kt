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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

/**
 * SyncLab 전용 카메라 뷰 - VideoFileManager와 연동되도록 파일 생성 로직 최적화
 */
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    isRecording: Boolean,
    onRecordingStarted: (Recording) -> Unit,
    onRecordingFinished: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    // 성능 최적화: 뷰 재생성 방지
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val videoCaptureState = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. 프리뷰 설정
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // 2. 고화질(FHD) 레코더 설정
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            videoCaptureState.value = videoCapture

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Log.e("CameraView", "Binding failed", e)
            }
        }, mainExecutor)
    }

    // 녹화 실행 로직
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val videoCapture = videoCaptureState.value ?: return@LaunchedEffect

            // VideoFileManager가 검색할 수 있도록 "SyncLab_" 접두사 강제 적용
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
            val name = "SyncLab_$timeStamp"

            // externalCacheDir를 사용하여 앱 삭제 시 함께 정리되도록 설정
            val file = File(context.externalCacheDir, "$name.mp4")
            val outputOptions = FileOutputOptions.Builder(file).build()

            val audioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val pending = videoCapture.output.prepareRecording(context, outputOptions)
            if (audioPermission) {
                try {
                    pending.withAudioEnabled()
                } catch (e: SecurityException) {
                    Log.e("CameraView", "Audio permission denied")
                }
            }

            val recording = pending.start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (!event.hasError()) {
                        Log.d("CameraView", "녹화 완료 및 저장: ${file.absolutePath} (크기: ${file.length()} bytes)")
                        // 파일이 완전히 기록된 후 콜백 호출
                        onRecordingFinished(file)
                    } else {
                        Log.e("CameraView", "녹화 중 에러 발생: ${event.error}")
                        // 에러 발생 시 생성된 불완전한 파일 삭제 시도
                        if (file.exists()) file.delete()
                    }
                }
            }
            onRecordingStarted(recording)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}