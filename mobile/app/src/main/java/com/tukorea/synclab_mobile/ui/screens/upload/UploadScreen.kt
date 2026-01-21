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
import com.tukorea.synclab_mobile.data.repository.UploadRepository
import com.tukorea.synclab_mobile.utils.VideoFileManager
import com.tukorea.synclab_mobile.utils.S3Uploader
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { UploadRepository() }

    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var presignedUrlInput by remember { mutableStateOf(S3Uploader.TEST_URL) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    // URL 수정 모드 여부
    var isEditMode by remember { mutableStateOf(false) }

    val loadData = {
        videoFiles = VideoFileManager.getVideoFiles(context)
    }

    LaunchedEffect(Unit) {
        loadData()
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
            // --- [컴팩트 URL 섹션] ---
            if (!isEditMode) {
                // 평상시: 짧은 텍스트로 표시
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { isEditMode = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "URL: $presignedUrlInput",
                        style = TextStyle(fontSize = 11.sp, color = Color.Gray),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("수정", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            } else {
                // 수정 모드: TextField 표시
                TextField(
                    value = presignedUrlInput,
                    onValueChange = { presignedUrlInput = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textStyle = TextStyle(fontSize = 12.sp),
                    label = { Text("Presigned URL 수정", fontSize = 10.sp) },
                    trailingIcon = {
                        IconButton(onClick = { isEditMode = false }) {
                            Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.Green)
                        }
                    },
                    singleLine = true
                )
            }

            if (isUploading) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                )
                Text("진행 중... ${(uploadProgress * 100).toInt()}%", fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "보관함 영상 (${videoFiles.size})", style = MaterialTheme.typography.titleSmall)

            // --- [영상 목록: 공간 최대 확보] ---
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
                                VideoFileManager.deleteFile(file)
                                loadData()
                            },
                            onUpload = {
                                if (presignedUrlInput.isBlank()) {
                                    Toast.makeText(context, "URL을 입력해주세요!", Toast.LENGTH_SHORT).show()
                                    return@VideoFileItem
                                }
                                scope.launch {
                                    isUploading = true
                                    uploadProgress = 0f
                                    val s3Result = repository.uploadVideoToS3(presignedUrlInput, file)
                                    if (s3Result.isSuccess) {
                                        uploadProgress = 0.5f
                                        val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                        if (jsonFile.exists()) {
                                            val serverResult = repository.uploadMetadataToServer(jsonFile, file.nameWithoutExtension)
                                            if (serverResult.isSuccess) {
                                                uploadProgress = 1.0f
                                                Toast.makeText(context, "성공!", Toast.LENGTH_SHORT).show()
                                                VideoFileManager.deleteFile(file)
                                                if(jsonFile.exists()) jsonFile.delete()
                                                loadData()
                                            } else {
                                                Toast.makeText(context, "서버 등록 실패", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            uploadProgress = 1.0f
                                            Toast.makeText(context, "S3 업로드 완료", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "S3 실패", Toast.LENGTH_SHORT).show()
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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB", fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(onClick = onUpload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
            }
        }
    }
}