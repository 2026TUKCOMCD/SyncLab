package com.tukorea.synclab_mobile.data.repository

import android.util.Log
import com.tukorea.synclab_mobile.api.NetworkClient
import com.tukorea.synclab_mobile.data.api.VidoeUploadService // 인터페이스 임포트 확인
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class UploadRepository {

    private val api: VidoeUploadService = NetworkClient.service

    private val s3Client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS) // 영상 업로드는 시간을 넉넉히 (10분)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // [Step 1] 함수명 유지: S3 직접 업로드
    // 이제 presignedUrl을 밖에서 넣어줄 필요 없이 내부에서 노트북 서버를 통해 받아옵니다.
    suspend fun uploadVideoToS3(unusedUrl: String, videoFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!videoFile.exists()) {
                    return@withContext Result.failure(Exception("파일이 존재하지 않습니다."))
                }

                // 1. 노트북 파이썬 서버 호출하여 진짜 S3 URL 받아오기
                Log.d("S3_UPLOAD", "노트북 서버에 URL 요청 중: ${videoFile.name}")
                val urlResponse = api.getPresignedUrl(videoFile.name)
                val realPresignedUrl = urlResponse["url"]
                    ?: return@withContext Result.failure(Exception("URL 획득 실패"))

                // 2. 받아온 진짜 URL로 S3에 파일 전송 (PUT 방식)
                val requestBody = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(realPresignedUrl)
                    .put(requestBody) // 💡 반드시 .put() 이어야 합니다! GET은 안 돼요.
                    .addHeader("Content-Type", "video/mp4")
                    .build()

                Log.d("S3_UPLOAD", "S3로 실제 업로드 시작...")

                s3Client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("S3_UPLOAD", "S3 업로드 최종 성공!")
                        Result.success(Unit)
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e("S3_UPLOAD", "S3 응답 에러: $errorBody")
                        Result.failure(Exception("S3 업로드 실패: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Log.e("S3_UPLOAD", "예외 발생", e)
                Result.failure(e)
            }
        }
    }

    // [Step 2] 함수명 유지: 서버 메타데이터 등록
    suspend fun uploadMetadataToServer(jsonFile: File, videoId: String): Result<Unit> {
        return try {
            val jsonRequestBody = jsonFile.asRequestBody("application/json".toMediaTypeOrNull())
            val jsonPart = MultipartBody.Part.createFormData("metadata", jsonFile.name, jsonRequestBody)
            val videoIdBody = videoId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.registerVideoMetadata(jsonPart, videoIdBody)

            if (response.isSuccessful) {
                Log.d("UPLOAD_REPO", "서버 메타데이터 등록 성공")
                Result.success(Unit)
            } else {
                Result.failure(Exception("서버 에러: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("UPLOAD_REPO", "메타데이터 등록 중 예외 발생: ${e.message}")
            Result.failure(e)
        }
    }
}