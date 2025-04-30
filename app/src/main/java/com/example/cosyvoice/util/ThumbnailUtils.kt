package com.example.cosyvoice.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object ThumbnailUtils {
    suspend fun getThumbnailFromAsset(context: Context, assetPath: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                Log.d("ThumbnailUtils", "Attempting to open asset: $assetPath")
                context.assets.openFd(assetPath).use { assetFileDescriptor ->
                    retriever.setDataSource(
                        assetFileDescriptor.fileDescriptor,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.length
                    )

                    val durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val duration = durationString?.toLongOrNull() ?: 0L
                    val thumbnailTime = if (duration > 0) duration / 2 else 1000L

                    Log.d("ThumbnailUtils", "Asset: $assetPath, Duration: $duration ms, ThumbnailTime: $thumbnailTime ms")

                    val bitmap = retriever.getFrameAtTime(
                        thumbnailTime * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (bitmap == null) {
                        Log.e("ThumbnailUtils", "Failed to extract thumbnail for $assetPath: Bitmap is null")
                    } else {
                        Log.d("ThumbnailUtils", "Thumbnail extracted successfully for $assetPath")
                    }
                    bitmap
                }
            } catch (e: IOException) {
                Log.e("ThumbnailUtils", "IOException extracting thumbnail for $assetPath: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("ThumbnailUtils", "General error extracting thumbnail for $assetPath: ${e.message}")
                null
            } finally {
                try {
                    retriever?.release()
                    Log.d("ThumbnailUtils", "MediaMetadataRetriever released for $assetPath")
                } catch (e: Exception) {
                    Log.e("ThumbnailUtils", "Error releasing MediaMetadataRetriever: ${e.message}")
                }
            }
        }
    }
}