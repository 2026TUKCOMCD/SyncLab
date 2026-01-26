package com.tukorea.synclab_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tukorea.synclab_mobile.ui.navigation.NavGraph // 작성하신 NavGraph 임포트
import com.tukorea.synclab_mobile.ui.theme.SyncLab_MobileTheme
import com.tukorea.synclab_mobile.utils.PermissionHelper
import com.tukorea.synclab_mobile.utils.NtpSyncManager

// 화면 경로 및 정보 정의
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Login : Screen("login", "로그인", Icons.Default.Lock)
    object Home : Screen("home", "메인", Icons.Default.Home)
    object Record : Screen("record", "녹화", Icons.Default.Videocam)
    object Upload : Screen("upload", "업로드", Icons.Default.Share)
    object Settings : Screen("settings", "설정", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SyncLab_MobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScaffold()
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 현재 경로 감시
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 로그인 화면에서는 하단 바를 숨김
    val showBottomBar = currentDestination?.route != Screen.Login.route

    LaunchedEffect(Unit) {
        NtpSyncManager.checkAndSync(isRecording = false)
    }

    var isPermissionGranted by remember {
        mutableStateOf(PermissionHelper.hasAllPermissions(context))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isPermissionGranted = result.values.all { it }
    }

    // 하단 네비게이션에 표시할 항목들
    val bottomNavItems = listOf(Screen.Home, Screen.Record, Screen.Upload, Screen.Settings)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // 🔴 탭 이동 시 스택 꼬임 방지: 홈 화면 위로 다 비움
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // 🔴 핵심 수정: MainActivity에 직접 작성했던 NavHost를 지우고,
        // 데이터 유지와 스택 관리가 구현된 NavGraph.kt의 함수를 호출합니다.
        Box(modifier = Modifier.padding(innerPadding)) {
            NavGraph(
                navController = navController,
                isPermissionGranted = isPermissionGranted,
                onPermissionRequest = {
                    launcher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                }
            )
        }
    }
}