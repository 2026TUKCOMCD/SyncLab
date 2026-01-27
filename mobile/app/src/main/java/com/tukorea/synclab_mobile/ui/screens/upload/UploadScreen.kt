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
import androidx.compose.material.ripple.rememberRipple
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
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(navController: NavController,
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
                    fileToDelete?.let { VideoFileManager.deleteFile(it); loadData() }
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
                .padding(horizontal = 20.dp) // 여백 약간 조정
        ) {
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
                            onDelete = { fileToDelete = file; showDeleteDialog = true },
                            onUpload = {
                                scope.launch {
                                    isUploading = true
                                    uploadProgress = 0f
                                    val jsonFile = File(file.parent, file.nameWithoutExtension + ".json")
                                    val metadata = try { Gson().fromJson(jsonFile.readText(), VideoMetadata::class.java) } catch (e: Exception) { null }
                                    if (metadata != null) {
                                        repository.uploadVideoToS3(file, metadata) { uploadProgress = it }
                                        loadData()
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
            // 비디오 아이콘
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Videocam, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 파일 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB", fontSize = 11.sp, color = Color.Gray)
            }

            // 버튼 영역 (중첩 문제 및 Deprecated API 해결)
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
            .size(32.dp) // 버튼 크기 고정 (절대 안겹침)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // ✅ 최신 Ripple API 적용 (경고 해결)
                indication = ripple(bounded = true, color = tint),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}