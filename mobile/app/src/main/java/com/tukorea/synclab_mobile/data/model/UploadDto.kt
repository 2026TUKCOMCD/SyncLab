package com.tukorea.synclab_mobile.data.model

import com.google.gson.annotations.SerializedName


data class InitUploadResponse(
    @SerializedName("upload_id") val uploadId: String,           // uploadId -> upload_id
    @SerializedName("presigned_urls") val presignedUrls: List<String>, // presignedUrls -> presigned_urls
    @SerializedName("s3_key") val s3Key: String                  // s3Key -> s3_key
)


data class CompleteUploadRequest(
    @SerializedName("session_id") val sessionId: String,         // sessionId -> session_id
    @SerializedName("upload_id") val uploadId: String,           // uploadId -> upload_id
    @SerializedName("video_name") val videoName: String,         // videoName -> video_name
    val etags: List<String>,
    val metadata: VideoMetadata
)