package com.tukorea.synclab_mobile.ui.navigation

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tukorea.synclab_mobile.Screen
import com.tukorea.synclab_mobile.ui.screens.auth.LoginScreen
import com.tukorea.synclab_mobile.ui.screens.upload.UploadScreen
import com.tukorea.synclab_mobile.ui.screens.record.RecordScreen
import com.tukorea.synclab_mobile.ui.components.PlaceholderScreen
import com.tukorea.synclab_mobile.ui.components.PermissionRequestScreen
import com.tukorea.synclab_mobile.ui.screens.home.HomeScreen
import com.tukorea.synclab_mobile.ui.screens.home.HomeViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    isPermissionGranted: Boolean,
    onPermissionRequest: () -> Unit
) {
    // 앱 전체에서 공유되는 세션 데이터 관리자
    val sharedHomeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // 1. 로그인 화면
        composable(route = Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

        // 2. 홈 화면
        composable(route = Screen.Home.route) {
            BackHandler {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(context, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
                }
            }
            HomeScreen(viewModel = sharedHomeViewModel)
        }

        // --- 공통: 뒤로가기 시 홈으로 이동 ---
        val navigateToHome = {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
                launchSingleTop = true
            }
        }

        // 3. 녹화 화면 (ViewModel 전달)
        composable(route = Screen.Record.route) {
            BackHandler { navigateToHome() }
            if (isPermissionGranted) {
                RecordScreen(
                    navController = navController,
                    homeViewModel = sharedHomeViewModel // ✅ 전달 완료
                )
            } else {
                PermissionRequestScreen(onRequest = onPermissionRequest)
            }
        }

        // 4. 업로드 화면 (ViewModel 전달)
        composable(route = Screen.Upload.route) {
            BackHandler { navigateToHome() }
            UploadScreen(
                navController = navController,
                homeViewModel = sharedHomeViewModel // ✅ 전달 완료
            )
        }

        // 5. 설정 화면
        composable(route = Screen.Settings.route) {
            BackHandler { navigateToHome() }
            PlaceholderScreen("환경 설정")
        }
    }
}