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
import com.tukorea.synclab_mobile.Screen
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

    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagLiveData("VideoUpload")
        .observeAsState(emptyList())

    val activeWorks = workInfos.filter { !it.state.isFinished }

    // 이번 세션에서 업로드 완료된 파일명 추적
    val uploadedFileNames = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(workInfos) {
        workInfos.forEach { workInfo ->
            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                val fileName = workInfo.tags.find { it.startsWith("name_") }?.removePrefix("name_")
                if (fileName != null) {
                    uploadedFileNames.value = uploadedFileNames.value + fileName
                }
            }
        }
    }

    val notifiedJobs = remember { mutableSetOf<java.util.UUID>() }
    LaunchedEffect(workInfos) {
        workInfos.forEach { workInfo ->
            if (workInfo.id !in notifiedJobs) {
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val fileName = workInfo.tags.find { it.startsWith("name_") }
                            ?.removePrefix("name_") ?: "파일"
                        Toast.makeText(context, "'$fileName' 업로드 완료", Toast.LENGTH_SHORT).show()
                        notifiedJobs.add(workInfo.id)
                    }
                    WorkInfo.State.FAILED -> {
                        val fileName = workInfo.tags.find { it.startsWith("name_") }
                            ?.removePrefix("name_") ?: "파일"
                        Toast.makeText(context, "'$fileName' 업로드 실패", Toast.LENGTH_LONG).show()
                        notifiedJobs.add(workInfo.id)
                    }
                    else -> {}
                }
            }
        }
    }

    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showNoSessionDialog by remember { mutableStateOf(false) }

    val loadData = {
        videoFiles = VideoFileManager.getVideoFiles(context)
    }

    LaunchedEffect(Unit) { loadData() }

    // 파일 삭제 확인 다이얼로그
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

    // 세션 없음 안내 다이얼로그
    if (showNoSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNoSessionDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("세션 없음", fontWeight = FontWeight.Bold) },
            text = { Text("활성 세션이 없습니다.\n홈 화면에서 세션에 먼저 참여해주세요.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoSessionDialog = false
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Upload.route) { inclusive = false }
                    }
                }) { Text("홈으로 이동", fontWeight = FontWeight.Bold, color = Color(0xFF3366FF)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoSessionDialog = false }) { Text("닫기") }
            }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("저장된 영상이 없습니다.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { navController.navigate(Screen.Record.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3366FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("녹화하러 가기", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(videoFiles) { file ->
                    VideoFileItem(
                        file = file,
                        isUploaded = file.name in uploadedFileNames.value,
                        onDelete = { fileToDelete = file; showDeleteDialog = true },
                        onUpload = {
                            scope.launch {
                                val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                if (!jsonFile.exists()) {
                                    Toast.makeText(context, "메타데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                val currentSessionId = homeViewModel.currentSession?.sessionId
                                if (currentSessionId.isNullOrBlank()) {
                                    Log.e("UploadScreen", "세션 ID 없음")
                                    showNoSessionDialog = true
                                    return@launch
                                }

                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()

                                val uploadWorkRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
                                    .addTag("VideoUpload")
                                    .addTag("name_${file.name}")
                                    .setConstraints(constraints)
                                    .setInputData(workDataOf(
                                        "video_path" to file.absolutePath,
                                        "json_path" to jsonFile.absolutePath,
                                        "session_id" to currentSessionId
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

@Composable
fun ActiveUploadItem(workInfo: WorkInfo, onCancel: () -> Unit) {
    val progress = workInfo.progress.getFloat("progress", 0f)
    val fileName = workInfo.tags.find { it.startsWith("name_") }?.removePrefix("name_") ?: "파일 업로드 중"
    val isRunning = workInfo.state == WorkInfo.State.RUNNING

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
                        text = when (workInfo.state) {
                            WorkInfo.State.RUNNING -> "서버 전송 중..."
                            WorkInfo.State.BLOCKED -> "조건 대기 중 (Wi-Fi 필요)"
                            else -> "업로드 대기 중..."
                        },
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
            if (isRunning) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Color(0xFF3366FF),
                    trackColor = Color(0xFFF1F5F9)
                )
            } else {
                // ENQUEUED / BLOCKED: 불확정 애니메이션으로 대기 중임을 표시
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Color(0xFF3366FF),
                    trackColor = Color(0xFFF1F5F9)
                )
            }
        }
    }
}

@Composable
fun VideoFileItem(
    file: File,
    isUploaded: Boolean,
    onDelete: () -> Unit,
    onUpload: () -> Unit
) {
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
                if (isUploaded) {
                    // 업로드 완료 상태 표시
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "업로드 완료",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    CustomSmallButton(Icons.Default.CloudUpload, Color(0xFF3366FF), Color(0xFFEEF2FF), onUpload)
                }
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
