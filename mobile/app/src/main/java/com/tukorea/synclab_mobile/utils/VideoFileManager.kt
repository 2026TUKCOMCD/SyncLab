package com.tukorea.synclab_mobile.utils

import android.content.Context
import android.util.Log
import java.io.File

object VideoFileManager {
    private const val TAG = "VideoFileManager"

    fun getVideoFiles(context: Context): List<File> {
        val paths = listOfNotNull(
            context.externalCacheDir,
            context.cacheDir,
            context.filesDir,
            context.getExternalFilesDir("videos")
        )

        val allDetectedFiles = mutableListOf<File>()

        paths.forEach { dir ->
            val filesInDir = dir.listFiles() ?: return@forEach
            filesInDir.forEach { file ->
                val fileNameLower = file.name.lowercase()
                // SyncLab 촬영 영상임을 보장하기 위해 "synclab" 접두사 확인을 권장합니다.
                if (file.isFile && fileNameLower.endsWith(".mp4") && file.length() > 0) {
                    allDetectedFiles.add(file)
                }
            }
        }

        val result = allDetectedFiles.distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }

        Log.d(TAG, "Scan finished. Found ${result.size} video files.")
        return result
    }

    /**
     * 영상 파일 삭제 시 해당 영상의 메타데이터(JSON) 파일도 함께 삭제합니다.
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                // 1. 영상 파일 이름에서 확장자를 뺀 순수 이름 추출 (예: SyncLab_123)
                val baseName = file.nameWithoutExtension
                val parentDir = file.parentFile

                // 2. 같은 폴더 내의 .json 파일 객체 생성
                val jsonFile = File(parentDir, "$baseName.json")

                // 3. 메타데이터 파일 존재 시 삭제
                if (jsonFile.exists()) {
                    val jsonDeleted = jsonFile.delete()
                    Log.d(TAG, "Metadata file deleted: $jsonDeleted (${jsonFile.name})")
                }

                // 4. 원본 영상 파일 삭제
                val videoDeleted = file.delete()
                Log.d(TAG, "Video file deleted: $videoDeleted (${file.name})")

                videoDeleted
            } else {
                Log.w(TAG, "File not found: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}")
            false
        }
    }
}