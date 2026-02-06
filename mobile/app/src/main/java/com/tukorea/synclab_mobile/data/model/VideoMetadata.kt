package com.tukorea.synclab_mobile.data.model

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

data class VideoMetadata(
    @SerializedName("video_name")
    val videoName: String? = null,

    @SerializedName("file_name")
    val fileName: String? = null,

    @SerializedName("absolute_start_time")
    val absoluteStartTime: Long = 0L,

    @SerializedName("absolute_end_time")
    val absoluteEndTime: Long = 0L,

    @SerializedName("duration")
    val duration: Double = 0.0,

    @SerializedName("session_id")
    val sessionId: String? = null // 기본값을 빼고 실제 데이터 유무를 체크하는 게 안전합니다.
) {
    fun toJson(): String = GsonBuilder().create().toJson(this)

    companion object {
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