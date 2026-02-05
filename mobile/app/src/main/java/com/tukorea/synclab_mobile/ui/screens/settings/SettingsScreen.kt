package com.tukorea.synclab_mobile.ui.screens.settings

import android.content.Context
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 1. DataStore 설정
private val Context.dataStore by preferencesDataStore(name = "user_settings")

// 2. UI 상태 데이터 클래스 (isGuest 추가)
data class SettingsUiState(
    val isGuest: Boolean = true, // 기본값을 게스트로 설정
    val isWifiOnly: Boolean = true,
    val isAutoUpload: Boolean = false,
    val uploadHistorySummary: String = "성공 0 / 실패 0",
    val cacheSize: String = "1.2GB",
    val userName: String = "게스트",
    val userEmail: String = "로그인이 필요합니다",
    val profileImageUrl: String = "https://vinsign.app/resources/avatars/avatar-guest.png"
)

// 3. ViewModel 정의
class SettingsViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
    private val AUTO_UPLOAD_KEY = booleanPreferencesKey("auto_upload")

    init {
        loadSettings()
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            // 실제 로직: 유저 토큰이나 세션을 체크하여 로그인 여부 판단
            val isLoggedIn = false // 테스트를 위해 false(게스트)로 설정

            if (isLoggedIn) {
                _uiState.value = _uiState.value.copy(
                    isGuest = false,
                    userName = "김철수",
                    userEmail = "@chulsoo_kim",
                    profileImageUrl = "https://vinsign.app/resources/avatars/avatar-2.png",
                    uploadHistorySummary = "성공 24 / 실패 1"
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            _uiState.value = _uiState.value.copy(
                isWifiOnly = prefs[WIFI_ONLY_KEY] ?: true,
                isAutoUpload = prefs[AUTO_UPLOAD_KEY] ?: false
            )
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[WIFI_ONLY_KEY] = enabled }
            _uiState.value = _uiState.value.copy(isWifiOnly = enabled)
        }
    }

    fun toggleAutoUpload(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[AUTO_UPLOAD_KEY] = enabled }
            _uiState.value = _uiState.value.copy(isAutoUpload = enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch { _uiState.value = _uiState.value.copy(cacheSize = "0.0MB") }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch { onSuccess() }
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context) as T
}

// 4. 메인 화면
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onLoginClick: () -> Unit = {} // 로그인 페이지 이동용
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context.applicationContext))
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF4F6F8)
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 프로필 섹션
            item { ProfileSection(uiState.userName, uiState.userEmail, uiState.profileImageUrl) }

            // 데이터 및 업로드 관리
            item {
                SectionHeader("데이터 및 업로드 관리", Color(0xFF2563EB))
                Column(modifier = Modifier.background(Color.White)) {
                    ToggleItem(Icons.Default.Wifi, "Wi-Fi 환경에서만 업로드", "LTE/5G 데이터 소모 방지", uiState.isWifiOnly) { viewModel.toggleWifiOnly(it) }
                    ToggleItem(Icons.Default.CloudUpload, "촬영 후 자동 업로드", "즉시 서버 전송 시작", uiState.isAutoUpload) { viewModel.toggleAutoUpload(it) }

                    // 게스트일 경우 히스토리에 '로그인 필요' 표시
                    NavigationItem(
                        icon = Icons.Default.History,
                        title = "업로드 히스토리",
                        badgeText = if (uiState.isGuest) "로그인 필요" else uiState.uploadHistorySummary
                    )

                    NavigationItem(
                        icon = Icons.Default.Delete,
                        title = "임시 파일 및 캐시 삭제",
                        subtitle = "현재 ${uiState.cacheSize} 사용 중",
                        iconColor = Color(0xFFF87171), textColor = Color.Red, onClick = { viewModel.clearCache() }
                    )
                }
            }

            // 계정 관리 (게스트 여부에 따라 다르게 표시)
            item {
                SectionHeader("계정 관리", Color.Gray)
                Column(modifier = Modifier.background(Color.White)) {
                    if (uiState.isGuest) {
                        // 게스트일 때: 로그인 버튼 표시
                        NavigationItem(
                            icon = Icons.AutoMirrored.Filled.Login,
                            title = "로그인 및 회원가입",
                            textColor = Color(0xFF2563EB),
                            onClick = onLoginClick
                        )
                    } else {
                        // 회원일 때: 비밀번호 변경 및 로그아웃 표시
                        NavigationItem(icon = Icons.Default.Lock, title = "비밀번호 변경")
                        NavigationItem(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = "로그아웃",
                            onClick = { viewModel.logout(onLogoutSuccess) }
                        )
                        NavigationItem(
                            icon = Icons.Default.PersonRemove,
                            title = "회원 탈퇴",
                            textColor = Color(0xFFEF4444)
                        )
                    }
                }
            }

            // 하단 카피라이트
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("© 2025 Vinsign Media Corp. All rights reserved.", fontSize = 11.sp, color = Color.LightGray)
                }
            }
        }
    }
}

// --- 공통 컴포넌트 ---

@Composable
fun ProfileSection(name: String, email: String, imageUrl: String) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = imageUrl, contentDescription = null,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFF3F4F6)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(email, fontSize = 14.sp, color = Color.Gray)
            if (name != "게스트") {
                Text("가입일: 2025.01", fontSize = 11.sp, color = Color.LightGray, fontStyle = FontStyle.Italic)
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
fun NavigationItem(icon: ImageVector, title: String, badgeText: String? = null, subtitle: String? = null, textColor: Color = Color.DarkGray, iconColor: Color = Color.Gray, onClick: () -> Unit = {}) {
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