package com.tukorea.synclab_mobile.ui.screens.upload

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tukorea.synclab_mobile.utils.VideoFileManager
import java.io.File

/**
 * 저장된 영상 목록을 보여주고 업로드 및 삭제를 관리하는 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen() {
    val context = LocalContext.current
    val TAG = "UI_LIFECYCLE"

    // 1. 상태를 초기값 없이 선언
    var videoFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var cacheSize by remember { mutableStateOf(0.0) }

    // 2. [핵심] 화면이 보일 때마다 강제로 데이터 로드 및 로그 출력
    LaunchedEffect(Unit) {
        Log.e(TAG, ">>> UploadScreen 진입함 - 데이터 로드 시작")
        videoFiles = VideoFileManager.getVideoFiles(context)
        cacheSize = VideoFileManager.getCacheSizeMb(context)
        Log.e(TAG, ">>> 검색된 파일 개수: ${videoFiles.size}")
    }

    // 목록 새로고침 함수
    val refreshList = {
        videoFiles = VideoFileManager.getVideoFiles(context)
        cacheSize = VideoFileManager.getCacheSizeMb(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("업로드 관리", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 요약 정보 카드
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "현재 저장된 영상: ${videoFiles.size}개", fontSize = 16.sp)
                        Text(
                            text = "사용 중인 용량: ${String.format("%.2f", cacheSize)} MB",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 영상 목록 리스트
            if (videoFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("저장된 영상이 없습니다.", color = Color.Gray)
                        Button(onClick = { refreshList() }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("새로고침")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(videoFiles) { file ->
                        VideoFileItem(
                            file = file,
                            onDelete = {
                                VideoFileManager.deleteFile(file)
                                refreshList()
                            },
                            onUpload = {
                                // 업로드 로직
                            }
                        )
                    }
                }
            }

            if (videoFiles.isNotEmpty()) {
                Button(
                    onClick = {
                        VideoFileManager.clearAllCache(context)
                        refreshList()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("모든 캐시 데이터 삭제 (용량 확보)")
                }
            }
        }
    }
}

@Composable
fun VideoFileItem(file: File, onDelete: () -> Unit, onUpload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    text = "${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onUpload) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        }
    }
}