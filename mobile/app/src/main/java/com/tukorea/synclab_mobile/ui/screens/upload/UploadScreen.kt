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
import com.google.gson.Gson
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.data.repository.UploadRepository
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.utils.VideoFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    navController: NavController,
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { UploadRepository() }

    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val loadData = {
        videoFiles = VideoFileManager.getVideoFiles(context)
    }

    LaunchedEffect(Unit) {
        loadData()
    }

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
            // 업로드 상태 표시 바
            if (isUploading) {
                Surface(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("서버 업로드 중...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3366FF))
                            Text("${(uploadProgress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3366FF))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Color(0xFF3366FF),
                            trackColor = Color(0xFFF1F5F9)
                        )
                    }
                }
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
                                    Log.d("UploadScreen", "🔘 업로드 버튼 클릭됨: ${file.name}")
                                    isUploading = true
                                    uploadProgress = 0f

                                    try {
                                        // 1. JSON 메타데이터 읽기 시도
                                        val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                        var metadata: VideoMetadata? = null

                                        if (jsonFile.exists()) {
                                            metadata = try {
                                                Gson().fromJson(jsonFile.readText(), VideoMetadata::class.java)
                                            } catch (e: Exception) {
                                                Log.e("UploadScreen", "❌ JSON 파싱 실패: ${e.message}")
                                                null
                                            }
                                        }

                                        // 2. 메타데이터가 없으면 현재 활성 세션 정보 주입
                                        // 2. 만약 메타데이터가 없거나 세션ID가 없으면 현재 세션ID 강제 할당
                                        if (metadata == null || metadata.sessionId.isNullOrEmpty()) {
                                            val currentSid = homeViewModel.currentSession?.sessionId
                                            if (currentSid != null) {
                                                Log.w("UploadScreen", "⚠️ 메타데이터 없음. 기본값으로 객체 생성 시도")

                                                // ⭐️ 모든 필수 파라미터에 기본값을 채워넣어 생성합니다.
                                                metadata = VideoMetadata(
                                                    videoName = file.name,
                                                    fileName = file.name,
                                                    absoluteStartTime = System.currentTimeMillis(),
                                                    absoluteEndTime = System.currentTimeMillis(),
                                                    duration = 0.0,
                                                    sessionId = currentSid
                                                )
                                            }
                                        }

                                        // 3. 최종 체크 및 전송
                                        if (metadata != null && !metadata.sessionId.isNullOrEmpty()) {
                                            Log.d("UploadScreen", "🚀 전송 시작: SessionId=${metadata.sessionId}")
                                            val result = repository.uploadVideoToS3(file, metadata) {
                                                uploadProgress = it
                                            }

                                            if (result.isSuccess) {
                                                Log.d("UploadScreen", "✅ 업로드 완료!")
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "업로드 완료!", Toast.LENGTH_SHORT).show()
                                                    loadData()
                                                }
                                            } else {
                                                val errorMsg = result.exceptionOrNull()?.message
                                                Log.e("UploadScreen", "❌ 업로드 실패: $errorMsg")
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "오류: $errorMsg", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            Log.e("UploadScreen", "❌ 중단: 세션 ID를 찾을 수 없음")
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "참여 중인 세션이 없습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UploadScreen", "🔥 예기치 못한 에러: ${e.message}")
                                    } finally {
                                        isUploading = false
                                    }
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
                CustomSmallButton(
                    icon = Icons.Default.CloudUpload,
                    tint = Color(0xFF3366FF),
                    bgColor = Color(0xFFEEF2FF),
                    onClick = onUpload
                )
                CustomSmallButton(
                    icon = Icons.Default.Delete,
                    tint = Color(0xFFEF4444),
                    bgColor = Color(0xFFFFEFEF),
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
fun CustomSmallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = tint),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}