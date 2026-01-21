package com.tukorea.synclab_mobile.utils

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object S3Uploader {

    // 1. 타임아웃 시간을 영상 업로드에 맞게 더 늘렸습니다.
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // 임시 코드: 테스트용 URL (발급받은 긴 URL)
    const val TEST_URL = "https://synclab-1080p-mp4.s3.ap-northeast-2.amazonaws.com/path/to/video.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIA4OMP7SOPPKRQBC6A%2F20260121%2Fap-northeast-2%2Fs3%2Faws4_request&X-Amz-Date=20260121T072001Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEPf%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaDmFwLW5vcnRoZWFzdC0yIkYwRAIgVYuEs%2FxvsLWwxKfOuIvF%2BD6oFIlQRSjYehoEeKdM6gACIGq96A7bJXZkAzinqzLze6nvnJ7S1gOdHSMlwHewnmkTKokDCMD%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQABoMODU1NTM3MzI0OTU4IgzbTGpJ3%2BmQfEVQGFYq3QKtoLrJ8KfN2eee9aWdzmb7cMCs71l%2F4XTGhY45IfrkPgtjjDflKG3upMbY2ADWwjcLIE%2Br6HdWDotvCxRVs8%2B6cQVHSOIzCRlM2U7FBiZ5C%2BBzi62S7qRDXN8PaJuUnR9UTGyd5eutOfB7AoiVxIhGcwjYZLJg8zGm7EwP2ACAUcmKOgN4CziZE77%2BZEv57xl4Xu2OWDk3yVtZS1jqaRP70cyNkLi%2B9CdjFeHZ%2B5jJWitycE%2BJ0YzgxufnMGoqXpUAGsmrZRm%2FFyQ0IXWRb8wW3t4krVYy3GFQz8ilHtk0m5g4%2Fmrh2xzX2cyuEwpTap%2BJqjSGNncnPXjQeB9IyqDIU2qoG%2B3ZQ20Ipa%2Fza2YAalucinUE%2BJk00IJWA%2BN4sWYHM0V39zq9S9QkNdZ50V%2FZ8TsTOo9K70u9Ghc8%2Fc28%2BmBxR6d2iHeJdf2OAZ0vTYAxLlojhejSFdrR9%2F%2BrML6xwcsGOq4C0XVmwLrvRLaYo%2BHQzQiPOisNTqgcEqKb3eY2yFZNBzMiWpQNKMIAJgzpnRVkE27BAO6c%2FpiV%2BA9Xm9eF8JDuJKqZ0vT%2BYuK%2FY0gkBW4cF1wC249BtytVjqsd%2BdrO7sXON4jtadE2gRnxMHddT44zVRpRpuPPMwKfEjqCN1lPhYgct9X1dINctnlmwbuaaAAoCSSHbt4HKbCxKXC%2F1%2FwSE8AjQhNq2qyRJSjbYVpi%2BJGnpRcv0a0PK26zoMgAK6JsIFpw2ey2Hrw85VrIsNILbjb%2B1fhSabJp6nf1X1HKR%2FtpvS%2B7vi47XusZxutPf%2BcmnKUty9uvcFp5EFD%2Fd8bPBf%2Fp4jFln8YU24pBUsk0LSWwnsKihPfzJ1qZqbWFoGDYPn%2BF4EyMiCB5NUbzENU%3D&X-Amz-Signature=1a4bd7e36d9d692b6fc65d2098fded2f486836f7e41ad245e20fe9972f0db0e6"

    suspend fun uploadVideo(
        presignedUrl: String = TEST_URL,
        file: File,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        // 2. 업로드 전 파일 존재 여부와 실제 크기 로그 출력
        if (!file.exists()) {
            Log.e("S3_TEST", "파일이 존재하지 않습니다: ${file.absolutePath}")
            return@withContext false
        }
        Log.d("S3_TEST", "업로드 시작 - 파일명: ${file.name}, 크기: ${file.length()} bytes")

        try {
            val requestBody = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaTypeOrNull()
                override fun contentLength() = file.length()

                override fun writeTo(sink: BufferedSink) {
                    file.source().use { source ->
                        var totalRead: Long = 0
                        val bufferSize = 8192L
                        var read: Long

                        // 게이지가 정확히 100%까지 가는지 확인하기 위한 로직
                        while (source.read(sink.buffer, bufferSize).also { read = it } != -1L) {
                            totalRead += read
                            val progress = ((totalRead * 100) / contentLength()).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(presignedUrl)
                .put(requestBody)
                .addHeader("Content-Type", "video/mp4")
                .build()

            client.newCall(request).execute().use { response ->
                val isSuccessful = response.isSuccessful
                val responseCode = response.code

                // 4. 성공 여부와 상관없이 응답 코드와 메시지 로그 남기기
                if (isSuccessful) {
                    Log.d("S3_TEST", "업로드 성공! Response Code: $responseCode")
                } else {
                    val errorBody = response.body?.string() // 서버가 보낸 에러 이유
                    Log.e("S3_TEST", "업로드 실패 - Code: $responseCode, Message: ${response.message}")
                    Log.e("S3_TEST", "에러 상세정보: $errorBody")
                }
                isSuccessful
            }
        } catch (e: Exception) {
            Log.e("S3_UPLOAD_ERROR", "업로드 중 예외 발생: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}