package com.tukorea.synclab_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.ui.navigation.NavGraph
import com.tukorea.synclab_mobile.ui.theme.SyncLab_MobileTheme
import com.tukorea.synclab_mobile.utils.PermissionHelper
import com.tukorea.synclab_mobile.utils.NtpSyncManager

// 시안 기반 화면 정보 정의
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Login : Screen("login", "로그인", Icons.Default.Lock)
    object Home : Screen("home", "홈", Icons.Default.Home)
    object Record : Screen("record", "녹화", Icons.Default.RadioButtonChecked) // fa-circle-dot 스타일
    object Upload : Screen("upload", "업로드", Icons.Default.CloudUpload) // fa-cloud-arrow-up 스타일
    object Settings : Screen("settings", "설정", Icons.Default.Settings) // fa-gear 스타일
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NetworkClient.init(applicationContext)
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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

    val bottomNavItems = listOf(Screen.Home, Screen.Record, Screen.Upload, Screen.Settings)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(80.dp)
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            icon = {
                                Box {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.label,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    // 업로드 아이콘에 시안의 빨간 알림 점 추가
                                    if (screen is Screen.Upload) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .clip(CircleShape)
                                                .background(Color.Red)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF3366FF),
                                selectedTextColor = Color(0xFF3366FF),
                                unselectedIconColor = Color(0xFF94A3B8), // 시안의 slate-400
                                unselectedTextColor = Color(0xFF94A3B8),
                                indicatorColor = Color.Transparent // 강조 배경 제거 (시안 스타일)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
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