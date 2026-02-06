package com.tukorea.synclab_mobile.ui.screens.upload

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    // 1. 진행 중인 업로드 작업 관찰
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagLiveData("VideoUpload")
        .observeAsState(emptyList())

    val activeWorks = workInfos.filter { !it.state.isFinished }

    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val loadData = {
        videoFiles = VideoFileManager.getVideoFiles(context)
    }

    LaunchedEffect(Unit) { loadData() }

    // 삭제 다이얼로그
    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("영상 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("'${fileToDelete?.name}' 영상을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let { VideoFileManager.deleteFile(it); loadData() }
                    showDeleteDialog = false
                }) { Text("삭제", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("취소") } }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("업로드 관리", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, null, tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // 섹션 1: 업로드 중인 항목
            if (activeWorks.isNotEmpty()) {
                item {
                    Text("업로드 중인 항목", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3366FF))
                }
                items(activeWorks) { workInfo ->
                    ActiveUploadItem(
                        workInfo = workInfo,
                        onCancel = { WorkManager.getInstance(context).cancelWorkById(workInfo.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // 섹션 2: 보관함
            item {
                Text("보관함 영상 (${videoFiles.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            if (videoFiles.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("저장된 영상이 없습니다.", color = Color(0xFF94A3B8))
                    }
                }
            } else {
                items(videoFiles) { file ->
                    VideoFileItem(
                        file = file,
                        onDelete = { fileToDelete = file; showDeleteDialog = true },
                        onUpload = {
                            scope.launch {
                                val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                if (!jsonFile.exists()) {
                                    Toast.makeText(context, "메타데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                // ✅ [핵심 수정] ViewModel에서 현재 세션 ID 추출
                                val currentSessionId = homeViewModel.currentSession?.sessionId

                                if (currentSessionId.isNullOrBlank()) {
                                    Toast.makeText(context, "세션 정보가 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                                    Log.e("UploadScreen", "❌ 세션 ID 없음: homeViewModel.currentSession is null")
                                    return@launch
                                }

                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()

                                // ✅ [핵심 수정] InputData에 session_id 추가
                                val uploadWorkRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
                                    .addTag("VideoUpload")
                                    .addTag("name_${file.name}")
                                    .setConstraints(constraints)
                                    .setInputData(workDataOf(
                                        "video_path" to file.absolutePath,
                                        "json_path" to jsonFile.absolutePath,
                                        "session_id" to currentSessionId // 👈 데이터 전달!
                                    ))
                                    .build()

                                WorkManager.getInstance(context).enqueueUniqueWork(
                                    "upload_${file.name}",
                                    ExistingWorkPolicy.REPLACE,
                                    uploadWorkRequest
                                )
                                Toast.makeText(context, "업로드를 시작합니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// 하단 컴포넌트들은 기존과 동일 (UI 로직)
@Composable
fun ActiveUploadItem(workInfo: WorkInfo, onCancel: () -> Unit) {
    val progress = workInfo.progress.getFloat("progress", 0f)
    val fileName = workInfo.tags.find { it.startsWith("name_") }?.removePrefix("name_") ?: "파일 업로드 중"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (workInfo.state == WorkInfo.State.RUNNING) "서버 전송 중..." else "대기 중...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3366FF)
                    )
                    Text(fileName, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "취소", tint = Color(0xFFEF4444))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color(0xFF3366FF),
                trackColor = Color(0xFFF1F5F9)
            )
        }
    }
}

@Composable
fun VideoFileItem(file: File, onDelete: () -> Unit, onUpload: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Videocam, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB", fontSize = 11.sp, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSmallButton(Icons.Default.CloudUpload, Color(0xFF3366FF), Color(0xFFEEF2FF), onUpload)
                CustomSmallButton(Icons.Default.Delete, Color(0xFFEF4444), Color(0xFFFFEFEF), onDelete)
            }
        }
    }
}

@Composable
fun CustomSmallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(bgColor).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}