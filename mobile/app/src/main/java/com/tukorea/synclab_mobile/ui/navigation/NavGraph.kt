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
    val sharedHomeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    var backPressedTime by remember { mutableLongStateOf(0L) }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Home.route) {
                    // "login" 화면 자체를 스택에서 제거
                    popUpTo(Screen.Login.route) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

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

        // --- 기타 화면 (뒤로가기 시 홈으로) ---
        val navigateToHome = {
            navController.navigate(Screen.Home.route) {
                // 홈 화면까지의 스택을 모두 비움
                popUpTo(Screen.Home.route) { inclusive = true }
                launchSingleTop = true
            }
        }

        composable(route = Screen.Record.route) {
            BackHandler { navigateToHome() }
            if (isPermissionGranted) {
                RecordScreen(navController = navController)
            } else {
                PermissionRequestScreen(onRequest = onPermissionRequest)
            }
        }

        composable(route = Screen.Upload.route) {
            BackHandler { navigateToHome() }
            UploadScreen(navController = navController)
        }

        composable(route = Screen.Settings.route) {
            BackHandler { navigateToHome() }
            PlaceholderScreen("환경 설정")
        }
    }
}