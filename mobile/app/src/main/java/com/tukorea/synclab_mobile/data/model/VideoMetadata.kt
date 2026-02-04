package com.tukorea.synclab_mobile.data.model

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

/**
 * DB 테이블(video) 컬럼과 1:1 매칭되는 데이터 모델
 */
data class VideoMetadata(
    // CompleteUploadRequest의 상위 필드인 video_name과 별개로
    // metadata 내부에도 video_name이 필요하다면 아래를 유지합니다.
    @SerializedName("video_name") val videoName: String,

    @SerializedName("file_name") val fileName: String,

    @SerializedName("absolute_start_time") val absoluteStartTime: Long,

    @SerializedName("absolute_end_time") val absoluteEndTime: Long,

    val duration: Double,

    @SerializedName("session_id") val sessionId: String = "default_session"
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
            sessionId: String
        ): VideoMetadata {
            return VideoMetadata(
                videoName = videoName,
                fileName = fileName,
                absoluteStartTime = startSys + startOff,
                absoluteEndTime = endSys + endOff,
                duration = (endSys - startSys) / 1000.0,
                sessionId = sessionId
            )
        }
    }
}