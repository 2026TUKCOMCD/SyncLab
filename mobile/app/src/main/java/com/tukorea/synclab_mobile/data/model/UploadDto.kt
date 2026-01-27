package com.tukorea.synclab_mobile.data.model

// 1. 서버에 분할 업로드를 시작할 때 받는 응답
data class InitUploadResponse(
    val uploadId: String,           // S3 Multipart Upload ID
    val presignedUrls: List<String>, // 각 5MB 조각에 대응하는 URL들
    val s3Key: String
)

// 2. 서버에 업로드 완료 보고 + 메타데이터를 한 번에 보낼 때 쓰는 요청
data class CompleteUploadRequest(
    val uploadId: String,
    val videoName: String,
    val etags: List<String>,        // S3에서 받은 조각별 ETag 목록
    val metadata: VideoMetadata     // <--- 기존에 만드신 메타데이터를 여기에 포함!
)