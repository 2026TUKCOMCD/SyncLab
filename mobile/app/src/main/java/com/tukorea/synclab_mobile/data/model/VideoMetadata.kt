package com.tukorea.synclab_mobile.data.model

import com.google.gson.GsonBuilder

/**
 * DB 테이블(video) 컬럼과 1:1 매칭되는 데이터 모델
 */
data class VideoMetadata(
    val videoName: String,
    val fileName: String,
    val absoluteStartTime: Long,
    val absoluteEndTime: Long,
    val duration: Double,
    val sessionId: String = "default_session" // ✅ 1. 클래스 필드에 존재함
) {
    fun toJson(): String = GsonBuilder()
        .create()
        .toJson(this)

    companion object {
        /**
         * 촬영 시점의 원시 데이터를 받아 DB 포맷으로 가공하여 객체 생성
         */
        fun create(
            fileName: String,
            videoName: String,
            startSys: Long,
            startOff: Long,
            endSys: Long,
            endOff: Long,
            sessionId: String // ✅ 2. 생성 함수 인자에도 추가되어야 함
        ): VideoMetadata {
            return VideoMetadata(
                videoName = videoName,
                fileName = fileName,
                absoluteStartTime = startSys + startOff,
                absoluteEndTime = endSys + endOff,
                duration = (endSys - startSys) / 1000.0,
                sessionId = sessionId // ✅ 3. 여기서 객체 생성 시 전달해야 함
            )
        }
    }
}