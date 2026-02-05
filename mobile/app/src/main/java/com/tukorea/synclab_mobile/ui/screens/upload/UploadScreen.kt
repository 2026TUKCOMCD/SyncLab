package com.tukorea.synclab_mobile.ui.screens.upload

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.work.*
import com.tukorea.synclab_mobile.data.repository.SettingsRepository
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.utils.VideoFileManager
import com.tukorea.synclab_mobile.worker.VideoUploadWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    navController: NavController,
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }

    // 1. WorkManager 상태 관찰 (VideoUploadWorker가 돌고 있는지 확인)
    // 태그를 지정해두면 관찰하기 쉽습니다.
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagLiveData("VideoUpload")
        .observeAsState(emptyList())

    // 2. 현재 실행 중인 작업의 정보 추출
    val activeWork = workInfos.find { !it.state.isFinished }
    val isUploading = activeWork != null && activeWork.state == WorkInfo.State.RUNNING
    val isWaiting = activeWork != null && activeWork.state == WorkInfo.State.ENQUEUED
    val uploadProgress = activeWork?.progress?.getFloat("progress", 0f) ?: 0f

    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val loadData = {
        videoFiles = VideoFileManager.getVideoFiles(context)
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // 삭제 다이얼로그 (기존과 동일)
    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("영상 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("'${fileToDelete?.name}' 영상을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let {
                        VideoFileManager.deleteFile(it)
                        loadData()
                    }
                    showDeleteDialog = false
                }) { Text("삭제", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("업로드 관리", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 20.dp)
        ) {
            // 업로드 상태 표시 바 (WorkManager 상태 연동)
            if (activeWork != null) {
                UploadStatusCard(
                    isUploading = isUploading,
                    isWaiting = isWaiting,
                    progress = uploadProgress
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("보관함 영상 (${videoFiles.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            if (videoFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("저장된 영상이 없습니다.", color = Color(0xFF94A3B8))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                ) {
                    items(videoFiles) { file ->
                        VideoFileItem(
                            file = file,
                            onDelete = {
                                fileToDelete = file
                                showDeleteDialog = true
                            },
                            onUpload = {
                                scope.launch {
                                    val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                    if (!jsonFile.exists()) {
                                        Toast.makeText(context, "메타데이터가 없어 업로드할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    // WorkManager에 일감 등록
                                    val isWifiOnly = settingsRepository.isWifiOnlyFlow.first()
                                    val constraints = Constraints.Builder()
                                        .setRequiredNetworkType(
                                            if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                                        )
                                        .build()

                                    val uploadWorkRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
                                        .addTag("VideoUpload")
                                        .setConstraints(constraints)
                                        .setInputData(workDataOf(
                                            "video_path" to file.absolutePath,
                                            "json_path" to jsonFile.absolutePath
                                        ))
                                        .build()

                                    WorkManager.getInstance(context).enqueueUniqueWork(
                                        "upload_${file.name}",
                                        ExistingWorkPolicy.KEEP, // 이미 대기 중이면 유지
                                        uploadWorkRequest
                                    )
                                    Toast.makeText(context, "업로드 대기열에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UploadStatusCard(isUploading: Boolean, isWaiting: Boolean, progress: Float) {
    Surface(
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val statusText = when {
                    isUploading -> "서버 업로드 중..."
                    isWaiting -> "네트워크 대기 중 (Wi-Fi 확인)..."
                    else -> "업로드 준비 중..."
                }
                Text(statusText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3366FF))
                Text("${(progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3366FF))
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Color(0xFF3366FF),
                trackColor = Color(0xFFF1F5F9)
            )
        }
    }
}

// VideoFileItem 및 CustomSmallButton 코드는 기존과 동일하므로 유지하시면 됩니다.
@Composable
fun VideoFileItem(file: File, onDelete: () -> Unit, onUpload: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Videocam, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB", fontSize = 11.sp, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSmallButton(icon = Icons.Default.CloudUpload, tint = Color(0xFF3366FF), bgColor = Color(0xFFEEF2FF), onClick = onUpload)
                CustomSmallButton(icon = Icons.Default.Delete, tint = Color(0xFFEF4444), bgColor = Color(0xFFFFEFEF), onClick = onDelete)
            }
        }
    }
}

@Composable
fun CustomSmallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(bgColor).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true, color = tint),
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}