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
import com.tukorea.synclab_mobile.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    isPermissionGranted: Boolean,
    onPermissionRequest: () -> Unit
) {
    // 모든 화면에서 공유할 ViewModel
    val sharedHomeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // 1. 로그인 화면
        composable(route = Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { loginResponse -> // 👈 [수정] 서버 응답(LoginResponse)을 받음
                    // ✅ [핵심] 뷰모델에 유저 정보와 세션 ID를 즉시 업데이트
                    sharedHomeViewModel.updateUserInfo(loginResponse)

                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onGuestLogin = { inviteCode ->
                    sharedHomeViewModel.isGuest = true
                    sharedHomeViewModel.joinSession(inviteCode)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. 홈 화면
        composable(route = Screen.Home.route) {
            HomeScreen(viewModel = sharedHomeViewModel)
        }

        // 공통 네비게이션 헬퍼
        val navigateToHome = {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
                launchSingleTop = true
            }
        }

        // 3. 녹화 화면
        composable(route = Screen.Record.route) {
            BackHandler { navigateToHome() }
            if (isPermissionGranted) {
                RecordScreen(navController = navController, homeViewModel = sharedHomeViewModel)
            } else {
                PermissionRequestScreen(onRequest = onPermissionRequest)
            }
        }

        // 4. 업로드 화면
        composable(route = Screen.Upload.route) {
            BackHandler { navigateToHome() }
            UploadScreen(navController = navController, homeViewModel = sharedHomeViewModel)
        }

        // 5. 설정 화면
        composable(route = Screen.Settings.route) {
            BackHandler { navigateToHome() }

            SettingsScreen(
                onBackClick = { navigateToHome() },
                onLogoutSuccess = {
                    sharedHomeViewModel.performLogout() // ✅ clearSession 대신 통합 로그아웃 호출

                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                    Toast.makeText(context, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                },
                onLoginClick = {
                    navController.navigate(Screen.Login.route) {
                        launchSingleTop = true
                    }
                },
                homeViewModel = sharedHomeViewModel
            )
        }
    }
}