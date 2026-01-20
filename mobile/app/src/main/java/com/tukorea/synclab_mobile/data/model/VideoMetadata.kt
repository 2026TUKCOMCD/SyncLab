package com.tukorea.synclab_mobile.data.model

import com.google.gson.GsonBuilder

/**
 * 전략 B: 서버측 선형 보간(Linear Interpolation)을 위한 초정밀 메타데이터 모델
 * 이 클래스는 촬영의 시작과 종료 시점의 원시 시스템 시간과 NTP 오프셋을 모두 보존합니다.
 */
data class VideoMetadata(
    val videoId: String,            // 영상 고유 식별자 (파일명 기반)
    val deviceId: String,           // 기기 고유 식별자
    val fileName: String,           // 실제 저장된 파일 이름

    // 1. 원본 시스템 시간 (보정되지 않은 기기 자체 시계)
    val startSystemTime: Long,
    val endSystemTime: Long,

    // 2. NTP 보정 데이터 (서버에서 사후 보정 및 선형 보간용)
    val startOffset: Long,          // 촬영 시작 버튼 클릭 직전 측정된 오프셋
    val endOffset: Long,            // 촬영 종료 버튼 클릭 직후 측정된 오프셋

    // 3. 네트워크 신뢰도 지표 (왕복 지연 시간)
    val startRtt: Long,             // 시작 시점의 네트워크 지연 (낮을수록 정확)
    val endRtt: Long,               // 종료 시점의 네트워크 지연

    // 4. 촬영 정보 및 하드웨어 메타데이터
    val durationMs: Long,           // 시스템 시간 기준 계산된 촬영 시간
    val lastSyncTimestamp: Long,    // 마지막으로 동기화에 성공한 시스템 시각
    val isSynced: Boolean,          // 동기화 성공 여부

    // 5. 기기 정보 (서버에서 기기별 시계 특성 파악용)
    val modelName: String = android.os.Build.MODEL,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,

    // 6. 영상 설정 정보
    val resolution: String = "1080p",
    val fps: Int = 30
) {
    /**
     * 객체를 서버 전송용 JSON 문자열로 변환합니다.
     */
    fun toJson(): String = GsonBuilder()
        .setPrettyPrinting()
        .create()
        .toJson(this)

    companion object {
        /**
         * 팩토리 메서드: 모든 필수 데이터를 받아 VideoMetadata 객체를 생성합니다.
         */
        fun create(
            fileName: String,
            startSys: Long,
            endSys: Long,
            startOff: Long,
            endOff: Long,
            startRtt: Long,
            endRtt: Long,
            lastSync: Long,
            isSynced: Boolean
        ): VideoMetadata {
            return VideoMetadata(
                videoId = fileName.removeSuffix(".mp4"),
                deviceId = android.os.Build.ID, // 기기 빌드 ID 또는 고유 식별자
                fileName = fileName,
                startSystemTime = startSys,
                endSystemTime = endSys,
                startOffset = startOff,
                endOffset = endOff,
                startRtt = startRtt,
                endRtt = endRtt,
                durationMs = endSys - startSys,
                lastSyncTimestamp = lastSync,
                isSynced = isSynced
            )
        }
    }
}