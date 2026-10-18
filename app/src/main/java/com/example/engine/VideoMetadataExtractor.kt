package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object VideoMetadataExtractor {
    private const val TAG = "VideoMetadataExtractor"

    suspend fun extractMetadata(context: Context, videoUri: Uri): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var hasAudioTrack = false
        var fileName = "video.mp4"
        var sizeBytes = 0L

        try {
            // Retrieve display name and size from ContentResolver
            context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "video.mp4"
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }

            retriever.setDataSource(context, videoUri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            if (durationMs <= 0) {
                return@withContext Result.failure(
                    IllegalArgumentException("The selected file is corrupted or is not a valid playable video.")
                )
            }

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

            var width = widthStr?.toIntOrNull() ?: 1920
            var height = heightStr?.toIntOrNull() ?: 1080
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // Swap dimensions if portrait rotated
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }

            // Check audio track presence via MediaExtractor
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, videoUri, null)
                val numTracks = extractor.trackCount
                for (i in 0 until numTracks) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        hasAudioTrack = true
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaExtractor audio check fallback: ${e.message}")
                val hasAudioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                hasAudioTrack = hasAudioMeta == "yes"
            } finally {
                extractor.release()
            }

            var frameRate = 30f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val captureRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                captureRate?.toFloatOrNull()?.let { frameRate = it }
            }

            val metadata = VideoMetadata(
                uri = videoUri.toString(),
                fileName = fileName,
                durationMs = durationMs,
                width = width,
                height = height,
                frameRate = frameRate,
                hasAudio = hasAudioTrack,
                sizeBytes = sizeBytes,
                mimeType = mimeType
            )

            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read video metadata", e)
            Result.failure(Exception("Could not read video metadata: ${e.localizedMessage ?: "Unknown error"}", e))
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun extractThumbnail(
        context: Context,
        videoUri: Uri,
        timeUs: Long = 1_000_000L
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun copyUriToTempFile(context: Context, videoUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "fevi_videos").apply { mkdirs() }
            val tempFile = File(cacheDir, "source_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
