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
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)

                val buffer = ByteArray(partSize.toInt())
                val bytesRead = raf.read(buffer)

                if (bytesRead == -1) return null

                val request = Request.Builder()
                    .url(partUrl)
                    .put(buffer.toRequestBody("video/mp4".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
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