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

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val baseName = file.nameWithoutExtension
                val parentDir = file.parentFile

                val jsonFile = File(parentDir, "$baseName.json")

                if (jsonFile.exists()) {
                    val jsonDeleted = jsonFile.delete()
                    Log.d(TAG, "Metadata file deleted: $jsonDeleted (${jsonFile.name})")
                }

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