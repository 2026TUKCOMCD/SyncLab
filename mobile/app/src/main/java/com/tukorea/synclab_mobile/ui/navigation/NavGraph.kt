package com.tukorea.synclab_mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tukorea.synclab_mobile.Screen
import com.tukorea.synclab_mobile.ui.screens.auth.LoginScreen
import com.tukorea.synclab_mobile.ui.screens.upload.UploadScreen
import com.tukorea.synclab_mobile.ui.screens.record.RecordScreen
// MainActivity 파일에 있는 함수들을 쓰기 위해 import (함수가 파일 최상단에 있을 경우)
import com.tukorea.synclab_mobile.ui.components.PlaceholderScreen
import com.tukorea.synclab_mobile.ui.components.PermissionRequestScreen
import com.tukorea.synclab_mobile.ui.screens.home.HomeScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    isPermissionGranted: Boolean,
    onPermissionRequest: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable(route = "login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.Home.route) {
            HomeScreen()

        }

        composable(route = Screen.Record.route) {
            if (isPermissionGranted) {
                RecordScreen(navController = navController)
            } else {
                // MainActivity.kt 파일 하단에 선언한 함수 호출
                PermissionRequestScreen(onRequest = onPermissionRequest)
            }
        }

        composable(route = Screen.Upload.route) {
            UploadScreen(navController = navController)
        }

        composable(route = Screen.Settings.route) {
            PlaceholderScreen("환경 설정")
        }
    }
}