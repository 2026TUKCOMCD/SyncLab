package com.tukorea.synclab_mobile.ui.screens.record

import android.content.Context
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
 * 순수 카메라 프리뷰 및 녹화 로직을 담당하는 컴포저블
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

    val previewView = remember { PreviewView(context) }
    val videoCaptureState = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    // 카메라 제공자 초기화 및 바인딩
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Preview 설정
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. 고화질(FHD) 비디오 레코더 설정
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            videoCaptureState.value = videoCapture

            // 3. 후면 카메라 선택
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

    // 외부에서 녹화 시작/중지 상태를 변경할 때의 로직
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val videoCapture = videoCaptureState.value ?: return@LaunchedEffect

            val name = "SyncLab_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(System.currentTimeMillis())}"
            val file = File(context.externalCacheDir, "$name.mp4")
            val outputOptions = FileOutputOptions.Builder(file).build()

            val recording = videoCapture.output.prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(mainExecutor) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (!event.hasError()) {
                            onRecordingFinished(file)
                        }
                    }
                }
            onRecordingStarted(recording)
        }
    }

    // 실제 카메라 화면 렌더링
    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}