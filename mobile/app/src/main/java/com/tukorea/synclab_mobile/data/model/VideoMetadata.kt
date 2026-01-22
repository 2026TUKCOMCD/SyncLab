package com.tukorea.synclab_mobile.data.model

import com.google.gson.GsonBuilder

/**
 * DB 테이블(video) 컬럼과 1:1 매칭되는 최종 데이터 모델
 * 불필요한 보조 지표를 모두 제거하고 DB 저장용 필드만 남겼습니다.
 */
data class VideoMetadata(
    val videoName: String,          // DB: video_name (영상 제목)
    val fileName: String,           // S3 Key 및 파일 식별용
    val absoluteStartTime: Long,    // DB: absolute_start_time (보정된 시작 시간)
    val absoluteEndTime: Long,      // DB: absolute_end_time (보정된 종료 시간)
    val duration: Double            // DB: duration (초 단위 영상 길이)
) {
    /**
     * 객체를 서버 전송용 JSON 문자열로 변환합니다.
     */
    fun toJson(): String = GsonBuilder()
        .create()
        .toJson(this)

    companion object {
        /**
         * 팩토리 메서드: 촬영 시점의 원시 데이터를 받아 DB 포맷으로 가공하여 객체 생성
         */
        fun create(
            fileName: String,
            videoName: String,
            startSys: Long,
            startOff: Long,
            endSys: Long,
            endOff: Long
        ): VideoMetadata {
            return VideoMetadata(
                videoName = videoName,
                fileName = fileName,
                // 안드로이드에서 보정 계산 완료
                absoluteStartTime = startSys + startOff,
                absoluteEndTime = endSys + endOff,
                // ms를 초(Double) 단위로 변환
                duration = (endSys - startSys) / 1000.0
            )
        }
    }
}