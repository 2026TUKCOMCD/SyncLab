package com.tukorea.synclab_mobile.utils

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object S3Uploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS) // 각 조각(5MB) 업로드 시간으로 충분
        .build()

    /**
     * 특정 조각(Part)을 업로드하는 핵심 함수
     * @param partUrl: 해당 파트 번호에 대해 백엔드에서 발급한 개별 Presigned URL
     * @param partNumber: 현재 몇 번째 조각인지 (1부터 시작)
     */
    suspend fun uploadPart(
        partUrl: String,
        file: File,
        partNumber: Int,
        offset: Long,
        partSize: Long
    ): String? = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(partSize.toInt())
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val bytesRead = raf.read(buffer)
                val actualData = if (bytesRead < partSize) buffer.copyOf(bytesRead) else buffer

                val requestBody = actualData.toRequestBody("video/mp4".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(partUrl)
                    .put(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // S3는 업로드 성공 시 헤더에 ETag를 담아 줍니다. (완료 보고 시 필수)
                        val etag = response.header("ETag")?.replace("\"", "")
                        Log.d("S3_UPLOAD", "Part $partNumber 성공: ETag=$etag")
                        etag
                    } else {
                        Log.e("S3_UPLOAD", "Part $partNumber 실패: ${response.code}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("S3_UPLOAD", "Part $partNumber 예외: ${e.message}")
            null
        }
    }
}