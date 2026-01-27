package com.tukorea.synclab_mobile.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile

object S3Uploader {
    private val client = OkHttpClient()

    fun uploadPart(partUrl: String, file: File, partNumber: Int, offset: Long, partSize: Long): String? {
        // 1. RandomAccessFile을 'use' 블록으로 감싸 에러 발생 시에도 확실히 닫히도록 함
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)

                // 2. 조각 크기만큼의 버퍼 생성
                val buffer = ByteArray(partSize.toInt())
                val bytesRead = raf.read(buffer)

                if (bytesRead == -1) return null // 파일 끝에 도달한 경우

                // 3. Request 생성
                val request = Request.Builder()
                    .url(partUrl)
                    .put(buffer.toRequestBody("video/mp4".toMediaType()))
                    .build()

                // 4. 네트워크 통신 실행
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // ETag 추출 및 따옴표 제거
                        response.header("ETag")?.replace("\"", "")
                    } else {
                        println("Upload failed for part $partNumber: ${response.code}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            println("Error uploading part $partNumber: ${e.message}")
            null
        }
    }
}