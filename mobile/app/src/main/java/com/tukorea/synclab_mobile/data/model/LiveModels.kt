package com.tukorea.synclab_mobile.data.model

import com.google.gson.annotations.SerializedName

data class LiveSessionResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("room_name") val roomName: String,
    @SerializedName("livekit_url") val livekitUrl: String
)

data class LiveTokenResponse(
    @SerializedName("token") val token: String
)

data class SwitchResponse(
    @SerializedName("status") val status: String,
    @SerializedName("active_camera") val activeCamera: String
)

data class LiveStatusResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("room_name") val roomName: String,
    @SerializedName("active_camera") val activeCamera: String?,
    @SerializedName("cameras") val cameras: List<CameraParticipant>
)

data class CameraParticipant(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("isActive") val isActive: Boolean,
    @SerializedName("isStreaming") val isStreaming: Boolean = false
)

data class OverlayData(
    @SerializedName("showScoreboard") val showScoreboard: Boolean = false,
    @SerializedName("homeTeam") val homeTeam: String = "HOME",
    @SerializedName("awayTeam") val awayTeam: String = "AWAY",
    @SerializedName("homeScore") val homeScore: Int = 0,
    @SerializedName("awayScore") val awayScore: Int = 0,
    @SerializedName("showLowerThird") val showLowerThird: Boolean = false,
    @SerializedName("lowerThird") val lowerThird: String = "",
    @SerializedName("subTitle") val subTitle: String = ""
)
