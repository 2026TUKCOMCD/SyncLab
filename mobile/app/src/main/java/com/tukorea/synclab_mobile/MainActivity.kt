package com.tukorea.synclab_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tukorea.synclab_mobile.ui.components.PermissionRequestScreen
import com.tukorea.synclab_mobile.ui.components.PlaceholderScreen
import com.tukorea.synclab_mobile.ui.screens.auth.LoginScreen // 추가됨
import com.tukorea.synclab_mobile.ui.screens.home.HomeScreen
import com.tukorea.synclab_mobile.ui.screens.record.RecordScreen
import com.tukorea.synclab_mobile.ui.screens.upload.UploadScreen
import com.tukorea.synclab_mobile.ui.theme.SyncLab_MobileTheme
import com.tukorea.synclab_mobile.utils.PermissionHelper
import com.tukorea.synclab_mobile.utils.NtpSyncManager

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
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

    // 현재 경로를 감시하여 하단 바 표시 여부 결정
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route != "login"

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

    val screens = listOf(Screen.Home, Screen.Record, Screen.Upload, Screen.Settings)

    Scaffold(
        bottomBar = {
            // 로그인 화면이 아닐 때만 하단 바를 보여줍니다.
            if (showBottomBar) {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
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
        NavHost(
            navController = navController,
            startDestination = "login", // 시작 화면을 로그인으로 설정
            modifier = Modifier.padding(innerPadding)
        ) {
            // --- 로그인 경로 추가 ---
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        // 로그인 성공 시 홈으로 이동하고 로그인 화면은 스택에서 제거
                        navController.navigate(Screen.Home.route) {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Record.route) {
                if (isPermissionGranted) {
                    RecordScreen(navController = navController)
                } else {
                    PermissionRequestScreen {
                        launcher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                    }
                }
            }

            composable(Screen.Upload.route) {
                UploadScreen(navController = navController)
            }

            composable(Screen.Settings.route) { PlaceholderScreen("환경 설정") }
        }
    }
}

// 나머지 PermissionRequestScreen, PlaceholderScreen 코드는 동일