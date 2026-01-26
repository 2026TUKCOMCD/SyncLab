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
            val raf = RandomAccessFile(file, "r")
            raf.seek(offset)
            val buffer = ByteArray(partSize.toInt())
            raf.readFully(buffer)
            raf.close()

            val request = Request.Builder()
                .url(partUrl)
                .put(buffer.toRequestBody("video/mp4".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.header("ETag")?.replace("\"", "")
            else null
        } catch (e: Exception) {
            null
        }
    }
}