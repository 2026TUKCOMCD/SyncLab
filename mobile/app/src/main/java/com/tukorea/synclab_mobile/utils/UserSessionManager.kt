package com.tukorea.synclab_mobile.utils

/**
 * 앱 전체에서 현재 로그인한 유저와 참가 중인 세션 정보를 공유하기 위한 싱글톤 객체
 */
object UserSessionManager {
    var userId: String? = "111" // 테스트용 기본값
    var currentSessionId: String? = null // 세션 참가 시 여기에 ID를 저장할 예정
    var userName: String? = "테스트 유저"

    // 세션 참가 성공 시 호출할 함수
    fun joinSession(sessionId: String) {
        this.currentSessionId = sessionId
    }

    // 로그아웃 혹은 세션 종료 시 호출
    fun clear() {
        currentSessionId = null
    }
}