package com.tukorea.synclab_mobile.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * SyncLab 앱에서 필요한 모든 권한을 관리하는 유틸리티 클래스
 */
object PermissionHelper {

    // 1. 필요한 모든 권한 리스트 정의
    val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        // 안드로이드 버전(SDK 28 이하)에 따라 필요한 추가 권한 처리
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    /**
     * 현재 모든 권한이 허용되었는지 확인하는 함수
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}