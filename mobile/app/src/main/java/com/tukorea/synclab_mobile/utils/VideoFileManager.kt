package com.tukorea.synclab_mobile.utils

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Utility for managing recorded video files.
 * Updated with high-priority logging and flexible file search logic.
 */
object VideoFileManager {
    // 로그캣에서 가장 잘 보이는 태그와 에러 레벨 사용
    private const val TAG = "VIDEO_CHECK_ERROR"

    /**
     * Retrieves a list of all video files.
     * Searches in all possible app directories to ensure no files are missed.
     */
    fun getVideoFiles(context: Context): List<File> {
        // 모든 로그를 Log.e (Error)로 출력하여 시스템 필터링을 우회합니다.
        Log.e(TAG, "========================================")
        Log.e(TAG, "!!! [CRITICAL] VIDEO FILE SCAN START !!!")
        Log.e(TAG, "========================================")

        // 표준 출력도 병행 (Logcat에서 'System.out'으로 검색 가능)
        println("DEBUG_SYSTEM_OUT: VideoFileManager.getVideoFiles() called")

        val paths = listOfNotNull(
            context.externalCacheDir,
            context.cacheDir,
            context.filesDir
        )

        val allDetectedFiles = mutableListOf<File>()

        paths.forEach { dir ->
            Log.e(TAG, "Checking Directory: ${dir.absolutePath}")

            val filesInDir = dir.listFiles()
            if (filesInDir == null) {
                Log.e(TAG, "Directory inaccessible or null: ${dir.name}")
                return@forEach
            }

            Log.e(TAG, "Total file count in [${dir.name}]: ${filesInDir.size}")

            filesInDir.forEach { file ->
                // 폴더 내의 '모든' 파일을 무조건 로그에 찍습니다. (필터링 전 확인용)
                Log.e(TAG, "  -> [FOUND ANY] Name: ${file.name}, Size: ${file.length()} bytes")

                val fileNameLower = file.name.lowercase()

                // 검색 조건 완화:
                // 1. mp4 확장자 포함
                // 2. 'synclab'이라는 단어가 파일명 어디든 포함 (앞부분이 아니어도 됨)
                // 3. 크기가 0보다 큰 경우
                if (fileNameLower.endsWith(".mp4") &&
                    fileNameLower.contains("synclab") &&
                    file.length() > 0
                ) {
                    allDetectedFiles.add(file)
                    Log.e(TAG, "     >>> [MATCHED] Added to upload queue: ${file.name}")
                }
            }
        }

        val result = allDetectedFiles.sortedByDescending { it.lastModified() }
        Log.e(TAG, "!!! SCAN FINISHED: Total ${result.size} files matched !!!")
        Log.e(TAG, "========================================")

        return result
    }

    /**
     * Deletes a specific file.
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val result = file.delete()
                Log.e(TAG, "File Delete SUCCESS: ${file.name}")
                result
            } else {
                Log.e(TAG, "Delete FAILED: File does not exist: ${file.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during delete: ${e.message}")
            false
        }
    }

    /**
     * Clears all cached video files.
     */
    fun clearAllCache(context: Context) {
        val files = getVideoFiles(context)
        files.forEach { deleteFile(it) }
        Log.e(TAG, "All cache files cleared.")
    }

    /**
     * Calculates total cache size in MB.
     */
    fun getCacheSizeMb(context: Context): Double {
        val files = getVideoFiles(context)
        val totalBytes = files.sumOf { it.length() }
        return totalBytes.toDouble() / (1024 * 1024)
    }
}