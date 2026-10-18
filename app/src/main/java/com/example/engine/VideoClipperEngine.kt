package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.AspectRatio
import com.example.data.model.ExportResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

object VideoClipperEngine {
    private const val TAG = "VideoClipperEngine"
    private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer

    data class ExportOptions(
        val aspectRatio: AspectRatio = AspectRatio.VERTICAL_9_16,
        val resolution: ExportResolution = ExportResolution.RES_1080P,
        val burnSubtitles: Boolean = false,
        val srtText: String? = null
    )

    /**
     * Clips video losslessly using native Android MediaExtractor + MediaMuxer.
     * Preserves original H.264 video and AAC audio with exact sync and zero quality loss.
     */
    suspend fun clipVideo(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        clipTitle: String,
        options: ExportOptions = ExportOptions(),
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val sanitized = clipTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(30)
        val outputDir = File(context.cacheDir, "fevi_rendered").apply { mkdirs() }
        val outputFile = File(outputDir, "${sanitized}_${System.currentTimeMillis()}.mp4")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            val trackCount = extractor.trackCount

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val indexMap = HashMap<Int, Int>()

            var videoTrackIndex = -1
            var audioTrackIndex = -1

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                }
            }

            if (indexMap.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No compatible video/audio tracks found"))
            }

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val durationUs = (endUs - startUs).coerceAtLeast(1000L)

            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            // Seek to start position sync frame
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var firstSampleTimeUs = -1L
            var samplesCopied = 0

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) {
                    // Check if both tracks passed endUs
                    if (sampleTimeUs > endUs + 1_000_000L) break
                }

                if (trackIndex in indexMap) {
                    if (sampleTimeUs >= (startUs - 200_000L)) {
                        if (firstSampleTimeUs == -1L) {
                            firstSampleTimeUs = sampleTimeUs
                        }

                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize >= 0) {
                            bufferInfo.offset = 0
                            bufferInfo.size = sampleSize
                            bufferInfo.presentationTimeUs = (sampleTimeUs - firstSampleTimeUs).coerceAtLeast(0L)
                            bufferInfo.flags = extractor.sampleFlags

                            val dstTrack = indexMap[trackIndex]!!
                            muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                            samplesCopied++

                            if (samplesCopied % 60 == 0) {
                                val progress = ((sampleTimeUs - startUs).toFloat() / durationUs).coerceIn(0f, 0.95f)
                                onProgress(progress)
                            }
                        }
                    }
                }

                extractor.advance()
            }

            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Lossless clipping failed", e)
            Result.failure(Exception("Clip export failed: ${e.message}", e))
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Copies and registers the rendered MP4 file into device MediaStore (Movies/FeviClipper)
     * so it immediately appears in Gallery, Photos, and YouTube app.
     */
    suspend fun saveToGallery(
        context: Context,
        renderedFile: File,
        displayName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FeviClipper")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, contentValues)
            ?: return@withContext Result.failure(IllegalStateException("Failed to create MediaStore entry"))

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(renderedFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            Result.success(itemUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving to gallery", e)
            resolver.delete(itemUri, null, null)
            Result.failure(e)
        }
    }
}
