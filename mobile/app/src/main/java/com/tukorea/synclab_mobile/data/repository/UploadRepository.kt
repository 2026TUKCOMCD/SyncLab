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

    // 이제 api는 registerVideoMetadata 함수를 가진 VidoeUploadService 타입입니다.
    private val api: VidoeUploadService = NetworkClient.service


    private val s3Client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // [Step 1] S3 직접 업로드 (기존 코드 수정)
    suspend fun uploadVideoToS3(presignedUrl: String, videoFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) { // 💡 네트워크 작업은 반드시 IO 스레드에서!
            try {
                if (!videoFile.exists()) {
                    return@withContext Result.failure(Exception("파일이 존재하지 않습니다: ${videoFile.absolutePath}"))
                }

                val requestBody = videoFile.asRequestBody("video/mp4".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(presignedUrl)
            //        .put(requestBody)
                    .method("GET", null) // 💡 2. 메소드를 "GET"으로 속여서 보냅니다.
                    //.addHeader("Content-Type", "video/mp4")
                    .build()

                Log.d("S3_UPLOAD", "업로드 시작: ${videoFile.name}")

                s3Client.newCall(request).execute().use { response ->
                    Log.d("S3_UPLOAD", "Response Code: ${response.code}")
                    Log.d("S3_UPLOAD", "Response Message: ${response.message}")

                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        val errorBody = response.body?.string() ?: "에러 내용 없음"
                        Log.e("S3_UPLOAD", "S3 응답 에러: $errorBody")
                        Result.failure(Exception("S3 업로드 실패 (코드: ${response.code})"))
                    }
                }
            } catch (e: Exception) {
                // 💡 상세한 스택 트레이스를 찍어서 'null' 대신 진짜 이유를 확인합니다.
                Log.e("S3_UPLOAD", "S3 업로드 중 예외 발생", e)
                Result.failure(e)
            }
        }
    }
    // [Step 2] 서버 메타데이터 등록
    suspend fun uploadMetadataToServer(jsonFile: File, videoId: String): Result<Unit> {
        return try {
            val jsonRequestBody = jsonFile.asRequestBody("application/json".toMediaTypeOrNull())
            val jsonPart = MultipartBody.Part.createFormData("metadata", jsonFile.name, jsonRequestBody)
            val videoIdBody = videoId.toRequestBody("text/plain".toMediaTypeOrNull())

            // 💡 드디어 정상적으로 호출 가능합니다.
            val response = api.registerVideoMetadata(jsonPart, videoIdBody)

            if (response.isSuccessful) {
                Log.d("UPLOAD_REPO", "서버 등록 성공")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("UPLOAD_REPO", "서버 에러: ${response.code()}, $errorBody")
                Result.failure(Exception("서버 에러: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("UPLOAD_REPO", "호출 중 예외 발생: ${e.message}")
            Result.failure(e)
        }
    }
}