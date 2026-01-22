package com.tukorea.synclab_mobile.ui.screens.upload

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.tukorea.synclab_mobile.data.model.VideoMetadata
import com.tukorea.synclab_mobile.data.repository.UploadRepository
import com.tukorea.synclab_mobile.utils.VideoFileManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { UploadRepository() }

    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    // --- 삭제 확인 다이얼로그 상태 추가 ---
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val loadData = {
        videoFiles = VideoFileManager.getVideoFiles(context)
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // 1. 삭제 확인 다이얼로그 UI
    if (showDeleteDialog && fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("영상 삭제", fontWeight = FontWeight.Bold) },
            text = { Text("'${fileToDelete?.name}' 영상을 삭제하시겠습니까?\n연관된 메타데이터 파일도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete?.let {
                            VideoFileManager.deleteFile(it)
                            loadData()
                        }
                        showDeleteDialog = false
                        fileToDelete = null
                    }
                ) {
                    Text("삭제", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    fileToDelete = null
                }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 관리", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (isUploading) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                )
                Text(
                    text = "업로드 중... ${(uploadProgress * 100).toInt()}%",
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "보관함 영상 (${videoFiles.size})", style = MaterialTheme.typography.titleSmall)

            if (videoFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("저장된 영상이 없습니다.", color = Color.LightGray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    items(videoFiles) { file ->
                        VideoFileItem(
                            file = file,
                            onDelete = {
                                // 바로 삭제하지 않고 다이얼로그 표시
                                fileToDelete = file
                                showDeleteDialog = true
                            },
                            onUpload = {
                                scope.launch {
                                    isUploading = true
                                    uploadProgress = 0f

                                    val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                    if (!jsonFile.exists()) {
                                        Toast.makeText(context, "메타데이터 파일 없음", Toast.LENGTH_SHORT).show()
                                        isUploading = false
                                        return@launch
                                    }

                                    val metadata = try {
                                        Gson().fromJson(jsonFile.readText(), VideoMetadata::class.java)
                                    } catch (e: Exception) { null }

                                    if (metadata == null) {
                                        Toast.makeText(context, "메타데이터 파싱 실패", Toast.LENGTH_SHORT).show()
                                        isUploading = false
                                        return@launch
                                    }

                                    val result = repository.uploadVideoToS3(file, metadata) { progress ->
                                        uploadProgress = progress
                                    }

                                    if (result.isSuccess) {
                                        Toast.makeText(context, "업로드 완료!", Toast.LENGTH_SHORT).show()
                                        // ✅ 요청하신 대로 업로드 성공 시 자동 삭제 코드(VideoFileManager.deleteFile)를 제거함
                                        loadData()
                                    } else {
                                        Toast.makeText(context, "실패: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }

                                    isUploading = false
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
fun VideoFileItem(
    file: File,
    onDelete: () -> Unit,
    onUpload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 파일 정보 섹션
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // 업로드 버튼
            IconButton(onClick = onUpload) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "업로드",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 삭제 버튼
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.Red
                )
            }
        }
    }
}