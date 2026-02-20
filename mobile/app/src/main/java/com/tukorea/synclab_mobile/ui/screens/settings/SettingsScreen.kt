package com.tukorea.synclab_mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onLoginClick: () -> Unit = {},
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(homeViewModel.isGuest, homeViewModel.userName, homeViewModel.profileImageUrl) {
        viewModel.syncUserInfo(
            isGuestUser = homeViewModel.isGuest,
            userNameFromDb = homeViewModel.userName,
            userEmailFromDb = homeViewModel.userEmail,
            profileImageUrl = homeViewModel.profileImageUrl,
            loginType = homeViewModel.loginType
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 1. 프로필 섹션
            item {
                ProfileSection(
                    name = uiState.userName,
                    email = uiState.userEmail,
                    imageUrl = uiState.profileImageUrl,
                    loginType = uiState.loginType,
                    isGuest = uiState.isGuest,
                    onEditClick = { showNicknameDialog = true }
                )
            }

            // 2. 데이터 및 업로드 관리
            item {
                SectionHeader("데이터 및 업로드 관리", Color(0xFF2563EB))
                Column(modifier = Modifier.background(Color.White)) {
                    ToggleItem(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi 환경에서만 업로드",
                        subtitle = "LTE/5G 데이터 소모 방지",
                        checked = uiState.isWifiOnly
                    ) { viewModel.toggleWifiOnly(it) }

                    ToggleItem(
                        icon = Icons.Default.CloudUpload,
                        title = "촬영 후 자동 업로드",
                        subtitle = "즉시 서버 전송 시작",
                        checked = uiState.isAutoUpload
                    ) { viewModel.toggleAutoUpload(it) }

                    // 캐시 삭제 항목
                    NavigationItem(
                        icon = Icons.Default.Delete,
                        title = "캐시 삭제",
                        subtitle = "현재 ${uiState.cacheSize} 사용 중",
                        subtitleColor = Color.Gray,
                        onClick = {
                            // 클릭 시 팝업 띄우기
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }

            // 3. 계정 관리
            item {
                SectionHeader("계정 관리", Color.Gray)
                if (uiState.isGuest) {
                    NavigationItem(
                        icon = Icons.AutoMirrored.Filled.Login,
                        title = "로그인 및 회원가입",
                        onClick = onLoginClick
                    )
                } else {
                    NavigationItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "로그아웃",
                        textColor = Color.Red,
                        iconColor = Color.Red,
                        onClick = {
                            homeViewModel.clearSession()
                            viewModel.logout(onComplete = onLogoutSuccess)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("캐시 및 영상 데이터 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Text("캐시를 지우면 촬영된 모든 영상 파일(.mp4)과 메타데이터(.json)가 기기에서 완전히 삭제됩니다.\n\n정말로 삭제하시겠습니까?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("전체 삭제", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 닉네임 변경 다이얼로그
    if (showNicknameDialog) {
        var inputName by remember { mutableStateOf(uiState.userName) }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("닉네임 변경", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { if (it.length <= 16) inputName = it },
                    label = { Text("새 닉네임") },
                    singleLine = true,
                    supportingText = { Text("${inputName.length}/16") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = inputName.isNotBlank(),
                    onClick = {
                        viewModel.updateUserName(
                            newName = inputName,
                            onSuccess = { newName ->
                                homeViewModel.userName = newName
                                showNicknameDialog = false
                            },
                            onError = { /* 에러는 ViewModel에서 로그 */ }
                        )
                    }
                ) { Text("변경") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
fun ProfileSection(
    name: String,
    email: String,
    imageUrl: String?,
    loginType: String,
    isGuest: Boolean = false,
    onEditClick: () -> Unit = {}
) {
    val badgeInfo = when (loginType) {
        "google" -> Pair("Google", Color(0xFF4285F4))
        "kakao"  -> Pair("카카오", Color(0xFFFFE812))
        "email"  -> Pair("이메일", Color(0xFF10B981))
        "local"  -> Pair("일반", Color(0xFF6366F1))
        else     -> null
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 이미지
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "프로필 사진",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "기본 프로필",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (badgeInfo != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = badgeInfo.second.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeInfo.first,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeInfo.second.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!isGuest) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "닉네임 변경",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (email.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(email, fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Text(
        text = title, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp
    )
}

@Composable
fun ToggleItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun NavigationItem(
    icon: ImageVector,
    title: String,
    badgeText: String? = null,
    subtitle: String? = null,
    textColor: Color = Color.DarkGray,
    iconColor: Color = Color.Gray,
    subtitleColor: Color = Color.Red,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemIcon(icon, iconColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = subtitleColor)
        }
        if (badgeText != null) Text(badgeText, fontSize = 12.sp, color = Color.Gray)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun ItemIcon(icon: ImageVector, tint: Color = Color.Gray) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF9FAFB)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}