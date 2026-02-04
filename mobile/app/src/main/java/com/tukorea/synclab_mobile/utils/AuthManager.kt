package com.tukorea.synclab_mobile.utils

import android.content.Context
import android.content.SharedPreferences

class AuthManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // 토큰 저장
    fun saveToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    // 토큰 읽기
    fun getToken(): String? {
        return prefs.getString("access_token", null)
    }

    // 로그아웃 시 토큰 삭제
    fun clearToken() {
        prefs.edit().remove("access_token").apply()
    }
}