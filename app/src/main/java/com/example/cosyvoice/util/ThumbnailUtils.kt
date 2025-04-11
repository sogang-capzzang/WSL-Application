package com.example.cosyvoice.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ThumbnailUtils {
    private val thumbnailCache = mutableMapOf<String, Bitmap?>()

    fun getThumbnailFromAsset(context: Context, assetPath: String): Bitmap? {
        // 메모리 캐시 확인
        if (thumbnailCache.containsKey(assetPath)) {
            Log.d("ThumbnailUtils", "Loaded thumbnail from memory cache: $assetPath")
            return thumbnailCache[assetPath]
        }

        val retriever = MediaMetadataRetriever()
        return try {
            val assetManager = context.assets
            val fileList = assetManager.list(assetPath.substringBeforeLast("/")) ?: emptyArray()
            if (!fileList.contains(assetPath.substringAfterLast("/"))) {
                Log.e("ThumbnailUtils", "File does not exist in assets: $assetPath")
                return null
            }
            Log.d("ThumbnailUtils", "File exists in assets: $assetPath")

            // assets 파일을 임시 파일로 복사
            val tempFile = File.createTempFile("thumbnail", ".mp4", context.cacheDir)
            context.assets.open(assetPath).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("ThumbnailUtils", "Copied asset to temp file: ${tempFile.absolutePath}")

            // 임시 파일에서 썸네일 추출
            retriever.setDataSource(tempFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(0)
            if (bitmap == null) {
                Log.e("ThumbnailUtils", "Failed to extract thumbnail from $assetPath: Bitmap is null")
                return null
            }
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 300, (300 * bitmap.height / bitmap.width), true)
            thumbnailCache[assetPath] = scaledBitmap
            Log.d("ThumbnailUtils", "Thumbnail loaded successfully: $assetPath")

            //임시 파일 삭제
            tempFile.delete()
            scaledBitmap
        } catch (e: Exception) {
            Log.e("ThumbnailUtils", "Error loading thumbnail for $assetPath: ${e.message}", e)
            null
        } finally {
            retriever.release()
        }
    }
}