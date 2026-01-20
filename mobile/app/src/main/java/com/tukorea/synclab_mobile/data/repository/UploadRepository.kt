package com.tukorea.synclab_mobile.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class UploadRepository {
    private val api = NetworkClient.service

    /**
     * 영상과 JSON 메타데이터를 서버로 동시 업로드
     */
    suspend fun uploadVideoWithMetadata(videoFile: File, jsonFile: File): Result<UploadResponse> {
        return try {
            // 1. 영상 파일 준비
            val videoRequestBody = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
            val videoPart = MultipartBody.Part.createFormData("video", videoFile.name, videoRequestBody)

            // 2. JSON 파일 준비
            val jsonRequestBody = jsonFile.asRequestBody("application/json".toMediaTypeOrNull())
            val jsonPart = MultipartBody.Part.createFormData("metadata", jsonFile.name, jsonRequestBody)

            // 3. 식별자 준비
            val videoIdBody = videoFile.nameWithoutExtension.toRequestBody("text/plain".toMediaTypeOrNull())

            // 4. 서버 전송
            val response = api.uploadVideoData(videoPart, jsonPart, videoIdBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Upload failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}