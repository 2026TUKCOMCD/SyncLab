package com.tukorea.synclab_mobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.tukorea.synclab_mobile.ui.screens.record.RecordScreen
import com.tukorea.synclab_mobile.ui.theme.SyncLab_MobileTheme
import com.tukorea.synclab_mobile.utils.PermissionHelper // 아까 만든 Helper 임포트

// --- 네비게이션 정의 ---
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

    // 1. 초기 권한 상태를 Helper를 통해 확인
    var isPermissionGranted by remember {
        mutableStateOf(PermissionHelper.hasAllPermissions(context))
    }

    // 2. 권한 요청 런처 (Helper에 정의된 목록 사용)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        isPermissionGranted = result.values.all { it }
        if (!isPermissionGranted) {
            Toast.makeText(context, "카메라와 마이크 권한이 승인되어야 녹화가 가능합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val screens = listOf(Screen.Home, Screen.Record, Screen.Upload, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { PlaceholderScreen("SyncLab 메인 화면") }

            composable(Screen.Record.route) {
                // 권한 상태에 따라 다른 화면 표시
                if (isPermissionGranted) {
                    RecordScreen(navController = navController)
                } else {
                    PermissionRequestScreen {
                        launcher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                    }
                }
            }

            composable(Screen.Upload.route) { PlaceholderScreen("S3 업로드 대기 목록") }
            composable(Screen.Settings.route) { PlaceholderScreen("환경 설정") }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("녹화 기능을 위해 카메라와 마이크 권한이 필요합니다.")
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            Button(onClick = onRequest) {
                Text("권한 허용하기")
            }
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
    }
}