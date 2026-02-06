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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel
import com.tukorea.synclab_mobile.R

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

    // [수정] 파라미터 이름을 ViewModel 정의부와 100% 일치시킴
    LaunchedEffect(homeViewModel.isGuest, homeViewModel.userName) {
        homeViewModel.loadHomeData() // 진입 시 서버 데이터 갱신 시도

        viewModel.syncUserInfo(
            isGuestUser = homeViewModel.isGuest,
            userNameFromDb = homeViewModel.userName, // 'name' 대신 명확한 이름 사용
            userEmailFromDb = homeViewModel.userEmail, // 'email' 대신 명확한 이름 사용
            logoResourceId = R.drawable.synclab_logo
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
            item { ProfileSection(uiState.userName, uiState.userEmail, uiState.profileImageUrl) }

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

                    NavigationItem(
                        icon = Icons.Default.Delete,
                        title = "캐시 삭제",
                        subtitle = "현재 ${uiState.cacheSize} 사용"
                    ) { viewModel.clearCache() }
                }
            }

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
        }
    }
}

@Composable
fun ProfileSection(name: String, email: String, imageUrl: Any) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Profile Image",
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFF3F4F6)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(email, fontSize = 14.sp, color = Color.Gray)
            if (name != "게스트") {
                Text("가입일: 2026.02", fontSize = 11.sp, color = Color.LightGray, fontStyle = FontStyle.Italic)
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
    onClick: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemIcon(icon, iconColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Color.Red)
        }
        if (badgeText != null) Text(badgeText, fontSize = 12.sp, color = Color.Gray)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun ItemIcon(icon: ImageVector, tint: Color = Color.Gray) {
    Box(modifier = Modifier.padding(end = 12.dp).size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF9FAFB)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}