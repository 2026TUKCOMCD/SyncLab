package com.tukorea.synclab_mobile.utils

import android.content.Context
import android.util.Log
import java.io.File

object VideoFileManager {
    private const val TAG = "VideoFileManager"

    fun getVideoFiles(context: Context): List<File> {
        // 검색할 모든 경로 리스트
        val paths = listOfNotNull(
            context.externalCacheDir,
            context.cacheDir,
            context.filesDir,
            context.getExternalFilesDir("videos") // 추가: 비디오 전용 폴더도 확인
        )

        val allDetectedFiles = mutableListOf<File>()

        paths.forEach { dir ->
            val filesInDir = dir.listFiles() ?: return@forEach

            filesInDir.forEach { file ->
                val fileNameLower = file.name.lowercase()

                // 검색 조건: mp4 확장자 + 이름에 synclab 포함 (혹은 mp4 전체)
                // 촬영 로직에 따라 synclab이 없을 수도 있으므로 .mp4 체크를 우선합니다.
                if (file.isFile && fileNameLower.endsWith(".mp4") && file.length() > 0) {
                    allDetectedFiles.add(file)
                }
            }
        }

        // 최신순 정렬
        val result = allDetectedFiles.distinctBy { it.absolutePath } // 중복 제거
            .sortedByDescending { it.lastModified() }

        Log.d(TAG, "Scan finished. Found ${result.size} video files.")
        return result
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                // mp4와 쌍을 이루는 json 파일이 있다면 함께 삭제
                val jsonFile = File(file.absolutePath.replace(".mp4", ".json"))
                if (jsonFile.exists()) jsonFile.delete()
                deleted
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}")
            false
        }
    }
}